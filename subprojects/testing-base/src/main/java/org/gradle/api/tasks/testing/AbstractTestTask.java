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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryVar;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.testing.DefaultTestTaskReports;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.internal.tasks.testing.junit.report.DefaultTestReport;
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter;
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
import org.gradle.api.internal.tasks.testing.results.StateTrackingTestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.VerificationTask;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.process.ProcessForkOptions;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic test task.
 *
 * @param <T>
 * @since 4.2
 */
public abstract class AbstractTestTask<T extends AbstractTestTask> extends ConventionTask implements ProcessForkOptions, VerificationTask, Reporting<TestTaskReports> {
    private final ListenerBroadcast<TestListener> testListenerBroadcaster;
    private final ListenerBroadcast<TestOutputListener> testOutputListenerBroadcaster;
    private final ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster;
    private final TestLoggingContainer testLogging;
    private final TestTaskReports reports;

    private final Class<T> taskType;
    private final DirectoryVar binResultsDir;
    private final ProcessForkOptions forkOptions;

    private TestExecuter testExecuter;
    private boolean ignoreFailures;
    private TestReporter testReporter;

    public AbstractTestTask(Class<T> taskType) {
        ListenerManager listenerManager = getListenerManager();
        testListenerInternalBroadcaster = listenerManager.createAnonymousBroadcaster(TestListenerInternal.class);
        testListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestListener.class);
        testOutputListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestOutputListener.class);
        testLogging = getObjectFactory().newInstance(DefaultTestLoggingContainer.class);

        reports = getInstantiator().newInstance(DefaultTestTaskReports.class, this);
        reports.getJunitXml().setEnabled(true);
        reports.getHtml().setEnabled(true);

        this.taskType = taskType;
        this.binResultsDir = newOutputDirectory();
        this.forkOptions = createForkOptions();
    }

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected InetAddressFactory getInetAddressFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
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
    protected ListenerManager getListenerManager() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected BuildOperationExecutor getBuildOperationExecutor() {
        throw new UnsupportedOperationException();
    }

    protected abstract ProcessForkOptions createForkOptions();

    protected abstract TestExecuter createTestExecuter();

    @Internal
    ListenerBroadcast<TestListener> getTestListenerBroadcaster() {
        return testListenerBroadcaster;
    }

    @Internal
    ListenerBroadcast<TestListenerInternal> getTestListenerInternalBroadcaster() {
        return testListenerInternalBroadcaster;
    }

    @Internal
    ListenerBroadcast<TestOutputListener> getTestOutputListenerBroadcaster() {
        return testOutputListenerBroadcaster;
    }

    /**
     * The reports that this task potentially produces.
     *
     * @return The reports that this task potentially produces
     */
    @Nested
    @Override
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
     *
     * @param configureAction The configuration
     * @return The reports that this task potentially produces
     */
    @Override
    public TestTaskReports reports(Action<? super TestTaskReports> configureAction) {
        configureAction.execute(reports);
        return reports;
    }

    /**
     * ATM. for testing only
     */
    void setTestReporter(TestReporter testReporter) {
        this.testReporter = testReporter;
    }

    void setTestExecuter(TestExecuter testExecuter) {
        this.testExecuter = testExecuter;
    }

    /**
     * Returns the root folder for the test result in internal binary format.
     *
     * @return the test result directory, containing the test result in binary format.
     */
    @OutputDirectory
    @Incubating
    public File getBinResultsDir() {
        return binResultsDir.getAsFile().getOrNull();
    }

    /**
     * Sets the root folder for the test result in internal binary format.
     *
     * @param binResultsDir The root folder
     */
    @Incubating
    public void setBinResultsDir(File binResultsDir) {
        this.binResultsDir.set(binResultsDir);
    }

    public void setBinResultsDir(Provider<? extends Directory> binResultsDir) {
        this.binResultsDir.set(binResultsDir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Input
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public String getExecutable() {
        return forkOptions.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExecutable(String executable) {
        forkOptions.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExecutable(Object executable) {
        forkOptions.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T executable(Object executable) {
        forkOptions.executable(executable);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public File getWorkingDir() {
        return forkOptions.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkingDir(File dir) {
        forkOptions.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWorkingDir(Object dir) {
        forkOptions.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T workingDir(Object dir) {
        forkOptions.workingDir(dir);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    public Map<String, Object> getEnvironment() {
        return forkOptions.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnvironment(Map<String, ?> environmentVariables) {
        forkOptions.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T environment(Map<String, ?> environmentVariables) {
        forkOptions.environment(environmentVariables);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T environment(String name, Object value) {
        forkOptions.environment(name, value);
        return taskType.cast(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T copyTo(ProcessForkOptions options) {
        forkOptions.copyTo(options);
        return taskType.cast(this);
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

        testListenerInternalBroadcaster.add(new TestListenerAdapter(testListenerBroadcaster.getSource(), testOutputListenerBroadcaster.getSource()));

        ProgressLogger parentProgressLogger = getProgressLoggerFactory().newOperation(taskType);
        parentProgressLogger.setDescription("Test Execution");
        parentProgressLogger.started();
        TestWorkerProgressListener testWorkerProgressListener = new TestWorkerProgressListener(getProgressLoggerFactory(), parentProgressLogger);
        testListenerInternalBroadcaster.add(testWorkerProgressListener);

        TestResultProcessor resultProcessor = new StateTrackingTestResultProcessor(testListenerInternalBroadcaster.getSource());

        if (testExecuter == null) {
            testExecuter = createTestExecuter();
        }

//        JavaVersion javaVersion = getJavaVersion();
//        if (!javaVersion.isJava6Compatible()) {
//            throw new UnsupportedJavaRuntimeException("Support for test execution using Java 5 or earlier was removed in Gradle 3.0.");
//        }

        try {
            testExecuter.execute(this, resultProcessor);
        } finally {
            parentProgressLogger.completed();
            testExecuter = null;
            testWorkerProgressListener.completeAll();
            testListenerBroadcaster.removeAll();
            testOutputListenerBroadcaster.removeAll();
            testListenerInternalBroadcaster.removeAll();
            outputWriter.close();
        }

        new TestResultSerializer(binaryResultsDir).write(results.values());

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
                Binary2JUnitXmlReportGenerator binary2JUnitXmlReportGenerator = new Binary2JUnitXmlReportGenerator(junitXml.getDestination(), testResultsProvider, outputAssociation, getBuildOperationExecutor(), getInetAddressFactory().getHostname());
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
//            testFramework = null;
        }

        if (testCountLogger.hadFailures()) {
            handleTestFailures();
        }
    }

    protected abstract String createNoMatchingTestErrorMessage();

    // only way I know of to determine current log level
    private LogLevel determineCurrentLogLevel() {
        for (LogLevel level : LogLevel.values()) {
            if (getLogger().isEnabled(level)) {
                return level;
            }
        }
        throw new AssertionError("could not determine current log level");
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

        DirectoryReport htmlReport = reports.getHtml();
        if (htmlReport.isEnabled()) {
            String reportUrl = new ConsoleRenderer().asClickableFileUrl(htmlReport.getEntryPoint());
            message = message.concat(". See the report at: " + reportUrl);
        } else {
            DirectoryReport junitXmlReport = reports.getJunitXml();
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
     * Unregisters a test listener with this task.  This method will only remove listeners that were added by calling {@link #addTestListener(org.gradle.api.tasks.testing.TestListener)} on this task.
     * If the listener was registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestListener(TestListener listener) {
        testListenerBroadcaster.remove(listener);
    }

    /**
     * Unregisters a test output listener with this task.  This method will only remove listeners that were added by calling {@link #addTestOutputListener(org.gradle.api.tasks.testing.TestOutputListener)}
     * on this task.  If the listener was registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestOutputListener(TestOutputListener listener) {
        testOutputListenerBroadcaster.remove(listener);
    }

    /**
     * <p>Adds a closure to be notified before a test suite is executed. A {@link org.gradle.api.tasks.testing.TestDescriptor} instance is passed to the closure as a parameter.</p>
     *
     * <p>This method is also called before any test suites are executed. The provided descriptor will have a null parent suite.</p>
     *
     * @param closure The closure to call.
     */
    public void beforeSuite(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("beforeSuite", closure));
    }

    /**
     * <p>Adds a closure to be notified after a test suite has executed. A {@link org.gradle.api.tasks.testing.TestDescriptor} and {@link TestResult} instance are passed to the closure as a
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
     * Adds a closure to be notified before a test is executed. A {@link org.gradle.api.tasks.testing.TestDescriptor} instance is passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void beforeTest(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("beforeTest", closure));
    }

    /**
     * Adds a closure to be notified after a test has executed. A {@link org.gradle.api.tasks.testing.TestDescriptor} and {@link TestResult} instance are passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void afterTest(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("afterTest", closure));
    }

    /**
     * Adds a closure to be notified when output from the test received. A {@link org.gradle.api.tasks.testing.TestDescriptor} and {@link org.gradle.api.tasks.testing.TestOutputEvent} instance are
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
}
