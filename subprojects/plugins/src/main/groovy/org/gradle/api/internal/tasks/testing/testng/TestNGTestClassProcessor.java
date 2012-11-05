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

import groovy.lang.MissingMethodException;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestClassRunInfo;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.junit.TestNGJUnitXmlReportGenerator;
import org.gradle.api.internal.tasks.testing.processors.CaptureTestOutputTestResultProcessor;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.internal.id.IdGenerator;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.logging.StandardOutputRedirector;
import org.gradle.util.GFileUtils;
import org.gradle.util.GUtil;
import org.gradle.util.ReflectionUtil;
import org.testng.ITestListener;
import org.testng.TestNG;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TestNGTestClassProcessor implements TestClassProcessor {
    private final List<Class<?>> testClasses = new ArrayList<Class<?>>();
    private final File testReportDir;
    private final TestNGOptions options;
    private final List<File> suiteFiles;
    private final IdGenerator<?> idGenerator;
    private final StandardOutputRedirector outputRedirector;
    private final File testResultsDir;
    private final boolean testReportOn;
    private TestNGTestResultProcessorAdapter testResultProcessor;
    private ClassLoader applicationClassLoader;

    public TestNGTestClassProcessor(File testReportDir, TestNGOptions options, List<File> suiteFiles, IdGenerator<?> idGenerator,
                                    StandardOutputRedirector outputRedirector, File testResultsDir, boolean testReportOn) {
        this.testReportDir = testReportDir;
        this.options = options;
        this.suiteFiles = suiteFiles;
        this.idGenerator = idGenerator;
        this.outputRedirector = outputRedirector;
        this.testResultsDir = testResultsDir;
        this.testReportOn = testReportOn;
    }

    public void startProcessing(TestResultProcessor resultProcessor) {
        ListenerBroadcast<TestResultProcessor> processors = new ListenerBroadcast<TestResultProcessor>(TestResultProcessor.class);
        processors.add(resultProcessor);
        if (testReportOn) {
            //Do not generate the xml results unless the report is wanted explicitly
            //TODO SF this check needs to be removed when new TestNG reports are turned on by default
            processors.add(new TestNGJUnitXmlReportGenerator(testResultsDir));
        }
        TestResultProcessor resultProcessorChain = new CaptureTestOutputTestResultProcessor(processors.getSource(), outputRedirector);

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
        testNg.setDefaultSuiteName(options.getSuiteName());
        testNg.setDefaultTestName(options.getTestName());
        testNg.setParallel(options.getParallel());
        testNg.setThreadCount(options.getThreadCount());
        try {
            ReflectionUtil.invoke(testNg, "setAnnotations", options.getAnnotations());
        } catch (MissingMethodException e) {
            /* do nothing; method has been removed in TestNG 6.3 */
        }
        if (options.getJavadocAnnotations()) {
            testNg.setSourcePath(GUtil.join(options.getTestResources(), File.pathSeparator));
        }

        //only use the default listeners flag when testReport is off
        //TODO SF spockify the test, add unit test coverage and inform about 'incubating' nature of the new report
        //we should remove this complexity when new TestNG reports are turned on by default
        testNg.setUseDefaultListeners(!testReportOn && options.getUseDefaultListeners());

        testNg.addListener((Object) adaptListener(testResultProcessor));
        testNg.setVerbose(0);
        testNg.setGroups(GUtil.join(options.getIncludeGroups(), ","));
        testNg.setExcludedGroups(GUtil.join(options.getExcludeGroups(), ","));
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
            Class[] classes = testClasses.toArray(new Class[testClasses.size()]);
            testNg.setTestClasses(classes);
        }

        testNg.run();
    }

    private ITestListener adaptListener(ITestListener listener) {
        TestNGListenerAdapterFactory factory = new TestNGListenerAdapterFactory(applicationClassLoader);
        return factory.createAdapter(listener);
    }
}
