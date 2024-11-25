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
import groovy.lang.DelegatesTo;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;
import org.gradle.api.NonNullApi;
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.tasks.testing.JvmTestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestExecutableUtils;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.detection.DefaultTestExecuter;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junit.result.PersistentTestResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.internal.tasks.testing.worker.TestWorker;
import org.gradle.api.jvm.ModularitySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
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
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.jvm.DefaultModularitySpec;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.jvm.UnsupportedJavaRuntimeException;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.JavaExecutableUtils;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.JavaDebugOptions;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.ProcessForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.util.internal.ConfigureUtil;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import static org.gradle.util.internal.ConfigureUtil.configureUsing;

/**
 * Executes JUnit (3.8.x, 4.x or 5.x) or TestNG tests. Test are always run in (one or more) separate JVMs.
 *
 * <p>
 * The sample below shows various configuration options.
 *
 * <pre class='autoTested'>
 * plugins {
 *     id("java-library") // adds 'test' task
 * }
 *
 * test {
 *   // discover and execute JUnit4-based tests
 *   useJUnit()
 *
 *   // discover and execute TestNG-based tests
 *   useTestNG()
 *
 *   // discover and execute JUnit Platform-based tests
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
 *   // fail the 'test' task on the first test failure
 *   failFast = true
 *
 *   // skip an actual test execution
 *   dryRun = true
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
public abstract class Test extends AbstractTestTask implements JavaForkOptions, PatternFilterable {

    private final JavaForkOptions forkOptions;
    private final ModularitySpec modularity;
    private final Property<JavaLauncher> javaLauncher;

    private FileCollection testClassesDirs;
    private final PatternFilterable patternSet;
    private FileCollection classpath;
    private final ConfigurableFileCollection stableClasspath;
    private final Property<TestFramework> testFramework;
    private boolean scanForTestClasses = true;
    private long forkEvery;
    private int maxParallelForks = 1;
    private TestExecuter<JvmTestExecutionSpec> testExecuter;

    public Test() {
        ObjectFactory objectFactory = getObjectFactory();
        patternSet = getPatternSetFactory().create();
        classpath = objectFactory.fileCollection();
        // Create a stable instance to represent the classpath, that takes care of conventions and mutations applied to the property
        stableClasspath = objectFactory.fileCollection();
        stableClasspath.from(new Callable<Object>() {
            @Override
            public Object call() {
                return getClasspath();
            }
        });
        forkOptions = getForkOptionsFactory().newDecoratedJavaForkOptions();
        forkOptions.setEnableAssertions(true);
        forkOptions.setExecutable(null);
        modularity = objectFactory.newInstance(DefaultModularitySpec.class);
        javaLauncher = objectFactory.property(JavaLauncher.class).convention(createJavaLauncherConvention());
        javaLauncher.finalizeValueOnRead();
        getDryRun().convention(false);
        testFramework = objectFactory.property(TestFramework.class).convention(new JUnitTestFramework(this, (DefaultTestFilter) getFilter(), true));
    }

    private Provider<JavaLauncher> createJavaLauncherConvention() {
        final ObjectFactory objectFactory = getObjectFactory();
        final JavaToolchainService javaToolchainService = getJavaToolchainService();
        Provider<JavaToolchainSpec> executableOverrideToolchainSpec = getProviderFactory().provider(new Callable<JavaToolchainSpec>() {
            @Override
            public JavaToolchainSpec call() {
                return TestExecutableUtils.getExecutableToolchainSpec(Test.this, objectFactory);
            }
        });

        return executableOverrideToolchainSpec
            .flatMap(new Transformer<Provider<JavaLauncher>, JavaToolchainSpec>() {
                @Override
                public Provider<JavaLauncher> transform(JavaToolchainSpec spec) {
                    return javaToolchainService.launcherFor(spec);
                }
            })
            .orElse(javaToolchainService.launcherFor(new Action<JavaToolchainSpec>() {
                @Override
                public void execute(JavaToolchainSpec javaToolchainSpec) {}
            }));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    @ToBeReplacedByLazyProperty
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
     * Returns the version of Java used to run the tests based on the {@link JavaLauncher} specified by {@link #getJavaLauncher()},
     * or the executable specified by {@link #getExecutable()} if the {@code JavaLauncher} is not present.
     *
     * @since 3.3
     */
    @Input
    @ToBeReplacedByLazyProperty
    public JavaVersion getJavaVersion() {
        return JavaVersion.toVersion(getJavaLauncher().get().getMetadata().getLanguageVersion().asInt());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Internal
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
    public String getMinHeapSize() {
        return forkOptions.getMinHeapSize();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
    public List<String> getJvmArgs() {
        return forkOptions.getJvmArgs();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
     * {@inheritDoc}
     */
    @Override
    public JavaDebugOptions getDebugOptions() {
        return forkOptions.getDebugOptions();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void debugOptions(Action<JavaDebugOptions> action) {
        forkOptions.debugOptions(action);
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
    @ToBeReplacedByLazyProperty
    public boolean getFailFast() {
        return super.getFailFast();
    }

    /**
     * Indicates if this task will skip individual test execution.
     *
     * <p>
     *     For JUnit 4 and 5, this will report tests that would have executed as skipped.
     *     For TestNG, this will report tests that would have executed as passed.
     * </p>
     *
     * <p>
     *     Only versions of TestNG which support native dry-running are supported, i.e. TestNG 6.14 or later.
     * </p>
     *
     * @return property for whether this task will skip individual test execution
     * @since 8.3
     */
    @Incubating
    @Input
    @Option(option = "test-dry-run", description = "Simulate test execution.")
    public abstract Property<Boolean> getDryRun();

    /**
     * {@inheritDoc}
     */
    @Override
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
        copyToolchainAsExecutable(target);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Test copyTo(JavaForkOptions target) {
        forkOptions.copyTo(target);
        copyToolchainAsExecutable(target);
        return this;
    }

    private void copyToolchainAsExecutable(ProcessForkOptions target) {
        String executable = getJavaLauncher().get().getExecutablePath().toString();
        target.setExecutable(executable);
    }

    /**
     * Returns the module path handling of this test task.
     *
     * @since 6.4
     */
    @Nested
    public ModularitySpec getModularity() {
        return modularity;
    }

    /**
     * {@inheritDoc}
     *
     * @since 4.4
     */
    @Override
    protected JvmTestExecutionSpec createTestExecutionSpec() {
        validateExecutableMatchesToolchain();
        JavaForkOptions javaForkOptions = getForkOptionsFactory().newJavaForkOptions();
        copyTo(javaForkOptions);
        javaForkOptions.systemProperty(TestWorker.WORKER_TMPDIR_SYS_PROPERTY, new File(getTemporaryDir(), "work"));
        JavaModuleDetector javaModuleDetector = getJavaModuleDetector();
        boolean testIsModule = javaModuleDetector.isModule(modularity.getInferModulePath().get(), getTestClassesDirs());
        FileCollection classpath = javaModuleDetector.inferClasspath(testIsModule, stableClasspath);
        FileCollection modulePath = javaModuleDetector.inferModulePath(testIsModule, stableClasspath);
        return new JvmTestExecutionSpec(getTestFramework(), classpath, modulePath, getCandidateClassFiles(), isScanForTestClasses(), getTestClassesDirs(), getPath(), getIdentityPath(), getForkEvery(), javaForkOptions, getMaxParallelForks(), getPreviousFailedTestClasses(), testIsModule);
    }

    private void validateExecutableMatchesToolchain() {
        File toolchainExecutable = getJavaLauncher().get().getExecutablePath().getAsFile();
        String customExecutable = getExecutable();
        JavaExecutableUtils.validateExecutable(
                customExecutable, "Toolchain from `executable` property",
                toolchainExecutable, "toolchain from `javaLauncher` property");
    }

    private Set<String> getPreviousFailedTestClasses() {
        TestResultSerializer serializer = new TestResultSerializer(getBinaryResultsDirectory().getAsFile().get());
        PersistentTestResult rootResult = serializer.read(TestResultSerializer.VersionMismatchAction.RETURN_NULL);
        if (rootResult == null) {
            return Collections.emptySet();
        }
        final Set<String> previousFailedTestClasses = new HashSet<>();
        for (PersistentTestResult classResult : rootResult.getChildren()) {
            if (classResult.getResultType() == TestResult.ResultType.FAILURE) {
            previousFailedTestClasses.add(classResult.getName());
                }
        }
        return previousFailedTestClasses;
    }

    @Override
    @TaskAction
    public void executeTests() {
        JavaVersion javaVersion = getJavaVersion();
        if (!javaVersion.isJava6Compatible()) {
            throw new UnsupportedJavaRuntimeException("Support for test execution using Java 5 or earlier was removed in Gradle 3.0.");
        }
        if (!javaVersion.isJava8Compatible()) {
            if (testFramework.get() instanceof JUnitPlatformTestFramework) {
                throw new UnsupportedJavaRuntimeException("Running tests with JUnit platform requires a Java 8+ toolchain.");
            } else {
                DeprecationLogger.deprecate("Running tests on Java versions earlier than 8")
                    .willBecomeAnErrorInGradle9()
                    .withUpgradeGuideSection(8, "minimum_test_jvm_version")
                    .nagUser();
            }
        }

        if (getDebug()) {
            getLogger().info("Running tests for remote debugging.");
        }

        try {
            super.executeTests();
        } finally {
            CompositeStoppable.stoppable(getTestFramework()).stop();
        }
    }

    @Override
    protected TestExecuter<JvmTestExecutionSpec> createTestExecuter() {
        if (testExecuter == null) {
            return new DefaultTestExecuter(getProcessBuilderFactory(), getActorFactory(), getModuleRegistry(),
                getServices().get(WorkerLeaseService.class),
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
        List<String> reasons = new ArrayList<>();
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
    @ToBeReplacedByLazyProperty
    public FileCollection getTestClassesDirs() {
        return testClassesDirs;
    }

    /**
     * Sets the directories to scan for compiled test sources.
     *
     * Typically, this would be configured to use the output of a source set:
     * <pre class='autoTested'>
     * plugins {
     *     id 'java'
     * }
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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

    /**
     * Returns the configured {@link TestFramework}.
     *
     * @since 7.3
     */
    @Nested
    public Property<TestFramework> getTestFrameworkProperty() {
        return testFramework;
    }

    @Internal
    @ToBeReplacedByLazyProperty(comment = "This will be removed")
    public TestFramework getTestFramework() {
        // TODO: Deprecate and remove this method
        return testFramework.get();
    }

    public TestFramework testFramework(@Nullable Closure testFrameworkConfigure) {
        // TODO: Deprecate and remove this method
        options(testFrameworkConfigure);
        return getTestFramework();
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
     * Configures test framework specific options.
     * <p>
     * When a {@code Test} task is created outside of Test Suites, you should call {@link #useJUnit()}, {@link #useJUnitPlatform()} or {@link #useTestNG()} before using this method.
     * If no test framework has been set, the task will assume JUnit4.
     *
     * @return The test framework options.
     */
    public TestFrameworkOptions options(@DelegatesTo(TestFrameworkOptions.class) Closure testFrameworkConfigure) {
        return ConfigureUtil.configure(testFrameworkConfigure, getOptions());
    }

    /**
     * Configures test framework specific options.
     * <p>
     * When a {@code Test} task is created outside of Test Suites, you should call {@link #useJUnit()}, {@link #useJUnitPlatform()} or {@link #useTestNG()} before using this method.
     * If no test framework has been set, the task will assume JUnit4.
     *
     * @return The test framework options.
     * @since 3.5
     */
    public TestFrameworkOptions options(Action<? super TestFrameworkOptions> testFrameworkConfigure) {
        return Actions.with(getOptions(), testFrameworkConfigure);
    }

    /**
     * Specifies that JUnit4 should be used to discover and execute the tests.
     *
     * @see #useJUnit(org.gradle.api.Action) Configure JUnit4 specific options.
     */
    public void useJUnit() {
        useTestFramework(new JUnitTestFramework(this, (DefaultTestFilter) getFilter(), true));
    }

    /**
     * Specifies that JUnit4 should be used to discover and execute the tests with additional configuration.
     * <p>
     * The supplied action configures an instance of {@link org.gradle.api.tasks.testing.junit.JUnitOptions JUnit4 specific options}.
     *
     * @param testFrameworkConfigure A closure used to configure JUnit4 options.
     */
    public void useJUnit(@Nullable @DelegatesTo(JUnitOptions.class) Closure testFrameworkConfigure) {
        useJUnit(ConfigureUtil.<JUnitOptions>configureUsing(testFrameworkConfigure));
    }

    /**
     * Specifies that JUnit4 should be used to discover and execute the tests with additional configuration.
     * <p>
     * The supplied action configures an instance of {@link org.gradle.api.tasks.testing.junit.JUnitOptions JUnit4 specific options}.
     *
     * @param testFrameworkConfigure An action used to configure JUnit4 options.
     * @since 3.5
     */
    public void useJUnit(Action<? super JUnitOptions> testFrameworkConfigure) {
        useJUnit();
        applyOptions(JUnitOptions.class, testFrameworkConfigure);
    }

    /**
     * Specifies that JUnit Platform should be used to discover and execute the tests.
     * <p>
     * Use this option if your tests use JUnit Jupiter/JUnit5.
     * <p>
     * JUnit Platform supports multiple test engines, which allows other testing frameworks to be built on top of it.
     * You may need to use this option even if you are not using JUnit directly.
     *
     * @see #useJUnitPlatform(org.gradle.api.Action) Configure JUnit Platform specific options.
     * @since 4.6
     */
    public void useJUnitPlatform() {
        useTestFramework(new JUnitPlatformTestFramework((DefaultTestFilter) getFilter(), true, getDryRun()));
    }

    /**
     * Specifies that JUnit Platform should be used to discover and execute the tests with additional configuration.
     * <p>
     * Use this option if your tests use JUnit Jupiter/JUnit5.
     * <p>
     * JUnit Platform supports multiple test engines, which allows other testing frameworks to be built on top of it.
     * You may need to use this option even if you are not using JUnit directly.
     * <p>
     * The supplied action configures an instance of {@link org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions JUnit Platform specific options}.
     *
     * @param testFrameworkConfigure A closure used to configure JUnit platform options.
     * @since 4.6
     */
    public void useJUnitPlatform(Action<? super JUnitPlatformOptions> testFrameworkConfigure) {
        useJUnitPlatform();
        applyOptions(JUnitPlatformOptions.class, testFrameworkConfigure);
    }

    /**
     * Specifies that TestNG should be used to discover and execute the tests.
     *
     * @see #useTestNG(org.gradle.api.Action) Configure TestNG specific options.
     */
    public void useTestNG() {
        useTestFramework(new TestNGTestFramework(this, (DefaultTestFilter) getFilter(), getObjectFactory()));

    }

    /**
     * Specifies that TestNG should be used to discover and execute the tests with additional configuration.
     * <p>
     * The supplied action configures an instance of {@link org.gradle.api.tasks.testing.testng.TestNGOptions TestNG specific options}.
     *
     * @param testFrameworkConfigure A closure used to configure TestNG options.
     */
    public void useTestNG(@DelegatesTo(TestNGOptions.class) Closure testFrameworkConfigure) {
        useTestNG(configureUsing(testFrameworkConfigure));
    }

    /**
     * Specifies that TestNG should be used to discover and execute the tests with additional configuration.
     * <p>
     * The supplied action configures an instance of {@link org.gradle.api.tasks.testing.testng.TestNGOptions TestNG specific options}.
     *
     * @param testFrameworkConfigure An action used to configure TestNG options.
     * @since 3.5
     */
    public void useTestNG(Action<? super TestNGOptions> testFrameworkConfigure) {
        useTestNG();
        applyOptions(TestNGOptions.class, testFrameworkConfigure);
    }

    /**
     * Set the framework, only if it is being changed to a new value.
     *
     * If we are setting a framework to its existing value, no-op so as not to overwrite existing options here.
     * We need to allow this especially for the default test task, so that existing builds that configure options and
     * then call useJunit() don't clear out their options.
     */
    void useTestFramework(TestFramework testFramework) {
        Class<?> currentFramework = this.testFramework.get().getClass();
        Class<?> newFramework = testFramework.getClass();
        if (currentFramework == newFramework) {
            return;
        }

        this.testFramework.set(testFramework);
    }

    private <T extends TestFrameworkOptions> void applyOptions(Class<T> optionsClass, Action<? super T> configuration) {
        Actions.with(Cast.cast(optionsClass, getOptions()), configuration);
    }

    /**
     * Returns the classpath to use to execute the tests.
     *
     * @since 6.6
     */
    @Classpath
    protected FileCollection getStableClasspath() {
        return stableClasspath;
    }

    /**
     * Returns the classpath to use to execute the tests.
     */
    @Internal("captured by stableClasspath")
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
    public long getForkEvery() {
        return getDebug() ? 0 : forkEvery;
    }

    /**
     * Sets the maximum number of test classes to execute in a forked test process.
     * <p>
     * By default, Gradle automatically uses a separate JVM when executing tests, so changing this property is usually not necessary.
     * </p>
     *
     * @param forkEvery The maximum number of test classes. Use 0 to specify no maximum.
     * @since 8.1
     */
    public void setForkEvery(long forkEvery) {
        if (forkEvery < 0) {
            throw new IllegalArgumentException("Cannot set forkEvery to a value less than 0.");
        }
        this.forkEvery = forkEvery;
    }

    /**
     * Sets the maximum number of test classes to execute in a forked test process.
     * <p>
     * By default, Gradle automatically uses a separate JVM when executing tests, so changing this property is usually not necessary.
     * </p>
     *
     * @param forkEvery The maximum number of test classes. Use null or 0 to specify no maximum.
     * @deprecated Use {@link #setForkEvery(long)} instead.
     */
    @Deprecated
    public void setForkEvery(@Nullable Long forkEvery) {
        if (forkEvery == null) {
            DeprecationLogger.deprecateBehaviour("Setting Test.forkEvery to null.")
                .withAdvice("Set Test.forkEvery to 0 instead.")
                .willBecomeAnErrorInGradle9()
                .withDslReference(Test.class, "forkEvery")
                .nagUser();
            setForkEvery(0);
        } else {
            DeprecationLogger.deprecateMethod(Test.class, "setForkEvery(Long)")
                .replaceWith("Test.setForkEvery(long)")
                .willBeRemovedInGradle9()
                .withDslReference(Test.class, "forkEvery")
                .nagUser();
            setForkEvery(forkEvery.longValue());
        }
    }

    /**
     * Returns the maximum number of test processes to start in parallel.
     *
     * <p>
     * By default, Gradle executes a single test class at a time.
     * <ul>
     * <li>A value of <code>1</code> means to only execute a single test class in a single test process at a time. This is the default.</li>
     * <li>A value of <code>N</code> means that up to <code>N</code> test processes will be started to execute test classes. <b>This can improve test execution time by running multiple test classes in parallel.</b></li>
     * </ul>
     *
     * This property cannot exceed the value of {@literal max-workers} for the current build. Gradle will also limit the number of started test processes across all {@link Test} tasks.
     *
     * @return The maximum number of forked test processes.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public int getMaxParallelForks() {
        return getDebug() ? 1 : maxParallelForks;
    }

    /**
     * Sets the maximum number of test processes to start in parallel.
     * <p>
     * By default, Gradle executes a single test class at a time but allows multiple {@link Test} tasks to run in parallel.
     * </p>
     *
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
    @InputFiles
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @ToBeReplacedByLazyProperty(comment = "Should this be kept as it is?")
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
    @UsedByScanPlugin("test-distribution, test-retry")
    void setTestExecuter(TestExecuter<JvmTestExecutionSpec> testExecuter) {
        this.testExecuter = testExecuter;
    }

    /**
     * Configures the java executable to be used to run the tests.
     *
     * @since 6.7
     */
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    @Override
    boolean testsAreNotFiltered() {
        return super.testsAreNotFiltered()
            && noCategoryOrTagOrGroupSpecified();
    }

    private boolean noCategoryOrTagOrGroupSpecified() {
        TestFrameworkOptions frameworkOptions = getTestFramework().getOptions();
        if (frameworkOptions == null) {
            return true;
        }

        if (JUnitOptions.class.isAssignableFrom(frameworkOptions.getClass())) {
            JUnitOptions junitOptions = (JUnitOptions) frameworkOptions;
            return junitOptions.getIncludeCategories().isEmpty()
                && junitOptions.getExcludeCategories().isEmpty();
        } else if (JUnitPlatformOptions.class.isAssignableFrom(frameworkOptions.getClass())) {
            JUnitPlatformOptions junitPlatformOptions = (JUnitPlatformOptions) frameworkOptions;
            return junitPlatformOptions.getIncludeTags().isEmpty()
                && junitPlatformOptions.getExcludeTags().isEmpty();
        } else if (TestNGOptions.class.isAssignableFrom(frameworkOptions.getClass())) {
            TestNGOptions testNGOptions = (TestNGOptions) frameworkOptions;
            return testNGOptions.getIncludeGroups().isEmpty()
                && testNGOptions.getExcludeGroups().isEmpty();
        } else {
            throw new IllegalArgumentException("Unknown test framework: " + frameworkOptions.getClass().getName());
        }
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected JavaToolchainService getJavaToolchainService() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ProviderFactory getProviderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ActorFactory getActorFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected WorkerProcessFactory getProcessBuilderFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected Factory<PatternSet> getPatternSetFactory() {
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

    @Inject
    protected JavaModuleDetector getJavaModuleDetector() {
        throw new UnsupportedOperationException();
    }
}
