/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.nativeplatform.test.xctest.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.junit.result.InMemoryTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestOutputStore;
import org.gradle.api.internal.tasks.testing.junit.result.TestReportDataCollector;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.logging.FullExceptionFormatter;
import org.gradle.api.internal.tasks.testing.logging.ShortExceptionFormatter;
import org.gradle.api.internal.tasks.testing.logging.TestCountLogger;
import org.gradle.api.internal.tasks.testing.logging.TestEventLogger;
import org.gradle.api.internal.tasks.testing.logging.TestExceptionFormatter;
import org.gradle.api.internal.tasks.testing.logging.TestWorkerProgressListener;
import org.gradle.api.internal.tasks.testing.results.StateTrackingTestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.nativeplatform.test.xctest.internal.NativeTestExecuter;
import org.gradle.nativeplatform.test.xctest.internal.XCTestTestExecutionSpec;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Executes XCTest tests. Test are always run in a single execution.
 *
 * @since 4.2
 */
@Incubating
public class XcTest extends AbstractTestTask {
    private final DirectoryProperty testBundleDir;
    private final DirectoryProperty workingDir;
    private final ObjectFactory objectFactory;

    @Inject
    public XcTest(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        testBundleDir = newInputDirectory();
        workingDir = getProject().getLayout().directoryProperty();
    }

    @Override
    protected XCTestTestExecutionSpec createTestExecutionSpec() {
        return new XCTestTestExecutionSpec(workingDir.getAsFile().get(), testBundleDir.getAsFile().get(), getPath());
    }

    @InputDirectory
    public File getTestBundleDir() {
        return testBundleDir.getAsFile().get();
    }

    public void setTestBundleDir(File testBundleDir) {
        this.testBundleDir.set(testBundleDir);
    }

    public void setTestBundleDir(Provider<? extends Directory> testBundleDir) {
        this.testBundleDir.set(testBundleDir);
    }

    @Internal
    public File getWorkingDir() {
        return workingDir.getAsFile().get();
    }

    public void setWorkingDir(File workingDir) {
        this.workingDir.set(workingDir);
    }

    public void setWorkingDir(Provider<? extends Directory> workingDir) {
        this.workingDir.set(workingDir);
    }

    @TaskAction
    public void executeTests() {
        LogLevel currentLevel = determineCurrentLogLevel();
        TestLogging levelLogging = testLogging.get(currentLevel);
        TestExceptionFormatter exceptionFormatter = getExceptionFormatter(levelLogging);
        TestEventLogger eventLogger = new TestEventLogger(getTextOutputFactory(), currentLevel, levelLogging, exceptionFormatter);
        addTestListener(eventLogger);
        addTestOutputListener(eventLogger);
//        if (getFilter().isFailOnNoMatchingTests() && (!getFilter().getIncludePatterns().isEmpty() || !filter.getCommandLineIncludePatterns().isEmpty())) {
//            addTestListener(new NoMatchingTestsReporter(createNoMatchingTestErrorMessage()));
//        }

        File binaryResultsDir = getBinResultsDir();
        getProject().delete(binaryResultsDir);
        getProject().mkdir(binaryResultsDir);

        Map<String, TestClassResult> results = new HashMap<String, TestClassResult>();
        TestOutputStore testOutputStore = new TestOutputStore(binaryResultsDir);

        TestOutputStore.Writer outputWriter = testOutputStore.writer();
        TestReportDataCollector testReportDataCollector = new TestReportDataCollector(results, outputWriter);

        addTestListener(testReportDataCollector);
        addTestOutputListener(testReportDataCollector);

        TestCountLogger testCountLogger = new TestCountLogger(getProgressLoggerFactory());
        addTestListener(testCountLogger);

        getTestListenerInternalBroadcaster().add(new TestListenerAdapter(testListenerBroadcaster.getSource(), testOutputListenerBroadcaster.getSource()));

        ProgressLogger parentProgressLogger = getProgressLoggerFactory().newOperation(Test.class);
        parentProgressLogger.setDescription("Test Execution");
        parentProgressLogger.started();
        TestWorkerProgressListener testWorkerProgressListener = new TestWorkerProgressListener(getProgressLoggerFactory(), parentProgressLogger);
        getTestListenerInternalBroadcaster().add(testWorkerProgressListener);

        TestResultProcessor resultProcessor = new StateTrackingTestResultProcessor(getTestListenerInternalBroadcaster().getSource());

        NativeTestExecuter testExecuter = objectFactory.newInstance(NativeTestExecuter.class);

        try {
            testExecuter.execute(createTestExecutionSpec(), resultProcessor);
        } finally {
            parentProgressLogger.completed();
            testExecuter = null;
            testWorkerProgressListener.completeAll();
            testListenerBroadcaster.removeAll();
            testOutputListenerBroadcaster.removeAll();
            getTestListenerInternalBroadcaster().removeAll();
            outputWriter.close();
        }

        new TestResultSerializer(binaryResultsDir).write(results.values());

        TestResultsProvider testResultsProvider = new InMemoryTestResultsProvider(results.values(), testOutputStore);

//        try {
//            if (testReporter == null) {
//                testReporter = new DefaultTestReport(getBuildOperationExecutor());
//            }

//            JUnitXmlReport junitXml = reports.getJunitXml();
//            if (junitXml.isEnabled()) {
//                TestOutputAssociation outputAssociation = junitXml.isOutputPerTestCase()
//                    ? TestOutputAssociation.WITH_TESTCASE
//                    : TestOutputAssociation.WITH_SUITE;
//                Binary2JUnitXmlReportGenerator binary2JUnitXmlReportGenerator = new Binary2JUnitXmlReportGenerator(junitXml.getDestination(), testResultsProvider, outputAssociation, getBuildOperationExecutor(), getInetAddressFactory().getHostname());
//                binary2JUnitXmlReportGenerator.generate();
//            }

//            DirectoryReport html = reports.getHtml();
//            if (!html.isEnabled()) {
//                getLogger().info("Test report disabled, omitting generation of the HTML test report.");
//            } else {
//                testReporter.generateReport(testResultsProvider, html.getDestination());
//            }
//        } finally {
//            CompositeStoppable.stoppable(testResultsProvider).stop();
//            testReporter = null;
//            testFramework = null;
//        }

        if (testCountLogger.hadFailures()) {
            handleTestFailures();
        }
    }

    private TestExceptionFormatter getExceptionFormatter(TestLogging testLogging) {
        switch (testLogging.getExceptionFormat()) {
            case SHORT:
                return new ShortExceptionFormatter(testLogging);
            case FULL:
                return new FullExceptionFormatter(testLogging);
            default:
                throw new AssertionError();
        }
    }

    private void handleTestFailures() {
        String message = "There were failing tests";

        if (getIgnoreFailures()) {
            getLogger().warn(message);
        } else {
            throw new GradleException(message);
        }
    }
}
