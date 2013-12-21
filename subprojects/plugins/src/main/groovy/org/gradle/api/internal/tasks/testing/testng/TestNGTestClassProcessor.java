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
import org.gradle.api.internal.tasks.testing.processors.CaptureTestOutputTestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.TestSelectionMatcher;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.NoSuchMethodException;
import org.gradle.logging.StandardOutputRedirector;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GFileUtils;
import org.testng.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class TestNGTestClassProcessor implements TestClassProcessor {
    private final List<Class<?>> testClasses = new ArrayList<Class<?>>();
    private final File testReportDir;
    private final TestNGSpec options;
    private final List<File> suiteFiles;
    private final IdGenerator<?> idGenerator;
    private final StandardOutputRedirector outputRedirector;
    private TestNGTestResultProcessorAdapter testResultProcessor;
    private ClassLoader applicationClassLoader;

    public TestNGTestClassProcessor(File testReportDir, TestNGSpec options, List<File> suiteFiles, IdGenerator<?> idGenerator,
                                    StandardOutputRedirector outputRedirector) {
        this.testReportDir = testReportDir;
        this.options = options;
        this.suiteFiles = suiteFiles;
        this.idGenerator = idGenerator;
        this.outputRedirector = outputRedirector;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        TestResultProcessor resultProcessorChain = new CaptureTestOutputTestResultProcessor(resultProcessor, outputRedirector);

        testResultProcessor = new TestNGTestResultProcessorAdapter(resultProcessorChain, idGenerator);

        applicationClassLoader = Thread.currentThread().getContextClassLoader();
    }

    public void processTestClass(TestClassRunInfo testClass) {
        try {
            testClasses.add(applicationClassLoader.loadClass(testClass.getTestClassName()));
        } catch (Throwable e) {
            throw new GradleException(String.format("Could not load test class '%s'.", testClass.getTestClassName()), e);
        }
    }

    public void stop() {
        TestNG testNg = new TestNG();
        testNg.setOutputDirectory(testReportDir.getAbsolutePath());
        testNg.setDefaultSuiteName(options.getDefaultSuiteName());
        testNg.setDefaultTestName(options.getDefaultTestName());
        testNg.setParallel(options.getParallel());
        testNg.setThreadCount(options.getThreadCount());
        try {
            JavaReflectionUtil.method(TestNG.class, Object.class, "setAnnotations").invoke(testNg, options.getAnnotations());
        } catch (NoSuchMethodException e) {
            /* do nothing; method has been removed in TestNG 6.3 */
        }
        if (options.getJavadocAnnotations()) {
            testNg.setSourcePath(CollectionUtils.join(File.pathSeparator, options.getTestResources()));
        }

        testNg.setUseDefaultListeners(options.getUseDefaultListeners());
        testNg.addListener((Object) adaptListener(testResultProcessor));
        if (!options.getIncludedTests().isEmpty()) {
            testNg.addListener(new SelectedTestsFilter(options.getIncludedTests()));
        }
        testNg.setVerbose(0);
        testNg.setGroups(CollectionUtils.join(",", options.getIncludeGroups()));
        testNg.setExcludedGroups(CollectionUtils.join(",", options.getExcludeGroups()));
        for (String listenerClass : options.getListeners()) {
            try {
                testNg.addListener(applicationClassLoader.loadClass(listenerClass).newInstance());
            } catch (Throwable e) {
                throw new GradleException(String.format("Could not add a test listener with class '%s'.", listenerClass), e);
            }
        }

        if (!suiteFiles.isEmpty()) {
            testNg.setTestSuites(GFileUtils.toPaths(suiteFiles));
        } else {
            testNg.setTestClasses(testClasses.toArray(new Class[testClasses.size()]));
        }

        testNg.run();
    }

    private ITestListener adaptListener(ITestListener listener) {
        TestNGListenerAdapterFactory factory = new TestNGListenerAdapterFactory(applicationClassLoader);
        return factory.createAdapter(listener);
    }

    private static class SelectedTestsFilter implements IMethodInterceptor {

        private final TestSelectionMatcher matcher;

        public SelectedTestsFilter(Set<String> includedTests) {
            matcher = new TestSelectionMatcher(includedTests);
        }

        public List<IMethodInstance> intercept(List<IMethodInstance> methods, ITestContext context) {
            List<IMethodInstance> filtered = new LinkedList<IMethodInstance>();
            for (IMethodInstance candidate : methods) {
                if (matcher.matchesTest(candidate.getMethod().getTestClass().getName(), candidate.getMethod().getMethodName())) {
                    filtered.add(candidate);
                }
            }
            return filtered;
        }
    }
}