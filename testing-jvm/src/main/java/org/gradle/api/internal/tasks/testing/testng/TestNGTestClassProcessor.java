/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.NoSuchMethodException;
import org.gradle.internal.time.Clock;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GFileUtils;
import org.testng.IMethodInstance;
import org.testng.IMethodInterceptor;
import org.testng.ISuite;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.TestNG;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import static org.gradle.api.tasks.testing.testng.TestNGOptions.DEFAULT_CONFIG_FAILURE_POLICY;

public class TestNGTestClassProcessor implements TestClassProcessor {
    private final List<Class<?>> testClasses = new ArrayList<Class<?>>();
    private final File testReportDir;
    private final TestNGSpec options;
    private final List<File> suiteFiles;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private final ActorFactory actorFactory;
    private ClassLoader applicationClassLoader;
    private Actor resultProcessorActor;
    private TestResultProcessor resultProcessor;

    public TestNGTestClassProcessor(File testReportDir, TestNGSpec options, List<File> suiteFiles, IdGenerator<?> idGenerator, Clock clock, ActorFactory actorFactory) {
        this.testReportDir = testReportDir;
        this.options = options;
        this.suiteFiles = suiteFiles;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.actorFactory = actorFactory;
    }

    @Override
    public void startProcessing(TestResultProcessor resultProcessor) {
        // Wrap the processor in an actor, to make it thread-safe
        resultProcessorActor = actorFactory.createBlockingActor(resultProcessor);
        this.resultProcessor = resultProcessorActor.getProxy(TestResultProcessor.class);
        applicationClassLoader = Thread.currentThread().getContextClassLoader();
    }

    @Override
    public void processTestClass(TestClassRunInfo testClass) {
        // TODO - do this inside some 'testng' suite, so that failures and logging are attached to 'testng' rather than some 'test worker'
        try {
            testClasses.add(applicationClassLoader.loadClass(testClass.getTestClassName()));
        } catch (Throwable e) {
            throw new GradleException(String.format("Could not load test class '%s'.", testClass.getTestClassName()), e);
        }
    }

    @Override
    public void stop() {
        try {
            runTests();
        } finally {
            resultProcessorActor.stop();
        }
    }

    @Override
    public void stopNow() {
        throw new UnsupportedOperationException("stopNow() should not be invoked on remote worker TestClassProcessor");
    }

    private void runTests() {
        TestNG testNg = new TestNG();
        testNg.setOutputDirectory(testReportDir.getAbsolutePath());
        testNg.setDefaultSuiteName(options.getDefaultSuiteName());
        testNg.setDefaultTestName(options.getDefaultTestName());
        if (options.getParallel() != null) {
            testNg.setParallel(options.getParallel());
        }
        if (options.getThreadCount() > 0) {
            testNg.setThreadCount(options.getThreadCount());
        }

        Class<?> configFailurePolicyArgType = getConfigFailurePolicyArgType(testNg);
        Object configFailurePolicyArgValue = getConfigFailurePolicyArgValue(testNg);

        invokeVerifiedMethod(testNg, "setConfigFailurePolicy", configFailurePolicyArgType, configFailurePolicyArgValue, DEFAULT_CONFIG_FAILURE_POLICY);
        invokeVerifiedMethod(testNg, "setPreserveOrder", boolean.class, options.getPreserveOrder(), false);
        invokeVerifiedMethod(testNg, "setGroupByInstances", boolean.class, options.getGroupByInstances(), false);
        testNg.setUseDefaultListeners(options.getUseDefaultListeners());
        testNg.setVerbose(0);
        testNg.setGroups(CollectionUtils.join(",", options.getIncludeGroups()));
        testNg.setExcludedGroups(CollectionUtils.join(",", options.getExcludeGroups()));

        //adding custom test listeners before Gradle's listeners.
        //this way, custom listeners are more powerful and, for example, they can change test status.
        for (String listenerClass : options.getListeners()) {
            try {
                testNg.addListener(JavaReflectionUtil.newInstance(applicationClassLoader.loadClass(listenerClass)));
            } catch (Throwable e) {
                throw new GradleException(String.format("Could not add a test listener with class '%s'.", listenerClass), e);
            }
        }

        if (!options.getIncludedTests().isEmpty() || !options.getIncludedTestsCommandLine().isEmpty() || !options.getExcludedTests().isEmpty()) {
            testNg.addListener(new SelectedTestsFilter(options.getIncludedTests(),
                options.getExcludedTests(), options.getIncludedTestsCommandLine()));
        }

        if (!suiteFiles.isEmpty()) {
            testNg.setTestSuites(GFileUtils.toPaths(suiteFiles));
        } else {
            testNg.setTestClasses(testClasses.toArray(new Class<?>[0]));
        }
        testNg.addListener((Object) adaptListener(new TestNGTestResultProcessorAdapter(resultProcessor, idGenerator, clock)));
        testNg.run();
    }

    /**
     * The setter for configFailurePolicy has a different signature depending on TestNG version.  This method uses reflection to
     * detect the API and return a reference to the correct argument type.
     * <ul>
     *     <li>When TestNG &gt;= 6.9.12, {@link TestNG#setConfigFailurePolicy(org.testng.xml.XmlSuite$FailurePolicy)}</li>
     *     <li>When TestNG &lt; 6.9.12, {@link TestNG#setConfigFailurePolicy(String)}</li>
     * </ul></li>
     *
     * @param testNg the TestNG instance
     * @return String.class or org.testng.xml.XmlSuite$FailurePolicy.class
     */
    private Class<?> getConfigFailurePolicyArgType(TestNG testNg) {
        Class<?> failurePolicy;
        try {
            failurePolicy = Class.forName("org.testng.xml.XmlSuite$FailurePolicy", false, testNg.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            // new API not found; fallback to legacy String argument
            failurePolicy = String.class;
        }
        return failurePolicy;
    }

    /**
     * The setter for configFailurePolicy has a different signature depending on TestNG version.  This method uses reflection to
     * detect the API.  If not {@link String}, coerce the spec's string value to the expected enum value using {@link XmlSuite$FailurePolicy#getValidPolicy(String)}
     * <ul>
     *     <li>When TestNG &gt;= 6.9.12, {@link TestNG#setConfigFailurePolicy(org.testng.xml.XmlSuite$FailurePolicy)}</li>
     *     <li>When TestNG &lt; 6.9.12, {@link TestNG#setConfigFailurePolicy(String)}</li>
     * </ul></li>
     *
     * @param testNg the TestNG instance
     * @return Arg value; might be a String or an enum value of org.testng.xml.XmlSuite$FailurePolicy.class
     */
    private Object getConfigFailurePolicyArgValue(TestNG testNg) {
        Object configFailurePolicyArgValue;
        try {
            Class<?> failurePolicy = Class.forName("org.testng.xml.XmlSuite$FailurePolicy", false, testNg.getClass().getClassLoader());
            Method getValidPolicy = failurePolicy.getMethod("getValidPolicy", String.class);
            configFailurePolicyArgValue = getValidPolicy.invoke(null, options.getConfigFailurePolicy());
        } catch (Exception e) {
            // unable to invoke new API method; fallback to legacy String value
            configFailurePolicyArgValue = options.getConfigFailurePolicy();
        }
        return configFailurePolicyArgValue;
    }

    private void invokeVerifiedMethod(TestNG testNg, String methodName, Class<?> paramClass, Object value, Object defaultValue) {
        try {
            JavaMethod.of(TestNG.class, Object.class, methodName, paramClass).invoke(testNg, value);
        } catch (NoSuchMethodException e) {
            if (!value.equals(defaultValue)) {
                // Should not reach this point as this is validated in the test framework implementation - just propagate the failure
                throw e;
            }
        }
    }

    private ITestListener adaptListener(ITestListener listener) {
        TestNGListenerAdapterFactory factory = new TestNGListenerAdapterFactory(applicationClassLoader);
        return factory.createAdapter(listener);
    }

    private static class SelectedTestsFilter implements IMethodInterceptor {

        private final TestSelectionMatcher matcher;

        public SelectedTestsFilter(Set<String> includedTests, Set<String> excludedTests,
            Set<String> includedTestsCommandLine) {
            matcher = new TestSelectionMatcher(includedTests, excludedTests, includedTestsCommandLine);
        }

        @Override
        public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
            ISuite suite = context.getSuite();
            List<IMethodInstance> filtered = new LinkedList<IMethodInstance>();
            for (IMethodInstance candidate : methods) {
                if (matcher.matchesTest(candidate.getMethod().getTestClass().getName(), candidate.getMethod().getMethodName())
                    || matcher.matchesTest(suite.getName(), null)) {
                    filtered.add(candidate);
                }
            }
            return filtered;
        }
    }
}
