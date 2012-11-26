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
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter;
import org.gradle.api.internal.tasks.testing.detection.TestExecuter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junit.result.NewJUnitXmlReportGenerator;
import org.gradle.api.internal.tasks.testing.junit.result.TestReportDataCollector;
import org.gradle.api.internal.tasks.testing.logging.*;
import org.gradle.api.internal.tasks.testing.results.TestListenerAdapter;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.testing.logging.TestLogging;
import org.gradle.api.tasks.testing.logging.TestLoggingContainer;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes tests. Supports JUnit (3.8.x or 4.x) or TestNG tests.
 * <p>
 * An example with a blend of various settings
 * <pre autoTested=''>
 * apply plugin: 'java' // adds 'test' task
 *
 * test {
 *   //configuring a system property for tests
 *   systemProperty 'some.prop', 'value'
 *
 *   //tuning the included/excluded tests
 *   include 'org/foo/**'
 *   exclude 'org/boo/**'
 *
 *   //makes the standard streams (err and out) visible at console when running tests
 *   testLogging.showStandardStreams = true
 *
 *   //tweaking memory settings for the forked vm that runs tests
 *   jvmArgs '-Xms128m', '-Xmx512m', '-XX:MaxPermSize=128m'
 *
 *   //listening to test execution events
 *   beforeTest { descriptor ->
 *      logger.lifecycle("Running test: " + descriptor)
 *   }
 *   onOutput { descriptor, event ->
 *      logger.lifecycle("Test: " + descriptor + " produced standard out/err: " + event.message )
 *   }
 * }
 * </pre>
 *
 * @author Hans Dockter
 */
public class Test extends ConventionTask implements JavaForkOptions, PatternFilterable, VerificationTask {
    private final ListenerBroadcast<TestListener> testListenerBroadcaster;
    private final ListenerBroadcast<TestOutputListener> testOutputListenerBroadcaster;
    private final StyledTextOutputFactory textOutputFactory;
    private final TestLoggingContainer testLogging;
    private final DefaultJavaForkOptions options;

    private TestExecuter testExecuter;
    private List<File> testSrcDirs = new ArrayList<File>();
    private File testClassesDir;
    private File testResultsDir;
    private File testReportDir;
    private PatternFilterable patternSet = new PatternSet();
    private boolean ignoreFailures;
    private FileCollection classpath;
    private TestFramework testFramework;
    private boolean testReport = true;
    private boolean scanForTestClasses = true;
    private long forkEvery;
    private int maxParallelForks = 1;

    public Test() {
        testListenerBroadcaster = getServices().get(ListenerManager.class).createAnonymousBroadcaster(TestListener.class);
        testOutputListenerBroadcaster = getServices().get(ListenerManager.class).createAnonymousBroadcaster(TestOutputListener.class);
        textOutputFactory = getServices().get(StyledTextOutputFactory.class);
        options = new DefaultJavaForkOptions(getServices().get(FileResolver.class));
        options.setEnableAssertions(true);
        testExecuter = new DefaultTestExecuter(getServices().getFactory(WorkerProcessBuilder.class), getServices().get(ActorFactory.class));

        Instantiator instantiator = getServices().get(Instantiator.class);
        testLogging = instantiator.newInstance(DefaultTestLoggingContainer.class, instantiator);
    }

    void setTestExecuter(TestExecuter testExecuter) {
        this.testExecuter = testExecuter;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public File getWorkingDir() {
        return options.getWorkingDir();
    }

    /**
     * {@inheritDoc}
     */
    public void setWorkingDir(Object dir) {
        options.setWorkingDir(dir);
    }

    /**
     * {@inheritDoc}
     */
    public Test workingDir(Object dir) {
        options.workingDir(dir);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public String getExecutable() {
        return options.getExecutable();
    }

    /**
     * {@inheritDoc}
     */
    public Test executable(Object executable) {
        options.executable(executable);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void setExecutable(Object executable) {
        options.setExecutable(executable);
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public Map<String, Object> getSystemProperties() {
        return options.getSystemProperties();
    }

    /**
     * {@inheritDoc}
     */
    public void setSystemProperties(Map<String, ?> properties) {
        options.setSystemProperties(properties);
    }

    /**
     * {@inheritDoc}
     */
    public Test systemProperties(Map<String, ?> properties) {
        options.systemProperties(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test systemProperty(String name, Object value) {
        options.systemProperty(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public FileCollection getBootstrapClasspath() {
        return options.getBootstrapClasspath();
    }

    /**
     * {@inheritDoc}
     */
    public void setBootstrapClasspath(FileCollection classpath) {
        options.setBootstrapClasspath(classpath);
    }

    /**
     * {@inheritDoc}
     */
    public Test bootstrapClasspath(Object... classpath) {
        options.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public String getMinHeapSize() {
        return options.getMinHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public String getDefaultCharacterEncoding() {
        return options.getDefaultCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        options.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    /**
     * {@inheritDoc}
     */
    public void setMinHeapSize(String heapSize) {
        options.setMinHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    public String getMaxHeapSize() {
        return options.getMaxHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    public void setMaxHeapSize(String heapSize) {
        options.setMaxHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public List<String> getJvmArgs() {
        return options.getJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setJvmArgs(Iterable<?> arguments) {
        options.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public Test jvmArgs(Iterable<?> arguments) {
        options.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test jvmArgs(Object... arguments) {
        options.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Input
    public boolean getEnableAssertions() {
        return options.getEnableAssertions();
    }

    /**
     * {@inheritDoc}
     */
    public void setEnableAssertions(boolean enabled) {
        options.setEnableAssertions(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public boolean getDebug() {
        return options.getDebug();
    }

    /**
     * {@inheritDoc}
     */
    public void setDebug(boolean enabled) {
        options.setDebug(enabled);
    }

    /**
     * {@inheritDoc}
     */
    public List<String> getAllJvmArgs() {
        return options.getAllJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    public void setAllJvmArgs(Iterable<?> arguments) {
        options.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    public Map<String, Object> getEnvironment() {
        return options.getEnvironment();
    }

    /**
     * {@inheritDoc}
     */
    public Test environment(Map<String, ?> environmentVariables) {
        options.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test environment(String name, Object value) {
        options.environment(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void setEnvironment(Map<String, ?> environmentVariables) {
        options.setEnvironment(environmentVariables);
    }

    /**
     * {@inheritDoc}
     */
    public Test copyTo(ProcessForkOptions target) {
        options.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public Test copyTo(JavaForkOptions target) {
        options.copyTo(target);
        return this;
    }

    @TaskAction
    public void executeTests() {
        LogLevel currentLevel = getCurrentLogLevel();
        TestLogging levelLogging = testLogging.get(currentLevel);
        TestExceptionFormatter exceptionFormatter = getExceptionFormatter(levelLogging);
        TestEventLogger eventLogger = new TestEventLogger(textOutputFactory, currentLevel, levelLogging, exceptionFormatter);
        addTestListener(eventLogger);
        addTestOutputListener(eventLogger);

        File binaryResultsDir = new File(getTemporaryDir(), "binary-test-results");
        TestReportDataCollector testReportDataCollector = null;
        if (isTestReport() && testFramework instanceof TestNGTestFramework) {
            //TODO SF this is obviously work in progress, add coverage for binary results interference

            getProject().delete(binaryResultsDir);
            getProject().mkdir(binaryResultsDir);

            testReportDataCollector = new TestReportDataCollector(binaryResultsDir);
            addTestListener(testReportDataCollector);
            addTestOutputListener(testReportDataCollector);
        }

        ProgressLoggerFactory progressLoggerFactory = getServices().get(ProgressLoggerFactory.class);
        TestCountLogger testCountLogger = new TestCountLogger(progressLoggerFactory);
        addTestListener(testCountLogger);

        TestResultProcessor resultProcessor = new TestListenerAdapter(
                getTestListenerBroadcaster().getSource(), testOutputListenerBroadcaster.getSource());

        testExecuter.execute(this, resultProcessor);

        if (testReportDataCollector != null) {
            new NewJUnitXmlReportGenerator(getTestResultsDir(), testReportDataCollector).generate();
            getProject().delete(binaryResultsDir);
        }

        testFramework.report();
        testFramework = null;

        if (testCountLogger.hadFailures()) {
            handleTestFailures();
        }
    }

    /**
     * Returns the {@link org.gradle.api.tasks.testing.TestListener} broadcaster.  This broadcaster will send messages
     * to all listeners that have been registered with the ListenerManager.
     */
    ListenerBroadcast<TestListener> getTestListenerBroadcaster() {
        return testListenerBroadcaster;
    }

    /**
     * Registers a test listener with this task. Consider also the following handy methods for quicker hooking into test execution:
     * {@link #beforeTest(groovy.lang.Closure)}, {@link #afterTest(groovy.lang.Closure)},
     * {@link #beforeSuite(groovy.lang.Closure)}, {@link #afterSuite(groovy.lang.Closure)}
     * <p>
     * This listener will NOT be notified of tests executed by other tasks.
     * To get that behavior, use {@link org.gradle.api.invocation.Gradle#addListener(Object)}.
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
     * Unregisters a test listener with this task.  This method will only remove listeners that were added by calling
     * {@link #addTestListener(org.gradle.api.tasks.testing.TestListener)} on this task.  If the listener was registered
     * with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything.
     * Instead, use {@link org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestListener(TestListener listener) {
        testListenerBroadcaster.remove(listener);
    }

    /**
     * Unregisters a test output listener with this task.  This method will only remove listeners that were added by calling
     * {@link #addTestOutputListener(org.gradle.api.tasks.testing.TestOutputListener)} on this task.  If the listener was registered
     * with Gradle using {@link org.gradle.api.invocation.Gradle#addListener(Object)} this method will not do anything.
     * Instead, use {@link org.gradle.api.invocation.Gradle#removeListener(Object)}.
     *
     * @param listener The listener to remove.
     */
    public void removeTestOutputListener(TestOutputListener listener) {
        testOutputListenerBroadcaster.remove(listener);
    }

    /**
     * <p>Adds a closure to be notified before a test suite is executed. A {@link org.gradle.api.tasks.testing.TestDescriptor}
     * instance is passed to the closure as a parameter.</p>
     *
     * <p>This method is also called before any test suites are executed. The provided descriptor will have a null
     * parent suite.</p>
     *
     * @param closure The closure to call.
     */
    public void beforeSuite(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("beforeSuite", closure));
    }

    /**
     * <p>Adds a closure to be notified after a test suite has executed. A {@link org.gradle.api.tasks.testing.TestDescriptor}
     * and {@link TestResult} instance are passed to the closure as a parameter.</p>
     *
     * <p>This method is also called after all test suites are executed. The provided descriptor will have a null parent
     * suite.</p>
     *
     * @param closure The closure to call.
     */
    public void afterSuite(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("afterSuite", closure));
    }

    /**
     * Adds a closure to be notified before a test is executed. A {@link org.gradle.api.tasks.testing.TestDescriptor}
     * instance is passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void beforeTest(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("beforeTest", closure));
    }

    /**
     * Adds a closure to be notified after a test has executed. A {@link org.gradle.api.tasks.testing.TestDescriptor}
     * and {@link TestResult} instance are passed to the closure as a parameter.
     *
     * @param closure The closure to call.
     */
    public void afterTest(Closure closure) {
        testListenerBroadcaster.add(new ClosureBackedMethodInvocationDispatch("afterTest", closure));
    }

    /**
     * Adds a closure to be notified when output from the test received.
     * A {@link org.gradle.api.tasks.testing.TestDescriptor}
     * and {@link org.gradle.api.tasks.testing.TestOutputEvent} instance are passed to the closure as a parameter.
     * <pre autoTested=''>
     * apply plugin: 'java'
     *
     * test {
     *   onOutput { descriptor, event ->
     *     if (event.destination == TestOutputEvent.Destination.StdErr) {
     *       logger.error("Test: " + descriptor + ", error: " + event.message)
     *     }
     *   }
     * }
     * </pre>
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
     * Returns the root folder for the test results.
     *
     * @return the test result directory, containing the internal test results, mostly in xml form.
     */
    @OutputDirectory
    public File getTestResultsDir() {
        return testResultsDir;
    }

    /**
     * Sets the root folder for the test results.
     *
     * @param testResultsDir The root folder
     */
    public void setTestResultsDir(File testResultsDir) {
        this.testResultsDir = testResultsDir;
    }

    /**
     * Returns the root folder for the test reports.
     *
     * @return the test report directory, containing the test report mostly in HTML form.
     */
    @OutputDirectory
    public File getTestReportDir() {
        return testReportDir;
    }

    /**
     * Sets the root folder for the test reports.
     *
     * @param testReportDir The root folder
     */
    public void setTestReportDir(File testReportDir) {
        this.testReportDir = testReportDir;
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
        ConfigureUtil.configure(testFrameworkConfigure, testFramework.getOptions());
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
     * Specifies that JUnit should be used to execute the tests.
     */
    public void useJUnit() {
        useJUnit(null);
    }

    /**
     * Specifies that JUnit should be used to execute the tests.
     *
     * @param testFrameworkConfigure A closure used to configure the JUnit options. This closure is passed an instance
     * of type {@link org.gradle.api.tasks.testing.junit.JUnitOptions}.
     */
    public void useJUnit(Closure testFrameworkConfigure) {
        useTestFramework(new JUnitTestFramework(this), testFrameworkConfigure);
    }

    /**
     * Specifies that TestNG should be used to execute the tests.
     */
    public void useTestNG() {
        useTestNG(null);
    }

    /**
     * Specifies that TestNG should be used to execute the tests.
     *
     * @param testFrameworkConfigure A closure used to configure the TestNG options. This closure is passed an instance
     * of type {@link org.gradle.api.tasks.testing.testng.TestNGOptions}.
     */
    public void useTestNG(Closure testFrameworkConfigure) {
        useTestFramework(new TestNGTestFramework(this), testFrameworkConfigure);
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
     * Specifies whether the test report should be generated.
     * <p>
     * Since Gradle 1.3 the TestNG uses this property in the following way:
     * <ul>
     *     <li>report 'on' means that new TestNG reporting is used:
     *     new improved html reports are generated, old reports are not generated,
     *     xml junit results (typically consumed by your CI server) are generated to {@link Test#getTestResultsDir()} dir
     *     (previously those were generated into {@code Test.testReportsDir/junitreports} dir).
     *     </li>
     *     <li>report 'off' means that old TestNG reporting is used: old html reports are generated,
     *     the xml junit results (for CI) are generated to {@code Test.testReportsDir/junitreports} dir.
     *     </li>
     * </ul>
     * Even though the property is processed differently by TestNG tests, the default behavior does not change.
     * The changes above were needed so that we can improve the TestNG reporting in a backwards compatible way.
     * For more information please refer to the release notes for Gradle 1.3.
     */
    @Input
    public boolean isTestReport() {
        return testReport;
    }

    public void setTestReport(boolean testReport) {
        this.testReport = testReport;
    }

    public void enableTestReport() {
        this.testReport = true;
    }

    public void disableTestReport() {
        this.testReport = false;
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
     * Specifies whether test classes should be detected. When {@code true} the classes which match the include and
     * exclude patterns are scanned for test classes, and any found are executed. When {@code false} the classes which
     * match the include and exclude patterns are executed.
     */
    @Input
    public boolean isScanForTestClasses() {
        return scanForTestClasses;
    }

    public void setScanForTestClasses(boolean scanForTestClasses) {
        this.scanForTestClasses = scanForTestClasses;
    }

    /**
     * Returns the maximum number of test classes to execute in a forked test process. The forked test process will be
     * restarted when this limit is reached. The default value is 0 (no maximum).
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
     * Returns the maximum number of forked test processes to execute in parallel. The default value is 1 (no parallel
     * test execution).
     *
     * @return The maximum number of forked test processes.
     */
    public int getMaxParallelForks() {
        return getDebug() ? 1 : maxParallelForks;
    }

    /**
     * Sets the maximum number of forked test processes to execute in parallel. Set to 1 to disable parallel test
     * execution.
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
    @Input // Also marked as input to force tests to run when the set of candidate class files changes 
    public FileTree getCandidateClassFiles() {
        return getProject().fileTree(getTestClassesDir()).matching(patternSet);
    }

    /**
     * Allows to set options related to which test events are logged to the console, and on which detail
     * level. For example, to show more information about exceptions use:
     *
     * <pre autoTested=''>
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
    public TestLoggingContainer getTestLogging() {
        return testLogging;
    }

    /**
     * Allows configuring the logging of the test execution, for example log eagerly the standard output, etc.
     * <pre autoTested=''>
     * apply plugin: 'java'
     *
     * //makes the standard streams (err and out) visible at console when running tests
     * test.testLogging {
     *   showStandardStreams = true
     * }
     * </pre>
     * @param closure configure closure
     */
    public void testLogging(Closure closure) {
        ConfigureUtil.configure(closure, testLogging);
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
        String reportUrl = new ConsoleRenderer().asClickableFileUrl(new File(getTestReportDir(), "index.html"));
        String message = "There were failing tests. See the report at: " + reportUrl;
        if (getIgnoreFailures()) {
            getLogger().warn(message);
        } else {
            throw new GradleException(message);
        }
    }
}
