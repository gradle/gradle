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

package org.gradle.api.tasks.testing;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.internal.tasks.testing.DefaultTestTaskReports;
import org.gradle.api.internal.tasks.testing.NoMatchingTestsReporter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junit.report.DefaultTestReport;
import org.gradle.api.internal.tasks.testing.junit.report.TestReporter;
import org.gradle.api.internal.tasks.testing.junit.result.*;
import org.gradle.api.internal.tasks.testing.logging.*;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.reporting.DirectoryReport;
import org.gradle.api.reporting.Reporting;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.ConsoleRenderer;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;
import java.io.File;
import java.util.*;

/**
 * Executes JUnit (3.8.x or 4.x) or TestNG tests. Test are always run in (one or more) separate JVMs.
 * The sample below shows various configuration options.
 *
 * <pre autoTested=''>
 * apply plugin: 'java' // adds 'test' task
 *
 * test {
 *   // enable TestNG support (default is JUnit)
 *   useTestNG()
 *
 *   // set a system property for the test JVM(s)
 *   systemProperty 'some.prop', 'value'
 *
 *   // explicitly include or exclude tests
 *   include 'org/foo/**'
 *   exclude 'org/boo/**'
 *
 *   // show standard out and standard error of the test JVM(s) on the console
 *   testLogging.showStandardStreams = true
 *
 *   // set heap size for the test JVM(s)
 *   minHeapSize = "128m"
 *   maxHeapSize = "512m"
 *
 *   // set JVM arguments for the test JVM(s)
 *   jvmArgs '-XX:MaxPermSize=256m'
 *
 *   // listen to events in the test execution lifecycle
 *   beforeTest { descriptor ->
 *      logger.lifecycle("Running test: " + descriptor)
 *   }
 *
 *   // listen to standard out and standard error of the test JVM(s)
 *   onOutput { descriptor, event ->
 *      logger.lifecycle("Test: " + descriptor + " produced standard out/err: " + event.message )
 *   }
 * }
 * </pre>
 * <p>
 * The test process can be started in debug mode (see {@link #getDebug()}) in an ad-hoc manner by supplying the `--debug-jvm` switch when invoking the build.
 * <pre>
 * gradle someTestTask --debug-jvm
 * </pre>

 */
public class Test extends ConventionTask implements JavaForkOptions, PatternFilterable, VerificationTask, Reporting<TestTaskReports> {

    private final ListenerBroadcast<TestListener> testListenerBroadcaster;
    private final ListenerBroadcast<TestOutputListener> testOutputListenerBroadcaster;
    private final TestLoggingContainer testLogging;
    private final DefaultJavaForkOptions forkOptions;
    private final DefaultTestFilter filter;

    private TestExecuter testExecuter;
    private List<File> testSrcDirs = new ArrayList<File>();
    private File testClassesDir;
    private File binResultsDir;
    private PatternFilterable patternSet = new PatternSet();
    private boolean ignoreFailures;
    private FileCollection classpath;
    private TestFramework testFramework;
    private boolean scanForTestClasses = true;
    private long forkEvery;
    private int maxParallelForks = 1;
    private TestReporter testReporter;

    @Nested
    private final DefaultTestTaskReports reports;

    public Test() {
        ListenerManager listenerManager = getListenerManager();
        testListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestListener.class);
        testOutputListenerBroadcaster = listenerManager.createAnonymousBroadcaster(TestOutputListener.class);
        forkOptions = new DefaultJavaForkOptions(getFileResolver());
        forkOptions.setEnableAssertions(true);
        Instantiator instantiator = getInstantiator();
        testLogging = instantiator.newInstance(DefaultTestLoggingContainer.class, instantiator);

        reports = instantiator.newInstance(DefaultTestTaskReports.class, this);
        reports.getJunitXml().setEnabled(true);
        reports.getHtml().setEnabled(true);

        filter = instantiator.newInstance(DefaultTestFilter.class);
    }

    @Inject
    protected ProgressLoggerFactory getProgressLoggerFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Instantiator getInstantiator() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ActorFactory getActorFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Factory<WorkerProcessBuilder> getProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
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
     * {@inheritDoc}
     */
    @Input
    public File getWorkingDir() {
        return forkOptions.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(Object dir) {
        forkOptions.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public Test workingDir(Object dir) {
        forkOptions.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public String getExecutable() {
        return forkOptions.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    public Test executable(Object executable) {
        forkOptions.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(Object executable) {
        forkOptions.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public Map<String, Object> getSystemProperties() {
        return forkOptions.getSystemProperties();
    }

    /**
     * {@inheritDoc}
     */
    public void setSystemProperties(Map<String, ?> properties) {
        forkOptions.setSystemProperties(properties);
    }

    /**
     * {@inheritDoc}
     */
    public Test systemProperties(Map<String, ?> properties) {
        forkOptions.systemProperties(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test systemProperty(String name, Object value) {
        forkOptions.systemProperty(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public FileCollection getBootstrapClasspath() {
        return forkOptions.getBootstrapClasspath();
    }

    /**
     * {@inheritDoc}
     */
    public void setBootstrapClasspath(FileCollection classpath) {
        forkOptions.setBootstrapClasspath(classpath);
    }

    /**
     * {@inheritDoc}
     */
    public Test bootstrapClasspath(Object... classpath) {
        forkOptions.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getMinHeapSize() {
        return forkOptions.getMinHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public String getDefaultCharacterEncoding() {
        return forkOptions.getDefaultCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        forkOptions.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    /**
     * {@inheritDoc}
     */
    public void setMinHeapSize(String heapSize) {
        forkOptions.setMinHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    public String getMaxHeapSize() {
        return forkOptions.getMaxHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxHeapSize(String heapSize) {
        forkOptions.setMaxHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public List<String> getJvmArgs() {
        return forkOptions.getJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setJvmArgs(Iterable<?> arguments) {
        forkOptions.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public Test jvmArgs(Iterable<?> arguments) {
        forkOptions.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test jvmArgs(Object... arguments) {
        forkOptions.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public boolean getEnableAssertions() {
        return forkOptions.getEnableAssertions();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnableAssertions(boolean enabled) {
        forkOptions.setEnableAssertions(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public boolean getDebug() {
        return forkOptions.getDebug();
    }

    /**
     * {@inheritDoc}
     */
    @Option(option = "debug-jvm", description = "Enable debugging for the test process. The process is started suspended and listening on port 5005. [INCUBATING]")
    public void setDebug(boolean enabled) {
        forkOptions.setDebug(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getAllJvmArgs() {
        return forkOptions.getAllJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setAllJvmArgs(Iterable<?> arguments) {
        forkOptions.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getEnvironment() {
        return forkOptions.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    public Test environment(Map<String, ?> environmentVariables) {
        forkOptions.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test environment(String name, Object value) {
        forkOptions.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void setEnvironment(Map<String, ?> environmentVariables) {
        forkOptions.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    public Test copyTo(ProcessForkOptions target) {
        forkOptions.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test copyTo(JavaForkOptions target) {
        forkOptions.copyTo(target);
        return this;
    }

    @TaskAction
    public void executeTests() {
        LogLevel currentLevel = getCurrentLogLevel();
        TestLogging levelLogging = testLogging.get(currentLevel);
        TestExceptionFormatter exceptionFormatter = getExceptionFormatter(levelLogging);
        TestEventLogger eventLogger = new TestEventLogger(getTextOutputFactory(), currentLevel, levelLogging, exceptionFormatter);
        addTestListener(eventLogger);
        addTestOutputListener(eventLogger);
        if (!getFilter().getIncludePatterns().isEmpty()) {
            addTestListener(new NoMatchingTestsReporter("No tests found for given includes: " + getFilter().getIncludePatterns()));
        }

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

        TestResultProcessor resultProcessor = new TestListenerAdapter(
                getTestListenerBroadcaster().getSource(), testOutputListenerBroadcaster.getSource());

        if (testExecuter == null) {
            testExecuter = new DefaultTestExecuter(getProcessBuilderFactory(), getActorFactory());
        }

        try {
            testExecuter.execute(this, resultProcessor);
        } finally {
            testExecuter = null;
            testListenerBroadcaster.removeAll();
            testOutputListenerBroadcaster.removeAll();
            outputWriter.close();
        }

        new TestResultSerializer(binaryResultsDir).write(results.values());

        TestResultsProvider testResultsProvider = new InMemoryTestResultsProvider(results.values(), testOutputStore.reader());

        try {
            if (testReporter == null) {
                testReporter = new DefaultTestReport();
            }

            JUnitXmlReport junitXml = reports.getJunitXml();
            if (junitXml.isEnabled()) {
                TestOutputAssociation outputAssociation = junitXml.isOutputPerTestCase()
                        ? TestOutputAssociation.WITH_TESTCASE
                        : TestOutputAssociation.WITH_SUITE;
                Binary2JUnitXmlReportGenerator binary2JUnitXmlReportGenerator = new Binary2JUnitXmlReportGenerator(junitXml.getDestination(), testResultsProvider, outputAssociation);
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
            testFramework = null;
        }

        if (testCountLogger.hadFailures()) {
            handleTestFailures();
        }
    }

    /**
     * Returns the {@link org.gradle.api.tasks.testing.TestListener} broadcaster.  This broadcaster will send messages to all listeners that have been registered with the ListenerManager.
     */
    ListenerBroadcast<TestListener> getTestListenerBroadcaster() {
        return testListenerBroadcaster;
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
     * passed to the closure as a parameter. <pre autoTested=''> apply plugin: 'java'
     *
     * test { onOutput { descriptor, event -> if (event.destination == TestOutputEvent.Destination.StdErr) { logger.error("Test: " + descriptor + ", error: " + event.message) } } } </pre>
     *
     * @param closure The closure to call.
     */
    public void onOutput(Closure closure) {
        testOutputListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("onOutput", closure));
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setIncludes(Iterable)
     */
    public Test include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setIncludes(Iterable)
     */
    public Test include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setExcludes(Iterable)
     */
    public Test exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#2F;*Test.class')).
     *
     * @see #setExcludes(Iterable)
     */
    public Test exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test exclude(Closure excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    /**
     * Sets the test name patterns to be included in execution.
     * Classes or method names are supported, wildcard '*' is supported.
     * For more information see the user guide chapter on testing.
     *
     * For more information on supported patterns see {@link TestFilter}
     */
    @Option(option = "tests", description = "Sets test class or method name to be included, '*' is supported.")
    @Incubating
    public Test setTestNameIncludePattern(String testNamePattern) {
        filter.setIncludePatterns(testNamePattern);
        return this;
    }

    /**
     * Returns the root folder for the compiled test sources.
     *
     * @return All test class directories to be used.
     */
    public File getTestClassesDir() {
        return testClassesDir;
    }

    /**
     * Sets the root folder for the compiled test sources.
     *
     * @param testClassesDir The root folder
     */
    public void setTestClassesDir(File testClassesDir) {
        this.testClassesDir = testClassesDir;
    }

    /**
     * Returns the root folder for the test results in internal binary format.
     *
     * @return the test result directory, containing the test results in binary format.
     */
    @OutputDirectory
    @Incubating
    public File getBinResultsDir() {
        return binResultsDir;
    }

    /**
     * Sets the root folder for the test results in internal binary format.
     *
     * @param binResultsDir The root folder
     */
    @Incubating
    public void setBinResultsDir(File binResultsDir) {
        this.binResultsDir = binResultsDir;
    }

    /**
     * Returns the include patterns for test execution.
     *
     * @see #include(String...)
     */
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    /**
     * Sets the include patterns for test execution.
     *
     * @param includes The patterns list
     * @see #include(String...)
     */
    public Test setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    /**
     * Returns the exclude patterns for test execution.
     *
     * @see #exclude(String...)
     */
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    /**
     * Sets the exclude patterns for test execution.
     *
     * @param excludes The patterns list
     * @see #exclude(String...)
     */
    public Test setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public boolean getIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * {@inheritDoc}
     */
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    public TestFramework getTestFramework() {
        return testFramework(null);
    }

    public TestFramework testFramework(Closure testFrameworkConfigure) {
        if (testFramework == null) {
            useJUnit(testFrameworkConfigure);
        }

        return testFramework;
    }

    /**
     * Returns test framework specific options. Make sure to call {@link #useJUnit()} or {@link #useTestNG()} before using this method.
     *
     * @return The test framework options.
     */
    @Nested
    public TestFrameworkOptions getOptions() {
        return options(null);
    }

    /**
     * Configures test framework specific options. Make sure to call {@link #useJUnit()} or {@link #useTestNG()} before using this method.
     *
     * @return The test framework options.
     */
    public TestFrameworkOptions options(Closure testFrameworkConfigure) {
        TestFrameworkOptions options = getTestFramework().getOptions();
        ConfigureUtil.configure(testFrameworkConfigure, options);
        return options;
    }

    TestFramework useTestFramework(TestFramework testFramework) {
        return useTestFramework(testFramework, null);
    }

    private TestFramework useTestFramework(TestFramework testFramework, Closure testFrameworkConfigure) {
        if (testFramework == null) {
            throw new IllegalArgumentException("testFramework is null!");
        }

        this.testFramework = testFramework;

        if (testFrameworkConfigure != null) {
            ConfigureUtil.configure(testFrameworkConfigure, this.testFramework.getOptions());
        }

        return this.testFramework;
    }

    /**
     * Specifies that JUnit should be used to execute the tests. <p> To configure JUnit specific options, see {@link #useJUnit(groovy.lang.Closure)}.
     */
    public void useJUnit() {
        useJUnit(null);
    }

    /**
     * Specifies that JUnit should be used to execute the tests, configuring JUnit specific options. <p> The supplied closure configures an instance of {@link
     * org.gradle.api.tasks.testing.junit.JUnitOptions}, which can be used to configure how JUnit runs.
     *
     * @param testFrameworkConfigure A closure used to configure the JUnit options.
     */
    public void useJUnit(Closure testFrameworkConfigure) {
        useTestFramework(new JUnitTestFramework(this, filter), testFrameworkConfigure);
    }

    /**
     * Specifies that TestNG should be used to execute the tests. <p> To configure TestNG specific options, see {@link #useTestNG(Closure)}.
     */
    public void useTestNG() {
        useTestNG(null);
    }

    /**
     * Specifies that TestNG should be used to execute the tests, configuring TestNG specific options. <p> The supplied closure configures an instance of {@link
     * org.gradle.api.tasks.testing.testng.TestNGOptions}, which can be used to configure how TestNG runs.
     *
     * @param testFrameworkConfigure A closure used to configure the TestNG options.
     */
    public void useTestNG(Closure testFrameworkConfigure) {
        useTestFramework(new TestNGTestFramework(this, this.filter, getInstantiator()), testFrameworkConfigure);
    }

    /**
     * Returns the classpath to use to execute the tests.
     */
    @InputFiles
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
    }

    /**
     * Returns the directories containing the test source.
     */
    @InputFiles
    public List<File> getTestSrcDirs() {
        return testSrcDirs;
    }

    public void setTestSrcDirs(List<File> testSrcDir) {
        this.testSrcDirs = testSrcDir;
    }

    /**
     * Specifies whether test classes should be detected. When {@code true} the classes which match the include and exclude patterns are scanned for test classes, and any found are executed. When
     * {@code false} the classes which match the include and exclude patterns are executed.
     */
    @Input
    public boolean isScanForTestClasses() {
        return scanForTestClasses;
    }

    public void setScanForTestClasses(boolean scanForTestClasses) {
        this.scanForTestClasses = scanForTestClasses;
    }

    /**
     * Returns the maximum number of test classes to execute in a forked test process. The forked test process will be restarted when this limit is reached. The default value is 0 (no maximum).
     *
     * @return The maximum number of test classes. Returns 0 when there is no maximum.
     */
    public long getForkEvery() {
        return forkEvery;
    }

    /**
     * Sets the maximum number of test classes to execute in a forked test process. Use null or 0 to use no maximum.
     *
     * @param forkEvery The maximum number of test classes. Use null or 0 to specify no maximum.
     */
    public void setForkEvery(Long forkEvery) {
        if (forkEvery != null && forkEvery < 0) {
            throw new IllegalArgumentException("Cannot set forkEvery to a value less than 0.");
        }
        this.forkEvery = forkEvery == null ? 0 : forkEvery;
    }

    /**
     * Returns the maximum number of forked test processes to execute in parallel. The default value is 1 (no parallel test execution).
     *
     * @return The maximum number of forked test processes.
     */
    public int getMaxParallelForks() {
        return getDebug() ? 1 : maxParallelForks;
    }

    /**
     * Sets the maximum number of forked test processes to execute in parallel. Set to 1 to disable parallel test execution.
     *
     * @param maxParallelForks The maximum number of forked test processes.
     */
    public void setMaxParallelForks(int maxParallelForks) {
        if (maxParallelForks < 1) {
            throw new IllegalArgumentException("Cannot set maxParallelForks to a value less than 1.");
        }
        this.maxParallelForks = maxParallelForks;
    }

    /**
     * Returns the classes files to scan for test classes.
     *
     * @return The candidate class files.
     */
    @InputFiles
    @Input
    public FileTree getCandidateClassFiles() {
        return getProject().fileTree(getTestClassesDir()).matching(patternSet);
    }

    /**
     * Allows to set options related to which test events are logged to the console, and on which detail level. For example, to show more information about exceptions use:
     *
     * <pre autoTested=''> apply plugin: 'java'
     *
     * test.testLogging { exceptionFormat "full" } </pre>
     *
     * For further information see {@link TestLoggingContainer}.
     *
     * @return this
     */
    public TestLoggingContainer getTestLogging() {
        return testLogging;
    }

    /**
     * Allows configuring the logging of the test execution, for example log eagerly the standard output, etc. <pre autoTested=''> apply plugin: 'java'
     *
     * //makes the standard streams (err and out) visible at console when running tests test.testLogging { showStandardStreams = true } </pre>
     *
     * @param closure configure closure
     */
    public void testLogging(Closure closure) {
        ConfigureUtil.configure(closure, testLogging);
    }

    /**
     * The reports that this task potentially produces.
     *
     * @return The reports that this task potentially produces
     */
    public TestTaskReports getReports() {
        return reports;
    }

    /**
     * Configures the reports that this task potentially produces.
     *
     * @param closure The configuration
     * @return The reports that this task potentially produces
     */
    public TestTaskReports reports(Closure closure) {
        reports.configure(closure);
        return reports;
    }

    /**
     * Allows filtering tests for execution.
     *
     * @return filter object
     * @since 1.10
     */
    @Incubating
    @Nested
    public TestFilter getFilter() {
        return filter;
    }

    /**
     * Executes the action against the {@link #getFilter()}.
     *
     * @param action configuration of the test filter
     * @since 1.10
     */
    @Incubating
    public void filter(Action<TestFilter> action) {
        action.execute(filter);
    }

    // only way I know of to determine current log level
    private LogLevel getCurrentLogLevel() {
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
}
