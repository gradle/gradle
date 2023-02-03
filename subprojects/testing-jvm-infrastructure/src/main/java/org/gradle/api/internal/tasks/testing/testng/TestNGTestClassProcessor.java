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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.TestFilterSpec;
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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class TestNGTestClassProcessor implements TestClassProcessor {

    public static final String DEFAULT_CONFIG_FAILURE_POLICY = "skip";

    private final List<Class<?>> testClasses = new ArrayList<Class<?>>();
    private final File testReportDir;
    private final TestNGSpec spec;
    private final List<File> suiteFiles;
    private final IdGenerator<?> idGenerator;
    private final Clock clock;
    private final ActorFactory actorFactory;
    private ClassLoader applicationClassLoader;
    private Actor resultProcessorActor;
    private TestResultProcessor resultProcessor;

    public TestNGTestClassProcessor(File testReportDir, TestNGSpec spec, List<File> suiteFiles, IdGenerator<?> idGenerator, Clock clock, ActorFactory actorFactory) {
        this.testReportDir = testReportDir;
        this.spec = spec;
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
        testNg.setDefaultSuiteName(spec.getDefaultSuiteName());
        testNg.setDefaultTestName(spec.getDefaultTestName());
        if (spec.getParallel() != null) {
            testNg.setParallel(spec.getParallel());
        }
        if (spec.getThreadCount() > 0) {
            testNg.setThreadCount(spec.getThreadCount());
        }

        setConfigFailurePolicy(testNg, spec.getConfigFailurePolicy());
        setPreserveOrder(testNg, spec.getPreserveOrder());
        setGroupByInstances(testNg, spec.getGroupByInstances());

        testNg.setUseDefaultListeners(spec.getUseDefaultListeners());
        testNg.setVerbose(0);
        testNg.setGroups(CollectionUtils.join(",", spec.getIncludeGroups()));
        testNg.setExcludedGroups(CollectionUtils.join(",", spec.getExcludeGroups()));

        // Adding custom test listeners before Gradle's listeners.
        // This way, custom listeners are more powerful and, for example, they can change test status.
        for (String listenerClass : spec.getListeners()) {
            try {
                testNg.addListener(JavaReflectionUtil.newInstance(applicationClassLoader.loadClass(listenerClass)));
            } catch (Throwable e) {
                throw new GradleException(String.format("Could not add a test listener with class '%s'.", listenerClass), e);
            }
        }

        TestFilterSpec filter = spec.getFilter();
        if (!filter.getIncludedTests().isEmpty() || !filter.getIncludedTestsCommandLine().isEmpty() || !filter.getExcludedTests().isEmpty()) {
            testNg.addListener(new SelectedTestsFilter(filter));
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
     * The setter for {@code configFailurePolicy} has a different signature depending on TestNG version.
     * This method uses reflection to detect the API and calls the version with the correct signature.
     *
     * If the TestNG version is greater than or equal to 6.9.12, the provided {@code value} is coerced to
     * an Enum, otherwise the method which accepts a {@code String} is called with the unmodified {@code value}.
     *
     * @param testNg the TestNG instance.
     * @param value The configured value to set, as a string.
     */
    private void setConfigFailurePolicy(TestNG testNg, String value) {
        Class<?> argType;
        Object argValue;
        try {
            argType = Class.forName("org.testng.xml.XmlSuite$FailurePolicy", false, testNg.getClass().getClassLoader());
            argValue = argType.getMethod("getValidPolicy", String.class).invoke(null, value);
        } catch (Exception e) {
            // New API not found. Fallback to legacy String argument.
            argType = String.class;
            argValue = value;
        }

        try {
            JavaMethod.of(TestNG.class, Object.class, "setConfigFailurePolicy", argType).invoke(testNg, argValue);
        } catch (NoSuchMethodException e) {
            if (!argValue.equals(DEFAULT_CONFIG_FAILURE_POLICY)) {
                String message = String.format("The version of TestNG used does not support setting config failure policy to '%s'.", value);
                throw new InvalidUserDataException(message);
            }
        }
    }

    private void setPreserveOrder(TestNG testNg, boolean value) {
        try {
            JavaMethod.of(TestNG.class, Object.class, "setPreserveOrder", boolean.class).invoke(testNg, value);
        } catch (NoSuchMethodException e) {
            if (value) {
                throw new InvalidUserDataException("Preserving the order of tests is not supported by this version of TestNG.");
            }
        }
    }

    private void setGroupByInstances(TestNG testNg, boolean value) {
        try {
            JavaMethod.of(TestNG.class, Object.class, "setGroupByInstances", boolean.class).invoke(testNg, value);
        } catch (NoSuchMethodException e) {
            if (value) {
                throw new InvalidUserDataException("Grouping tests by instances is not supported by this version of TestNG.");
            }
        }
    }

    private ITestListener adaptListener(ITestListener listener) {
        TestNGListenerAdapterFactory factory = new TestNGListenerAdapterFactory(applicationClassLoader);
        return factory.createAdapter(listener);
    }

    private static class SelectedTestsFilter implements IMethodInterceptor {

        private final TestSelectionMatcher matcher;

        public SelectedTestsFilter(TestFilterSpec filter) {
            matcher = new TestSelectionMatcher(filter);
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
