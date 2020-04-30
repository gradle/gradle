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

package org.gradle.api.tasks.testing;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.testing.DefaultTestTaskReports;
import org.gradle.api.internal.tasks.testing.FailFastTestListenerInternal;
import org.gradle.api.internal.tasks.testing.NoMatchingTestsReporter;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.result.Binary2JUnitXmlReportGenerator;
import org.gradle.api.internal.tasks.testing.junit.result.InMemoryTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestOutputAssociation;
import org.gradle.api.internal.tasks.testing.junit.result.TestOutputStore;
import org.gradle.api.internal.tasks.testing.junit.result.TestReportDataCollector;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.logging.DefaultTestLoggingContainer;
import org.gradle.api.internal.tasks.testing.logging.FullExceptionFormatter;
import org.gradle.api.internal.tasks.testing.logging.ShortExceptionFormatter;
import org.gradle.api.internal.tasks.testing.logging.TestCountLogger;
import org.gradle.api.internal.tasks.testing.logging.TestEventLogger;
import org.gradle.api.internal.tasks.testing.logging.TestExceptionFormatter;
import org.gradle.api.internal.tasks.testing.logging.TestWorkerProgressListener;
import org.gradle.api.internal.tasks.testing.report.DefaultTestReport;
import org.gradle.api.internal.tasks.testing.report.TestReporter;
import org.gradle.api.internal.tasks.testing.results.StateTrackingTestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ReplacedBy;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.nativeintegration.network.HostnameLookup;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.util.ClosureBackedAction;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract class for all test task.
 *
 * <ul>
 *     <li>Support for test listeners</li>
 *     <li>Support for reporting</li>
 *     <li>Support for report linking in the console output</li>
 * </ul>
 *
 * <p><b>Note:</b> This abstract class is not intended for implementation by build script or plugin authors.
 *
 * @since 4.4
 */
public abstract class AbstractTestTask extends ConventionTask implements VerificationTask, Reporting<TestTaskReports> {
    private final DefaultTestFilter filter;
    private final TestTaskReports reports;
    private final ListenerBroadcast<TestListener> testListenerBroadcaster;
    private final ListenerBroadcast<TestOutputListener> testOutputListenerBroadcaster;
    private final ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster;
    private final TestLoggingContainer testLogging;
    private final DirectoryProperty binaryResultsDirectory;
    private TestReporter testReporter;
    private boolean ignoreFailures;
    private boolean failFast;

    public AbstractTestTask() {
        Instantiator instantiator = getInstantiator();
        testLogging = instantiator.newInstance(DefaultTestLoggingContainer.class, instantiator);
        ListenerManager listenerManager = getListenerManager();
        testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);
        testOutputListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestOutputListener.class);
        testListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestListener.class);
        binaryResultsDirectory = getProject().getObjects().directoryProperty();

        reports = getProject().getObjects().newInstance(DefaultTestTaskReports.class, this);
        reports.getJunitXml().setEnabled(true);
        reports.getHtml().setEnabled(true);

        filter = instantiator.newInstance(DefaultTestFilter.class);
    }

    @Inject
    protected ProgressLoggerFactory getProgressLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected HostnameLookup getHostnameLookup() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected BuildOperationExecutor getBuildOperationExecutor() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ListenerManager getListenerManager() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileSystemOperations getFileSystemOperations() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates test executer. For internal use only.
     *
     * @since 4.4
     */
    protected abstract TestExecuter<? extends TestExecutionSpec> createTestExecuter();

    /**
     * Creates test execution specification. For internal use only.
     *
     * @since 4.4
     */
    protected abstract TestExecutionSpec createTestExecutionSpec();

    @Internal
    @VisibleForTesting
    ListenerBroadcast<TestOutputListener> getTestOutputListenerBroadcaster() {
        return testOutputListenerBroadcaster;
    }

    @Internal
    @VisibleForTesting
    ListenerBroadcast<TestListenerInternal> getTestListenerInternalBroadcaster() {
        return testListenerInternalBroadcaster;
    }

    @VisibleForTesting
    void setTestReporter(TestReporter testReporter) {
        this.testReporter = testReporter;
    }

    // only way I know of to determine current log level
    private LogLevel determineCurrentLogLevel() {
        for (LogLevel level : LogLevel.values()) {
            if (getLogger().isEnabled(level)) {
                return level;
            }
        }
        throw new AssertionError("could not determine current log level");
    }

    /**
     * Returns the root folder for the test results in internal binary format.
     *
     * @return the test result directory, containing the test results in binary format.
     */
    @ReplacedBy("binaryResultsDirectory")
    @Deprecated
    public File getBinResultsDir() {
        return binaryResultsDirectory.getAsFile().getOrNull();
    }

    /**
     * Sets the root folder for the test results in internal binary format.
     *
     * @param binResultsDir The root folder
     */
    @Deprecated
    public void setBinResultsDir(File binResultsDir) {
        this.binaryResultsDirectory.set(binResultsDir);
    }

    /**
     * Returns the root directory property for the test results in internal binary format.
     *
     * @since 4.4
     */
    @OutputDirectory
    public DirectoryProperty getBinaryResultsDirectory() {
        return binaryResultsDirectory;
    }

    /**
     * Registers a test listener with this task. Consider also the following handy methods for quicker hooking into test execution: {@link #beforeTest(groovy.lang.Closure)}, {@link
     * #afterTest(groovy.lang.Closure)}, {@link #beforeSuite(groovy.lang.Closure)}, {@link #afterSuite(groovy.lang.Closure)} <p> This listener will NOT be notified of tests executed by other tasks. To
     * get that behavior, use {@link org.gradle.api.invocation.Gradle#addListener(Object)}.
     *
     * @param listener The listener to add.
     */
    public void addTestListener(TestListener listener) {
        testListenerBroadcaster.add(listener);
    }

    /**
     * Registers a output listener with this task. Quicker way of hooking into output events is using the {@link #onOutput(groovy.lang.Closure)} method.
     *
     * @param listener The listener to add.
     */
    public void addTestOutputListener(TestOutputListener listener) {
        testOutputListenerBroadcaster.add(listener);
    }

    /**
     * Unregisters a test listener with this task.  This method will only remove listeners that were added by calling {@link #addTestListener(TestListener)} on this task. If the listener was
     * registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestListener(TestListener listener) {
        testListenerBroadcaster.remove(listener);
    }

    /**
     * Unregisters a test output listener with this task.  This method will only remove listeners that were added by calling {@link #addTestOutputListener(TestOutputListener)} on this task.  If the
     * listener was registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestOutputListener(TestOutputListener listener) {
        testOutputListenerBroadcaster.remove(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
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

    /**
     * Adds a closure to be notified when output from the test received. A {@link TestDescriptor} and {@link TestOutputEvent} instance are
     * passed to the closure as a parameter.
     *
     * <pre class='autoTested'>
     * apply plugin: 'java'
     *
     * test {
     *    onOutput { descriptor, event -&gt;
     *        if (event.destination == TestOutputEvent.Destination.StdErr) {
     *            logger.error("Test: " + descriptor + ", error: " + event.message)
     *        }
     *    }
     * }
     * </pre>
     *
     * @param closure The closure to call.
     */
    public void onOutput(Closure closure) {
        testOutputListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("onOutput", closure));
    }

    /**
     * <p>Adds a closure to be notified before a test suite is executed. A {@link TestDescriptor} instance is passed to the closure as a parameter.</p>
     *
     * <p>This method is also called before any test suites are executed. The provided descriptor will have a null parent suite.</p>
     *
     * @param closure The closure to call.
     */
    public void beforeSuite(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("beforeSuite", closure));
    }

    /**
     * <p>Adds a closure to be notified after a test suite has executed. A {@link TestDescriptor} and {@link TestResult} instance are passed to the closure as a
     * parameter.</p>
     *
     * <p>This method is also called after all test suites are executed. The provided descriptor will have a null parent suite.</p>
     *
     * @param closure The closure to call.
     */
    public void afterSuite(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("afterSuite", closure));
    }

    /**
     * Adds a closure to be notified before a test is executed. A {@link TestDescriptor} instance is passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void beforeTest(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("beforeTest", closure));
    }

    /**
     * Adds a closure to be notified after a test has executed. A {@link TestDescriptor} and {@link TestResult} instance are passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void afterTest(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("afterTest", closure));
    }

    /**
     * Allows to set options related to which test events are logged to the console, and on which detail level. For example, to show more information about exceptions use:
     *
     * <pre class='autoTested'>
     * apply plugin: 'java'
     *
     * test.testLogging {
     *     exceptionFormat "full"
     * }
     * </pre>
     *
     * For further information see {@link TestLoggingContainer}.
     *
     * @return this
     */
    @Internal
    // TODO:LPTR Should be @Nested with @Console inside
    public TestLoggingContainer getTestLogging() {
        return testLogging;
    }

    /**
     * Allows configuring the logging of the test execution, for example log eagerly the standard output, etc.
     *
     * <pre class='autoTested'>
     * apply plugin: 'java'
     *
     * // makes the standard streams (err and out) visible at console when running tests
     * test.testLogging {
     *    showStandardStreams = true
     * }
     * </pre>
     *
     * @param closure configure closure
     */
    public void testLogging(Closure closure) {
        ConfigureUtil.configure(closure, testLogging);
    }

    /**
     * Allows configuring the logging of the test execution, for example log eagerly the standard output, etc.
     *
     * <pre class='autoTested'>
     * apply plugin: 'java'
     *
     * // makes the standard streams (err and out) visible at console when running tests
     * test.testLogging {
     *    showStandardStreams = true
     * }
     * </pre>
     *
     * @param action configure action
     * @since 3.5
     */
    public void testLogging(Action<? super TestLoggingContainer> action) {
        action.execute(testLogging);
    }

    @TaskAction
    public void executeTests() {
        if (getFilter().isFailOnNoMatchingTests() && (!getFilter().getIncludePatterns().isEmpty()
            || !filter.getCommandLineIncludePatterns().isEmpty()
            || !filter.getExcludePatterns().isEmpty())) {
            addTestListener(new NoMatchingTestsReporter(createNoMatchingTestErrorMessage()));
        }

        LogLevel currentLevel = determineCurrentLogLevel();
        TestLogging levelLogging = getTestLogging().get(currentLevel);
        TestExceptionFormatter exceptionFormatter = getExceptionFormatter(levelLogging);
        TestEventLogger eventLogger = new TestEventLogger(getTextOutputFactory(), currentLevel, levelLogging, exceptionFormatter);
        addTestListener(eventLogger);
        addTestOutputListener(eventLogger);

        TestExecutionSpec executionSpec = createTestExecutionSpec();

        final File binaryResultsDir = getBinResultsDir();
        FileSystemOperations fs = getFileSystemOperations();
        fs.delete(new Action<DeleteSpec>() {
            @Override
            public void execute(DeleteSpec spec) {
                spec.delete(binaryResultsDir);
            }
        });
        binaryResultsDir.mkdirs();

        Map<String, TestClassResult> results = new HashMap<String, TestClassResult>();
        TestOutputStore testOutputStore = new TestOutputStore(binaryResultsDir);

        TestOutputStore.Writer outputWriter = testOutputStore.writer();
        TestReportDataCollector testReportDataCollector = new TestReportDataCollector(results, outputWriter);

        addTestListener(testReportDataCollector);
        addTestOutputListener(testReportDataCollector);

        TestCountLogger testCountLogger = new TestCountLogger(getProgressLoggerFactory());
        addTestListener(testCountLogger);

        getTestListenerInternalBroadcaster().add(new TestListenerAdapter(testListenerBroadcaster.getSource(), getTestOutputListenerBroadcaster().getSource()));

        ProgressLogger parentProgressLogger = getProgressLoggerFactory().newOperation(AbstractTestTask.class);
        parentProgressLogger.setDescription("Test Execution");
        parentProgressLogger.started();
        TestWorkerProgressListener testWorkerProgressListener = new TestWorkerProgressListener(getProgressLoggerFactory(), parentProgressLogger);
        getTestListenerInternalBroadcaster().add(testWorkerProgressListener);

        TestExecuter<TestExecutionSpec> testExecuter = Cast.uncheckedNonnullCast(createTestExecuter());
        TestListenerInternal resultProcessorDelegate = getTestListenerInternalBroadcaster().getSource();
        if (failFast) {
            resultProcessorDelegate = new FailFastTestListenerInternal(testExecuter, resultProcessorDelegate);
        }

        TestResultProcessor resultProcessor = new StateTrackingTestResultProcessor(resultProcessorDelegate);

        try {
            testExecuter.execute(executionSpec, resultProcessor);
        } finally {
            parentProgressLogger.completed();
            testWorkerProgressListener.completeAll();
            testListenerBroadcaster.removeAll();
            getTestOutputListenerBroadcaster().removeAll();
            getTestListenerInternalBroadcaster().removeAll();
            outputWriter.close();
        }

        new TestResultSerializer(binaryResultsDir).write(results.values());

        createReporting(results, testOutputStore);

        if (testCountLogger.hadFailures()) {
            handleTestFailures();
        }
    }

    private String createNoMatchingTestErrorMessage() {
        return "No tests found for given includes: "
            + Joiner.on(' ').join(getNoMatchingTestErrorReasons());
    }

    /**
     * Returns the reasons for no matching test error.
     *
     * @since 4.5
     */
    @Internal
    protected List<String> getNoMatchingTestErrorReasons() {
        List<String> reasons = Lists.newArrayList();
        if (!getFilter().getIncludePatterns().isEmpty()) {
            reasons.add(getFilter().getIncludePatterns() + "(filter.includeTestsMatching)");
        }
        if (!filter.getCommandLineIncludePatterns().isEmpty()) {
            reasons.add(filter.getCommandLineIncludePatterns() + "(--tests filter)");
        }
        return reasons;
    }

    private void createReporting(Map<String, TestClassResult> results, TestOutputStore testOutputStore) {
        TestResultsProvider testResultsProvider = new InMemoryTestResultsProvider(results.values(), testOutputStore);

        try {
            if (testReporter == null) {
                testReporter = new DefaultTestReport(getBuildOperationExecutor());
            }

            JUnitXmlReport junitXml = reports.getJunitXml();
            if (junitXml.isEnabled()) {
                TestOutputAssociation outputAssociation = junitXml.isOutputPerTestCase()
                    ? TestOutputAssociation.WITH_TESTCASE
                    : TestOutputAssociation.WITH_SUITE;
                Binary2JUnitXmlReportGenerator binary2JUnitXmlReportGenerator = new Binary2JUnitXmlReportGenerator(junitXml.getDestination(), testResultsProvider, outputAssociation, getBuildOperationExecutor(), getHostnameLookup().getHostname());
                binary2JUnitXmlReportGenerator.generate();
            }

            DirectoryReport html = reports.getHtml();
            if (!html.isEnabled()) {
                getLogger().info("Test report disabled, omitting generation of the HTML test report.");
            } else {
                testReporter.generateReport(testResultsProvider, html.getDestination());
            }
        } finally {
            CompositeStoppable.stoppable(testResultsProvider).stop();
            testReporter = null;
        }
    }

    /**
     * Sets the test name patterns to be included in execution.
     * Classes or method names are supported, wildcard '*' is supported.
     * For more information see the user guide chapter on testing.
     *
     * For more information on supported patterns see {@link TestFilter}
     */
    @Option(option = "tests", description = "Sets test class or method name to be included, '*' is supported.")
    public AbstractTestTask setTestNameIncludePatterns(List<String> testNamePattern) {
        filter.setCommandLineIncludePatterns(testNamePattern);
        return this;
    }

    @Internal
    boolean getFailFast() {
        return failFast;
    }

    void setFailFast(boolean failFast) {
        this.failFast = failFast;
    }

    /**
     * The reports that this task potentially produces.
     *
     * @return The reports that this task potentially produces
     */
    @Override
    @Nested
    public TestTaskReports getReports() {
        return reports;
    }

    /**
     * Configures the reports that this task potentially produces.
     *
     * @param closure The configuration
     * @return The reports that this task potentially produces
     */
    @Override
    public TestTaskReports reports(Closure closure) {
        return reports(new ClosureBackedAction<TestTaskReports>(closure));
    }

    /**
     * Configures the reports that this task potentially produces.
     *
     * @param configureAction The configuration
     * @return The reports that this task potentially produces
     */
    @Override
    public TestTaskReports reports(Action<? super TestTaskReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    private void handleTestFailures() {
        String message = "There were failing tests";

        DirectoryReport htmlReport = getReports().getHtml();
        if (htmlReport.isEnabled()) {
            String reportUrl = new ConsoleRenderer().asClickableFileUrl(htmlReport.getEntryPoint());
            message = message.concat(". See the report at: " + reportUrl);
        } else {
            DirectoryReport junitXmlReport = getReports().getJunitXml();
            if (junitXmlReport.isEnabled()) {
                String resultsUrl = new ConsoleRenderer().asClickableFileUrl(junitXmlReport.getEntryPoint());
                message = message.concat(". See the results at: " + resultsUrl);
            }
        }

        if (getIgnoreFailures()) {
            getLogger().warn(message);
        } else {
            throw new GradleException(message);
        }
    }

    /**
     * Allows filtering tests for execution.
     *
     * @return filter object
     * @since 1.10
     */
    @Nested
    public TestFilter getFilter() {
        return filter;
    }
}
