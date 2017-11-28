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
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.tasks.options.Option;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.NoMatchingTestsReporter;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;
import org.gradle.util.SingleMessageLogger;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.ConfigureUtil.configureUsing;

/**
 * Executes JUnit (3.8.x or 4.x) or TestNG tests. Test are always run in (one or more) separate JVMs.
 * The sample below shows various configuration options.
 *
 * <pre class='autoTested'>
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
 *   beforeTest { descriptor -&gt;
 *      logger.lifecycle("Running test: " + descriptor)
 *   }
 *
 *   // listen to standard out and standard error of the test JVM(s)
 *   onOutput { descriptor, event -&gt;
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
@CacheableTask
public class Test extends AbstractTestTask implements JavaForkOptions, PatternFilterable {

    private final DefaultJavaForkOptions forkOptions;
    private final DefaultTestFilter filter;

    private FileCollection testClassesDirs;
    private PatternFilterable patternSet;
    private FileCollection classpath;
    private TestFramework testFramework;
    private boolean scanForTestClasses = true;
    private long forkEvery;
    private int maxParallelForks = 1;
    private TestExecuter<JvmTestExecutionSpec> testExecuter;

    public Test() {
        patternSet = getFileResolver().getPatternSetFactory().create();
        forkOptions = new DefaultJavaForkOptions(getFileResolver());
        forkOptions.setEnableAssertions(true);
        Instantiator instantiator = getInstantiator();

        filter = instantiator.newInstance(DefaultTestFilter.class);
    }

    @Inject
    protected ActorFactory getActorFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ClassLoaderCache getClassLoaderCache() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected WorkerProcessFactory getProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected FileResolver getFileResolver() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ModuleRegistry getModuleRegistry() {
        throw new UnsupportedOperationException();
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
    public Test workingDir(Object dir) {
        forkOptions.workingDir(dir);
        return this;
    }

    /**
     * Returns the version of Java used to run the tests based on the executable specified by {@link #getExecutable()}.
     *
     * @since 3.3
     */
    @Input
    public JavaVersion getJavaVersion() {
        return getServices().get(JvmVersionDetector.class).getJavaVersion(getExecutable());
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
    public Test executable(Object executable) {
        forkOptions.executable(executable);
        return this;
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
    public Map<String, Object> getSystemProperties() {
        return forkOptions.getSystemProperties();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemProperties(Map<String, ?> properties) {
        forkOptions.setSystemProperties(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test systemProperties(Map<String, ?> properties) {
        forkOptions.systemProperties(properties);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test systemProperty(String name, Object value) {
        forkOptions.systemProperty(name, value);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FileCollection getBootstrapClasspath() {
        return forkOptions.getBootstrapClasspath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBootstrapClasspath(FileCollection classpath) {
        forkOptions.setBootstrapClasspath(classpath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test bootstrapClasspath(Object... classpath) {
        forkOptions.bootstrapClasspath(classpath);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMinHeapSize() {
        return forkOptions.getMinHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDefaultCharacterEncoding() {
        return forkOptions.getDefaultCharacterEncoding();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDefaultCharacterEncoding(String defaultCharacterEncoding) {
        forkOptions.setDefaultCharacterEncoding(defaultCharacterEncoding);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMinHeapSize(String heapSize) {
        forkOptions.setMinHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getMaxHeapSize() {
        return forkOptions.getMaxHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMaxHeapSize(String heapSize) {
        forkOptions.setMaxHeapSize(heapSize);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getJvmArgs() {
        return forkOptions.getJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setJvmArgs(List<String> arguments) {
        forkOptions.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setJvmArgs(Iterable<?> arguments) {
        forkOptions.setJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test jvmArgs(Iterable<?> arguments) {
        forkOptions.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test jvmArgs(Object... arguments) {
        forkOptions.jvmArgs(arguments);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getEnableAssertions() {
        return forkOptions.getEnableAssertions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEnableAssertions(boolean enabled) {
        forkOptions.setEnableAssertions(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getDebug() {
        return forkOptions.getDebug();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Option(option = "debug-jvm", description = "Enable debugging for the test process. The process is started suspended and listening on port 5005. [INCUBATING]")
    public void setDebug(boolean enabled) {
        forkOptions.setDebug(enabled);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAllJvmArgs() {
        return forkOptions.getAllJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllJvmArgs(List<String> arguments) {
        forkOptions.setAllJvmArgs(arguments);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllJvmArgs(Iterable<?> arguments) {
        forkOptions.setAllJvmArgs(arguments);
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
    public Test environment(Map<String, ?> environmentVariables) {
        forkOptions.environment(environmentVariables);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test environment(String name, Object value) {
        forkOptions.environment(name, value);
        return this;
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
    public Test copyTo(ProcessForkOptions target) {
        forkOptions.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test copyTo(JavaForkOptions target) {
        forkOptions.copyTo(target);
        return this;
    }

    /**
     * {@inheritDoc}
     * @since 4.4
     */
    @Override
    protected JvmTestExecutionSpec createTestExecutionSpec() {
        return new JvmTestExecutionSpec(getTestFramework(), getClasspath(), getCandidateClassFiles(), isScanForTestClasses(), getTestClassesDirs(), getPath(), getIdentityPath(), getForkEvery(), this, getMaxParallelForks());
    }

    @TaskAction
    public void executeTests() {

        JavaVersion javaVersion = getJavaVersion();
        if (!javaVersion.isJava6Compatible()) {
            throw new UnsupportedJavaRuntimeException("Support for test execution using Java 5 or earlier was removed in Gradle 3.0.");
        }

        if (getFilter().isFailOnNoMatchingTests() && (!getFilter().getIncludePatterns().isEmpty() || !filter.getCommandLineIncludePatterns().isEmpty())) {
            addTestListener(new NoMatchingTestsReporter(createNoMatchingTestErrorMessage()));
        }

        try {
            super.executeTests();
        } finally {
            testFramework = null;
        }
    }

    @Override
    protected TestExecuter<JvmTestExecutionSpec> createTestExecuter() {
        if (testExecuter == null) {
            return new DefaultTestExecuter(getProcessBuilderFactory(), getActorFactory(), getModuleRegistry(),
                getServices().get(WorkerLeaseRegistry.class),
                getServices().get(BuildOperationExecutor.class),
                getServices().get(StartParameter.class).getMaxWorkerCount(),
                getServices().get(Clock.class));
        } else {
            return testExecuter;
        }
    }

    private String createNoMatchingTestErrorMessage() {
        String msg = "No tests found for given includes: ";
        if (!getIncludes().isEmpty()) {
            msg += getIncludes() + "(include rules) ";
        }
        if (!getExcludes().isEmpty()) {
            msg += getExcludes() + "(exclude rules) ";
        }
        if (!filter.getIncludePatterns().isEmpty()) {
            msg += filter.getIncludePatterns() + "(filter.includeTestsMatching) ";
        }
        if (!filter.getCommandLineIncludePatterns().isEmpty()) {
            msg += filter.getCommandLineIncludePatterns() + "(--tests filter) ";
        }
        return msg;
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see #setIncludes(Iterable)
     */
    @Override
    public Test include(String... includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * Adds include patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see #setIncludes(Iterable)
     */
    @Override
    public Test include(Iterable<String> includes) {
        patternSet.include(includes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test include(Spec<FileTreeElement> includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test include(Closure includeSpec) {
        patternSet.include(includeSpec);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see #setExcludes(Iterable)
     */
    @Override
    public Test exclude(String... excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * Adds exclude patterns for the files in the test classes directory (e.g. '**&#47;*Test.class')).
     *
     * @see #setExcludes(Iterable)
     */
    @Override
    public Test exclude(Iterable<String> excludes) {
        patternSet.exclude(excludes);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test exclude(Spec<FileTreeElement> excludeSpec) {
        patternSet.exclude(excludeSpec);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
    public Test setTestNameIncludePatterns(List<String> testNamePattern) {
        filter.setCommandLineIncludePatterns(testNamePattern);
        return this;
    }

    /**
     * Returns the root folder for the compiled test sources.
     *
     * @return All test class directories to be used.
     * @deprecated Use {@link #getTestClassesDirs()}.
     */
    @Deprecated
    @Internal
    public File getTestClassesDir() {
        SingleMessageLogger.nagUserOfReplacedMethod("getTestClassesDir()", "getTestClassesDirs()");
        if (testClassesDirs==null || testClassesDirs.isEmpty()) {
            return null;
        }
        return getProject().file(CollectionUtils.first(testClassesDirs));
    }

    /**
     * Sets the root folder for the compiled test sources.
     *
     * @param testClassesDir The root folder
     * @deprecated Use {@link #setTestClassesDirs(FileCollection)}.
     */
    @Deprecated
    public void setTestClassesDir(File testClassesDir) {
        SingleMessageLogger.nagUserOfReplacedMethod("setTestClassesDir(File)", "setTestClassesDirs(FileCollection)");
        setTestClassesDirs(getProject().files(testClassesDir));
    }

    /**
     * Returns the directories for the compiled test sources.
     *
     * @return All test class directories to be used.
     * @since 4.0
     */
    @Internal
    public FileCollection getTestClassesDirs() {
        return testClassesDirs;
    }

    /**
     * Sets the directories to scan for compiled test sources.
     *
     * Typically, this would be configured to use the output of a source set:
     * <pre class='autoTested'>
     * apply plugin: 'java'
     *
     * sourceSets {
     *    integrationTest {
     *       compileClasspath += main.output
     *       runtimeClasspath += main.output
     *    }
     * }
     *
     * task integrationTest(type: Test) {
     *     // Runs tests from src/integrationTest
     *     testClassesDirs = sourceSets.integrationTest.output.classesDirs
     *     classpath = sourceSets.integrationTest.runtimeClasspath
     * }
     * </pre>
     *
     * @param testClassesDirs All test class directories to be used.
     * @since 4.0
     */
    public void setTestClassesDirs(FileCollection testClassesDirs) {
        this.testClassesDirs = testClassesDirs;
    }

    /**
     * Returns the include patterns for test execution.
     *
     * @see #include(String...)
     */
    @Override
    @Internal
    public Set<String> getIncludes() {
        return patternSet.getIncludes();
    }

    /**
     * Sets the include patterns for test execution.
     *
     * @param includes The patterns list
     * @see #include(String...)
     */
    @Override
    public Test setIncludes(Iterable<String> includes) {
        patternSet.setIncludes(includes);
        return this;
    }

    /**
     * Returns the exclude patterns for test execution.
     *
     * @see #exclude(String...)
     */
    @Override
    @Internal
    public Set<String> getExcludes() {
        return patternSet.getExcludes();
    }

    /**
     * Sets the exclude patterns for test execution.
     *
     * @param excludes The patterns list
     * @see #exclude(String...)
     */
    @Override
    public Test setExcludes(Iterable<String> excludes) {
        patternSet.setExcludes(excludes);
        return this;
    }

    @Internal
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
    // TODO:LPTR This doesn't resolve any of the nested options for the concrete subtypes
    public TestFrameworkOptions getOptions() {
        return getTestFramework().getOptions();
    }

    /**
     * Configures test framework specific options. Make sure to call {@link #useJUnit()} or {@link #useTestNG()} before using this method.
     *
     * @return The test framework options.
     */
    public TestFrameworkOptions options(Closure testFrameworkConfigure) {
        return ConfigureUtil.configure(testFrameworkConfigure, getOptions());
    }

    /**
     * Configures test framework specific options. Make sure to call {@link #useJUnit()} or {@link #useTestNG()} before using this method.
     *
     * @return The test framework options.
     * @since 3.5
     */
    public TestFrameworkOptions options(Action<? super TestFrameworkOptions> testFrameworkConfigure) {
        TestFrameworkOptions options = getOptions();
        testFrameworkConfigure.execute(options);
        return options;
    }

    TestFramework useTestFramework(TestFramework testFramework) {
        return useTestFramework(testFramework, null);
    }

    private <T extends TestFrameworkOptions> TestFramework useTestFramework(TestFramework testFramework, Action<? super T> testFrameworkConfigure) {
        if (testFramework == null) {
            throw new IllegalArgumentException("testFramework is null!");
        }

        this.testFramework = testFramework;

        if (testFrameworkConfigure != null) {
            testFrameworkConfigure.execute(Cast.<T>uncheckedCast(this.testFramework.getOptions()));
        }

        return this.testFramework;
    }

    /**
     * Specifies that JUnit should be used to execute the tests. <p> To configure JUnit specific options, see {@link #useJUnit(groovy.lang.Closure)}.
     */
    public void useJUnit() {
        useJUnit(Actions.<JUnitOptions>doNothing());
    }

    /**
     * Specifies that JUnit should be used to execute the tests, configuring JUnit specific options. <p> The supplied closure configures an instance of {@link
     * org.gradle.api.tasks.testing.junit.JUnitOptions}, which can be used to configure how JUnit runs.
     *
     * @param testFrameworkConfigure A closure used to configure the JUnit options.
     */
    public void useJUnit(Closure testFrameworkConfigure) {
        useJUnit(ConfigureUtil.<JUnitOptions>configureUsing(testFrameworkConfigure));
    }

    /**
     * Specifies that JUnit should be used to execute the tests, configuring JUnit specific options. <p> The supplied action configures an instance of {@link
     * org.gradle.api.tasks.testing.junit.JUnitOptions}, which can be used to configure how JUnit runs.
     *
     * @param testFrameworkConfigure An action used to configure the JUnit options.
     * @since 3.5
     */
    public void useJUnit(Action<? super JUnitOptions> testFrameworkConfigure) {
        useTestFramework(new JUnitTestFramework(this, filter), testFrameworkConfigure);
    }

    /**
     * Specifies that TestNG should be used to execute the tests. <p> To configure TestNG specific options, see {@link #useTestNG(Closure)}.
     */
    public void useTestNG() {
        useTestNG(Actions.<TestFrameworkOptions>doNothing());
    }

    /**
     * Specifies that TestNG should be used to execute the tests, configuring TestNG specific options. <p> The supplied closure configures an instance of {@link
     * org.gradle.api.tasks.testing.testng.TestNGOptions}, which can be used to configure how TestNG runs.
     *
     * @param testFrameworkConfigure A closure used to configure the TestNG options.
     */
    public void useTestNG(Closure testFrameworkConfigure) {
        useTestNG(configureUsing(testFrameworkConfigure));
    }

    /**
     * Specifies that TestNG should be used to execute the tests, configuring TestNG specific options. <p> The supplied action configures an instance of {@link
     * org.gradle.api.tasks.testing.testng.TestNGOptions}, which can be used to configure how TestNG runs.
     *
     * @param testFrameworkConfigure An action used to configure the TestNG options.
     * @since 3.5
     */
    public void useTestNG(Action<? super TestFrameworkOptions> testFrameworkConfigure) {
        useTestFramework(new TestNGTestFramework(this, this.filter, getInstantiator(), getClassLoaderCache()), testFrameworkConfigure);
    }

    /**
     * Returns the classpath to use to execute the tests.
     */
    @Classpath
    public FileCollection getClasspath() {
        return classpath;
    }

    public void setClasspath(FileCollection classpath) {
        this.classpath = classpath;
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
    @Internal
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
     * It cannot exceed the value of {@literal max-workers} for the current build.
     *
     * @return The maximum number of forked test processes.
     */
    @Internal
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
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public FileTree getCandidateClassFiles() {
        return getTestClassesDirs().getAsFileTree().matching(patternSet);
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

    /**
     * Sets the testExecuter property.
     *
     * @since 4.2
     */
    void setTestExecuter(TestExecuter<JvmTestExecutionSpec> testExecuter) {
        this.testExecuter = testExecuter;
    }
}
