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

import com.google.common.base.Joiner;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.exceptions.MarkedVerificationException;
import org.gradle.api.internal.tasks.testing.DefaultTestTaskReports;
import org.gradle.api.internal.tasks.testing.FailFastTestListenerInternal;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.result.Binary2JUnitXmlReportGenerator;
import org.gradle.api.internal.tasks.testing.junit.result.InMemoryTestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.JUnitXmlResultOptions;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
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
import org.gradle.api.internal.tasks.testing.report.HtmlTestReport;
import org.gradle.api.internal.tasks.testing.results.StateTrackingTestResultProcessor;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.results.TestListenerInternal;
import org.gradle.api.logging.LogLevel;
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
import org.gradle.internal.Describables;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.logging.ConsoleRenderer;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.nativeintegration.network.HostnameLookup;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.util.internal.ConfigureUtil;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Abstract class for all test tasks.
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
@DisableCachingByDefault(because = "Abstract super-class, not to be instantiated directly")
public abstract class AbstractTestTask extends ConventionTask implements VerificationTask, Reporting<TestTaskReports> {

    /**
     * Wraps a list of listeners to subscribe, and lazily configures an anonymous broadcaster with those listeners when requested.
     * Instances of this class are suitable for serialization (as long as listeners are serializable as well).
     */
    private class BroadcastSubscriptions<T> {
        private final Class<T> listenerClass;
        private final List<Object> subscribedListeners = new LinkedList<Object>();
        private transient ListenerBroadcast<T> broadcaster;

        private BroadcastSubscriptions(Class<T> listenerClass) {
            this.listenerClass = listenerClass;
        }

        @SuppressWarnings("unchecked")
        ListenerBroadcast<T> get() {
            if (broadcaster == null) {
                broadcaster = getListenerManager().createAnonymousBroadcaster(listenerClass);
                for (Object listener : subscribedListeners) {
                    if (listenerClass.isInstance(listener)) {
                        broadcaster.add((T) listener);
                    } else {
                        broadcaster.add((Dispatch<MethodInvocation>) listener);
                    }
                }
            }
            return broadcaster;
        }

        void addListener(T listener) {
            subscribedListeners.add(listener);
            if (broadcaster != null) {
                broadcaster.add(listener);
            }
        }

        void addListener(Dispatch<MethodInvocation> listener) {
            subscribedListeners.add(listener);
            if (broadcaster != null) {
                broadcaster.add(listener);
            }
        }

        void removeListener(Object listener) {
            subscribedListeners.remove(listener);
            if (broadcaster != null) {
                broadcaster.remove(listener);
            }
        }

        void removeAllListeners() {
            subscribedListeners.clear();
            if (broadcaster != null) {
                broadcaster.removeAll();
            }
        }
    }

    private final DefaultTestFilter filter;
    private final TestTaskReports reports;
    private final BroadcastSubscriptions<TestListener> testListenerSubscriptions;
    private final BroadcastSubscriptions<TestOutputListener> testOutputListenerSubscriptions;
    private final TestLoggingContainer testLogging;
    private final DirectoryProperty binaryResultsDirectory;
    private boolean ignoreFailures;
    private boolean failFast;

    public AbstractTestTask() {
        Instantiator instantiator = getInstantiator();
        testLogging = instantiator.newInstance(DefaultTestLoggingContainer.class, instantiator);
        testListenerSubscriptions = new BroadcastSubscriptions<TestListener>(TestListener.class);
        testOutputListenerSubscriptions = new BroadcastSubscriptions<TestOutputListener>(TestOutputListener.class);
        binaryResultsDirectory = getProject().getObjects().directoryProperty();

        reports = getProject().getObjects().newInstance(DefaultTestTaskReports.class, Describables.quoted("Task", getIdentityPath()));
        reports.getJunitXml().getRequired().set(true);
        reports.getHtml().getRequired().set(true);

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
    protected BuildOperationRunner getBuildOperationRunner() {
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
        testListenerSubscriptions.addListener(listener);
    }

    private void addDispatchAsTestListener(String methodName, Closure closure) {
        testListenerSubscriptions.addListener(new ClosureBackedMethodInvocationDispatch(methodName, closure));
    }

    /**
     * Registers a output listener with this task. Quicker way of hooking into output events is using the {@link #onOutput(groovy.lang.Closure)} method.
     *
     * @param listener The listener to add.
     */
    public void addTestOutputListener(TestOutputListener listener) {
        testOutputListenerSubscriptions.addListener(listener);
    }

    private void addDispatchAsTestOutputListener(String methodName, Closure closure) {
        testOutputListenerSubscriptions.addListener(new ClosureBackedMethodInvocationDispatch(methodName, closure));
    }

    /**
     * Unregisters a test listener with this task.  This method will only remove listeners that were added by calling {@link #addTestListener(TestListener)} on this task. If the listener was
     * registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestListener(TestListener listener) {
        testListenerSubscriptions.removeListener(listener);
    }

    /**
     * Unregisters a test output listener with this task.  This method will only remove listeners that were added by calling {@link #addTestOutputListener(TestOutputListener)} on this task.  If the
     * listener was registered with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything. Instead, use {@link
     * org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestOutputListener(TestOutputListener listener) {
        testOutputListenerSubscriptions.removeListener(listener);
    }

    /**
     * {@inheritDoc}
     */
    @Internal
    @Override
    @ToBeReplacedByLazyProperty
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
        addDispatchAsTestOutputListener("onOutput", closure);
    }

    /**
     * <p>Adds a closure to be notified before a test suite is executed. A {@link TestDescriptor} instance is passed to the closure as a parameter.</p>
     *
     * <p>This method is also called before any test suites are executed. The provided descriptor will have a null parent suite.</p>
     *
     * @param closure The closure to call.
     */
    public void beforeSuite(Closure closure) {
        addDispatchAsTestListener("beforeSuite", closure);
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
        addDispatchAsTestListener("afterSuite", closure);
    }

    /**
     * Adds a closure to be notified before a test is executed. A {@link TestDescriptor} instance is passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void beforeTest(Closure closure) {
        addDispatchAsTestListener("beforeTest", closure);
    }

    /**
     * Adds a closure to be notified after a test has executed. A {@link TestDescriptor} and {@link TestResult} instance are passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void afterTest(Closure closure) {
        addDispatchAsTestListener("afterTest", closure);
    }

    /**
     * Allows to set options related to which test events are logged to the console, and on which detail level. For example, to show more information about exceptions use:
     *
     * <pre class='autoTested'>
     * apply plugin: 'java'
     *
     * test.testLogging {
     *     exceptionFormat = "full"
     * }
     * </pre>
     *
     * For further information see {@link TestLoggingContainer}.
     *
     * @return this
     */
    @Nested
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
    public void testLogging(@DelegatesTo(TestLoggingContainer.class) Closure closure) {
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
        LogLevel currentLevel = determineCurrentLogLevel();
        TestLogging levelLogging = getTestLogging().get(currentLevel);
        TestExceptionFormatter exceptionFormatter = getExceptionFormatter(levelLogging);
        TestEventLogger eventLogger = new TestEventLogger(getTextOutputFactory(), currentLevel, levelLogging, exceptionFormatter);
        addTestListener(eventLogger);
        addTestOutputListener(eventLogger);

        TestExecutionSpec executionSpec = createTestExecutionSpec();

        final File binaryResultsDir = getBinaryResultsDirectory().getAsFile().get();
        FileSystemOperations fs = getFileSystemOperations();
        fs.delete(spec -> spec.delete(binaryResultsDir));

        try {
            Files.createDirectories(binaryResultsDir.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Record test events to `results`, and test outputs to `testOutputStore`
        Map<String, TestClassResult> results = new HashMap<>();
        TestOutputStore testOutputStore = new TestOutputStore(binaryResultsDir);
        TestOutputStore.Writer outputWriter = testOutputStore.writer();
        TestReportDataCollector testReportDataCollector = new TestReportDataCollector(results, outputWriter);
        addTestListener(testReportDataCollector);
        addTestOutputListener(testReportDataCollector);

        // Log number of completed, skipped, and failed tests to console, and update live as count changes
        TestCountLogger testCountLogger = new TestCountLogger(getProgressLoggerFactory());
        addTestListener(testCountLogger);

        // Adapt all listeners registered with addTestListener() and addTestOutputListener() to TestListenerInternal
        ListenerBroadcast<TestListenerInternal> testListenerInternalBroadcaster = getListenerManager().createAnonymousBroadcaster(TestListenerInternal.class);
        testListenerInternalBroadcaster.add(new TestListenerAdapter(testListenerSubscriptions.get().getSource(), testOutputListenerSubscriptions.get().getSource()));

        // Log to the console which tests are currently executing, and update live as current tests change
        ProgressLogger parentProgressLogger = getProgressLoggerFactory().newOperation(AbstractTestTask.class);
        parentProgressLogger.setDescription("Test Execution");
        parentProgressLogger.started();
        TestWorkerProgressListener testWorkerProgressListener = new TestWorkerProgressListener(getProgressLoggerFactory(), parentProgressLogger);
        testListenerInternalBroadcaster.add(testWorkerProgressListener);

        TestExecuter<TestExecutionSpec> testExecuter = Cast.uncheckedNonnullCast(createTestExecuter());
        TestListenerInternal resultProcessorDelegate = testListenerInternalBroadcaster.getSource();
        if (failFast) {
            resultProcessorDelegate = new FailFastTestListenerInternal(testExecuter, resultProcessorDelegate);
        }

        TestResultProcessor resultProcessor = new StateTrackingTestResultProcessor(resultProcessorDelegate);

        try {
            testExecuter.execute(executionSpec, resultProcessor);
        } finally {
            parentProgressLogger.completed();
            testWorkerProgressListener.completeAll();
            testListenerSubscriptions.removeAllListeners();
            testOutputListenerSubscriptions.removeAllListeners();
            testListenerInternalBroadcaster.removeAll();
            outputWriter.close();
        }

        // Write binary results to disk
        new TestResultSerializer(binaryResultsDir).write(results.values());

        // Generate HTML and XML reports
        createReporting(results, testOutputStore);

        handleCollectedResults(testCountLogger);
    }

    private void handleCollectedResults(TestCountLogger testCountLogger) {
        if (testCountLogger.hadFailures()) {
            handleTestFailures();
        } else if (testCountLogger.getTotalTests() == 0) {
            if (testsAreNotFiltered()) {
                emitDeprecationMessage();
            } else if (shouldFailOnNoMatchingTests()) {
                throw new TestExecutionException(createNoMatchingTestErrorMessage());
            }
        }
    }

    private boolean shouldFailOnNoMatchingTests() {
        return patternFiltersSpecified() && filter.isFailOnNoMatchingTests();
    }

    private void emitDeprecationMessage() {
        DeprecationLogger.deprecateBehaviour("No test executed.")
            .withAdvice("There are test sources present but no test was executed. Please check your test configuration.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "test_task_fail_on_no_test_executed")
            .nagUser();
    }

    boolean testsAreNotFiltered() {
        return !patternFiltersSpecified();
    }

    private boolean patternFiltersSpecified() {
        return !filter.getIncludePatterns().isEmpty()
            || !filter.getCommandLineIncludePatterns().isEmpty()
            || !filter.getExcludePatterns().isEmpty();
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
        List<String> reasons = new ArrayList<String>();
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

            JUnitXmlReport junitXml = reports.getJunitXml();
            if (junitXml.getRequired().get()) {
                JUnitXmlResultOptions xmlResultOptions = new JUnitXmlResultOptions(
                    junitXml.isOutputPerTestCase(),
                    junitXml.getMergeReruns().get(),
                    junitXml.getIncludeSystemOutLog().get(),
                    junitXml.getIncludeSystemErrLog().get()
                );
                Binary2JUnitXmlReportGenerator binary2JUnitXmlReportGenerator = new Binary2JUnitXmlReportGenerator(
                    junitXml.getOutputLocation().getAsFile().get(),
                    testResultsProvider,
                    xmlResultOptions,
                    getBuildOperationRunner(),
                    getBuildOperationExecutor(),
                    getHostnameLookup().getHostname());
                binary2JUnitXmlReportGenerator.generate();
            }

            DirectoryReport html = reports.getHtml();
            if (!html.getRequired().get()) {
                getLogger().info("Test report disabled, omitting generation of the HTML test report.");
            } else {
                HtmlTestReport htmlReport = new HtmlTestReport(getBuildOperationRunner(), getBuildOperationExecutor());
                htmlReport.generateReport(testResultsProvider, html.getOutputLocation().getAsFile().getOrNull());
            }
        } finally {
            CompositeStoppable.stoppable(testResultsProvider).stop();
        }
    }

    /**
     * Sets the test name patterns to be included in execution.
     * Classes or method names are supported, wildcard '*' is supported.
     * For more information see the user guide chapter on testing.
     *
     * For more information on supported patterns see {@link TestFilter}
     */
    @Option(option = "tests", description = "Sets test class or method name to be included (in addition to the test task filters), '*' is supported.")
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
        if (htmlReport.getRequired().get()) {
            String reportUrl = new ConsoleRenderer().asClickableFileUrl(htmlReport.getEntryPoint());
            message = message.concat(". See the report at: " + reportUrl);
        } else {
            DirectoryReport junitXmlReport = getReports().getJunitXml();
            if (junitXmlReport.getRequired().get()) {
                String resultsUrl = new ConsoleRenderer().asClickableFileUrl(junitXmlReport.getEntryPoint());
                message = message.concat(". See the results at: " + resultsUrl);
            }
        }

        if (getIgnoreFailures()) {
            getLogger().warn(message);
        } else {
            throw new MarkedVerificationException(message);
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
