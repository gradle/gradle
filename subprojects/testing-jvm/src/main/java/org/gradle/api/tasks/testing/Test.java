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

import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
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
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.testing.junit.JUnitOptions;
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.util.ConfigureUtil.configureUsing;

/**
 * Executes JUnit (3.8.x, 4.x or 5.x) or TestNG tests. Test are always run in (one or more) separate JVMs.
 *
 * <p>
 * The sample below shows various configuration options.
 *
 * <pre class='autoTested'>
 * apply plugin: 'java' // adds 'test' task
 *
 * test {
 *   // enable TestNG support (default is JUnit)
 *   useTestNG()
 *   // enable JUnit Platform (a.k.a. JUnit 5) support
 *   useJUnitPlatform()
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
 *   // Fail the 'test' task on the first test failure
 *   failFast = true
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
@NonNullApi
@CacheableTask
public class Test extends AbstractTestTask implements JavaForkOptions, PatternFilterable {

    private final JavaForkOptions forkOptions;

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
        forkOptions = getForkOptionsFactory().newJavaForkOptions();
        forkOptions.setEnableAssertions(true);
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
    protected JavaForkOptionsFactory getForkOptionsFactory() {
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
    public List<CommandLineArgumentProvider> getJvmArgumentProviders() {
        return forkOptions.getJvmArgumentProviders();
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
    @Option(option = "debug-jvm", description = "Enable debugging for the test process. The process is started suspended and listening on port 5005.")
    public void setDebug(boolean enabled) {
        forkOptions.setDebug(enabled);
    }

    /**
     * Enables fail fast behavior causing the task to fail on the first failed test.
     */
    @Option(option = "fail-fast", description = "Stops test execution after the first failed test.")
    @Override
    public void setFailFast(boolean failFast) {
        super.setFailFast(failFast);
    }

    /**
     * Indicates if this task will fail on the first failed test
     *
     * @return whether this task will fail on the first failed test
     */
    @Override
    public boolean getFailFast() {
        return super.getFailFast();
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
        JavaForkOptions javaForkOptions = getForkOptionsFactory().newJavaForkOptions();
        copyTo(javaForkOptions);
        return new JvmTestExecutionSpec(getTestFramework(), getClasspath(), getCandidateClassFiles(), isScanForTestClasses(), getTestClassesDirs(), getPath(), getIdentityPath(), getForkEvery(), javaForkOptions, getMaxParallelForks(), getPreviousFailedTestClasses());
    }

    private Set<String> getPreviousFailedTestClasses() {
        TestResultSerializer serializer = new TestResultSerializer(getBinResultsDir());
        if (serializer.isHasResults()) {
            final Set<String> previousFailedTestClasses = new HashSet<String>();
            serializer.read(new Action<TestClassResult>() {
                @Override
                public void execute(TestClassResult testClassResult) {
                    if (testClassResult.getFailuresCount() > 0) {
                        previousFailedTestClasses.add(testClassResult.getClassName());
                    }
                }
            });
            return previousFailedTestClasses;
        } else {
            return Collections.emptySet();
        }
    }

    @Override
    @TaskAction
    public void executeTests() {
        JavaVersion javaVersion = getJavaVersion();
        if (!javaVersion.isJava6Compatible()) {
            throw new UnsupportedJavaRuntimeException("Support for test execution using Java 5 or earlier was removed in Gradle 3.0.");
        }

        if (getDebug()) {
            getLogger().info("Running tests for remote debugging.");
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
                getServices().get(Clock.class),
                getServices().get(DocumentationRegistry.class),
                (DefaultTestFilter) getFilter());
        } else {
            return testExecuter;
        }
    }

    @Override
    protected List<String> getNoMatchingTestErrorReasons() {
        List<String> reasons = Lists.newArrayList();
        if (!getIncludes().isEmpty()) {
            reasons.add(getIncludes() + "(include rules)");
        }
        if (!getExcludes().isEmpty()) {
            reasons.add(getExcludes() + "(exclude rules)");
        }
        reasons.addAll(super.getNoMatchingTestErrorReasons());
        return reasons;
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
     * {@inheritDoc}
     */
    @Override
    public Test setTestNameIncludePatterns(List<String> testNamePattern) {
        super.setTestNameIncludePatterns(testNamePattern);
        return this;
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

    public TestFramework testFramework(@Nullable Closure testFrameworkConfigure) {
        if (testFramework == null) {
            useJUnit(testFrameworkConfigure);
        }

        return testFramework;
    }

    /**
     * Returns test framework specific options. Make sure to call {@link #useJUnit()}, {@link #useJUnitPlatform()} or {@link #useTestNG()} before using this method.
     *
     * @return The test framework options.
     */
    @Nested
    public TestFrameworkOptions getOptions() {
        return getTestFramework().getOptions();
    }

    /**
     * Configures test framework specific options. Make sure to call {@link #useJUnit()}, {@link #useJUnitPlatform()} or {@link #useTestNG()} before using this method.
     *
     * @return The test framework options.
     */
    public TestFrameworkOptions options(Closure testFrameworkConfigure) {
        return ConfigureUtil.configure(testFrameworkConfigure, getOptions());
    }

    /**
     * Configures test framework specific options. Make sure to call {@link #useJUnit()}, {@link #useJUnitPlatform()} or {@link #useTestNG()} before using this method.
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

    private <T extends TestFrameworkOptions> TestFramework useTestFramework(TestFramework testFramework, @Nullable Action<? super T> testFrameworkConfigure) {
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
    public void useJUnit(@Nullable Closure testFrameworkConfigure) {
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
        useTestFramework(new JUnitTestFramework(this, (DefaultTestFilter) getFilter()), testFrameworkConfigure);
    }

    /**
     * Specifies that JUnit Platform (a.k.a. JUnit 5) should be used to execute the tests. <p> To configure JUnit platform specific options, see {@link #useJUnitPlatform(Action)}.
     *
     * @since 4.6
     */
    @Incubating
    public void useJUnitPlatform() {
        useJUnitPlatform(Actions.<JUnitPlatformOptions>doNothing());
    }

    /**
     * Specifies that JUnit Platform (a.k.a. JUnit 5) should be used to execute the tests, configuring JUnit platform specific options. <p> The supplied action configures an instance of {@link
     * org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions}, which can be used to configure how JUnit platform runs.
     *
     * @param testFrameworkConfigure An action used to configure the JUnit platform options.
     * @since 4.6
     */
    @Incubating
    public void useJUnitPlatform(Action<? super JUnitPlatformOptions> testFrameworkConfigure) {
        useTestFramework(new JUnitPlatformTestFramework((DefaultTestFilter) getFilter()), testFrameworkConfigure);
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
    public void useTestNG(Action<? super TestNGOptions> testFrameworkConfigure) {
        useTestFramework(new TestNGTestFramework(this, (DefaultTestFilter) getFilter(), getInstantiator(), getClassLoaderCache()), testFrameworkConfigure);
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
     * Returns the maximum number of test classes to execute in a forked test process. The forked test process will be restarted when this limit is reached.
     *
     * <p>
     * By default, Gradle automatically uses a separate JVM when executing tests.
     * <ul>
     *  <li>A value of <code>0</code> (no limit) means to reuse the test process for all test classes. This is the default.</li>
     *  <li>A value of <code>1</code> means that a new test process is started for <b>every</b> test class. <b>This is very expensive.</b></li>
     *  <li>A value of <code>N</code> means that a new test process is started after <code>N</code> test classes.</li>
     * </ul>
     * This property can have a large impact on performance due to the cost of stopping and starting each test process. It is unusual for this property to be changed from the default.
     *
     * @return The maximum number of test classes to execute in a test process. Returns 0 when there is no maximum.
     */
    @Internal
    public long getForkEvery() {
        return forkEvery;
    }

    /**
     * Sets the maximum number of test classes to execute in a forked test process.
     * <p>
     * By default, Gradle automatically uses a separate JVM when executing tests, so changing this property is usually not necessary.
     * </p>
     *
     * @param forkEvery The maximum number of test classes. Use null or 0 to specify no maximum.
     */
    public void setForkEvery(@Nullable Long forkEvery) {
        if (forkEvery != null && forkEvery < 0) {
            throw new IllegalArgumentException("Cannot set forkEvery to a value less than 0.");
        }
        this.forkEvery = forkEvery == null ? 0 : forkEvery;
    }

    /**
     * Returns the maximum number of test processes to start in parallel.
     *
     * <p>
     * By default, Gradle executes a single test class at a time.
     * <ul>
     *  <li>A value of <code>1</code> means to only execute a single test class in a single test process at a time. This is the default.</li>
     *  <li>A value of <code>N</code> means that up to <code>N</code> test processes will be started to execute test classes. <b>This can improve test execution time by running multiple test classes in parallel.</b></li>
     * </ul>
     *
     * This property cannot exceed the value of {@literal max-workers} for the current build. Gradle will also limit the number of started test processes across all {@link Test} tasks.
     *
     * @return The maximum number of forked test processes.
     */
    @Internal
    public int getMaxParallelForks() {
        return getDebug() ? 1 : maxParallelForks;
    }

    /**
     * Sets the maximum number of test processes to start in parallel.
     * <p>
     * By default, Gradle executes a single test class at a time but allows multiple {@link Test} tasks to run in parallel.
     * </p>
     * @param maxParallelForks The maximum number of forked test processes. Use 1 to disable parallel test execution for this task.
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
    @SkipWhenEmpty
    public FileTree getCandidateClassFiles() {
        return getTestClassesDirs().getAsFileTree().matching(patternSet);
    }

    /**
     * Executes the action against the {@link #getFilter()}.
     *
     * @param action configuration of the test filter
     * @since 1.10
     */
    public void filter(Action<TestFilter> action) {
        action.execute(getFilter());
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
