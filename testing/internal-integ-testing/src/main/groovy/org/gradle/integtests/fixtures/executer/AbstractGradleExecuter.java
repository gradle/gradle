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
package org.gradle.integtests.fixtures.executer;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.initialization.DefaultClassLoaderScope;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.integtests.fixtures.RepoScriptBlockUtil;
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer;
import org.gradle.integtests.fixtures.validation.ValidationServicesFixture;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.MutableActionSet;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.buildprocess.BuildProcessState;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.jvm.JavaHomeException;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.CachingJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.DefaultJvmVersionDetector;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.services.DefaultLoggingManagerFactory;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.console.TestOverrideConsoleDetector;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.scripts.ScriptFileUtil;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.launcher.cli.WelcomeMessageAction;
import org.gradle.launcher.daemon.configuration.DaemonBuildOptions;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;
import org.gradle.process.internal.streams.SafeStreams;
import org.gradle.test.fixtures.ResettableExpectations;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.ClosureBackedAction;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.joining;
import static org.gradle.api.internal.artifacts.BaseRepositoryFactory.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY;
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl;
import static org.gradle.integtests.fixtures.executer.AbstractGradleExecuter.CliDaemonArgument.DAEMON;
import static org.gradle.integtests.fixtures.executer.AbstractGradleExecuter.CliDaemonArgument.FOREGROUND;
import static org.gradle.integtests.fixtures.executer.AbstractGradleExecuter.CliDaemonArgument.NOT_DEFINED;
import static org.gradle.integtests.fixtures.executer.AbstractGradleExecuter.CliDaemonArgument.NO_DAEMON;
import static org.gradle.internal.service.scopes.DefaultGradleUserHomeScopeServiceRegistry.REUSE_USER_HOME_SERVICES;
import static org.gradle.util.internal.CollectionUtils.join;
import static org.gradle.util.internal.DefaultGradleVersion.VERSION_OVERRIDE_VAR;

public abstract class AbstractGradleExecuter implements GradleExecuter, ResettableExpectations {
    private static final String DEBUG_SYSPROP = "org.gradle.integtest.debug";
    private static final String LAUNCHER_DEBUG_SYSPROP = "org.gradle.integtest.launcher.debug";
    public static final int DAEMON_DEBUG_PORT = 5005;
    public static final int LAUNCHER_DEBUG_PORT = 5006;
    private static final String PROFILE_SYSPROP = "org.gradle.integtest.profile";
    private static final String ALLOW_INSTRUMENTATION_AGENT_SYSPROP = "org.gradle.integtest.agent.allowed";

    protected static final ServiceRegistry GLOBAL_SERVICES = new BuildProcessState(
        true,
        AgentStatus.of(isAgentInstrumentationEnabled()),
        ClassPath.EMPTY,
        newCommandLineProcessLogging(),
        NativeServicesTestFixture.getInstance(),
        ValidationServicesFixture.getServices()
    ).getServices();

    private static final JvmVersionDetector JVM_VERSION_DETECTOR = new DefaultJvmVersionDetector(new CachingJvmMetadataDetector(new DefaultJvmMetadataDetector(GLOBAL_SERVICES.get(ClientExecHandleBuilderFactory.class), GLOBAL_SERVICES.get(TemporaryFileProvider.class))));

    protected final static Set<String> PROPAGATED_SYSTEM_PROPERTIES = new HashSet<>();

    // TODO - don't use statics to communicate between the test runner and executer
    public static void propagateSystemProperty(String name) {
        PROPAGATED_SYSTEM_PROPERTIES.add(name);
    }

    public static void doNotPropagateSystemProperty(String name) {
        PROPAGATED_SYSTEM_PROPERTIES.remove(name);
    }

    private final Logger logger;

    protected final IntegrationTestBuildContext buildContext;

    private final Set<File> isolatedDaemonBaseDirs = new HashSet<>();
    private final Set<File> daemonCrashLogsBeforeTest;
    private final Set<GradleHandle> running = new HashSet<>();
    private final List<ExecutionResult> results = new ArrayList<>();
    private final List<String> args = new ArrayList<>();
    private final List<String> tasks = new ArrayList<>();
    private boolean allowExtraLogging = true;
    protected ConsoleAttachment consoleAttachment = ConsoleAttachment.NOT_ATTACHED;
    private File workingDir;
    private boolean quiet;
    private boolean taskList;
    private boolean dependencyList;
    private final Map<String, String> environmentVars = new HashMap<>();
    private final List<File> initScripts = new ArrayList<>();
    private String executable;
    private TestFile gradleUserHomeDir;
    private File userHomeDir;
    private String javaHome;
    private Jvm jvm;
    private File buildScript;
    private File projectDir;
    private File settingsFile;
    private boolean ignoreMissingSettingsFile;
    private boolean ignoreCleanupAssertions;
    private PipedOutputStream stdinPipe;
    private String defaultCharacterEncoding;
    private Locale defaultLocale;
    private int daemonIdleTimeoutSecs = 120;
    protected boolean requireDaemon;
    private File daemonBaseDir;
    private final List<String> buildJvmOpts = new ArrayList<>();
    private final List<String> commandLineJvmOpts = new ArrayList<>();
    private boolean useOnlyRequestedJvmOpts;
    private boolean useOwnUserHomeServices;
    private ConsoleOutput consoleType;
    protected WarningMode warningMode = WarningMode.All;
    private boolean showStacktrace = false;
    private boolean renderWelcomeMessage;
    private boolean disableToolchainDownload = true;
    private boolean disableToolchainDetection = true;
    private boolean disablePluginRepositoryMirror = false;

    private int expectedGenericDeprecationWarnings;
    private final List<ExpectedDeprecationWarning> expectedDeprecationWarnings = new ArrayList<>();
    private boolean eagerClassLoaderCreationChecksOn = true;
    private boolean stackTraceChecksOn = true;
    private boolean jdkWarningChecksOn = false;

    private final MutableActionSet<GradleExecuter> beforeExecute = new MutableActionSet<>();
    private ImmutableActionSet<GradleExecuter> afterExecute = ImmutableActionSet.empty();

    protected final GradleVersion gradleVersion;
    protected final TestDirectoryProvider testDirectoryProvider;
    protected final GradleDistribution distribution;
    private GradleVersion gradleVersionOverride;

    private JavaDebugOptionsInternal debug = new JavaDebugOptionsInternal(Boolean.getBoolean(DEBUG_SYSPROP), DAEMON_DEBUG_PORT);

    private JavaDebugOptionsInternal debugLauncher = new JavaDebugOptionsInternal(Boolean.getBoolean(LAUNCHER_DEBUG_SYSPROP), LAUNCHER_DEBUG_PORT);

    private String profiler = System.getProperty(PROFILE_SYSPROP, "");

    protected boolean interactive;

    protected boolean noExplicitNativeServicesDir;
    private boolean fullDeprecationStackTrace;
    private boolean checkDeprecations = true;
    private boolean filterJavaVersionDeprecation = true;
    private boolean checkDaemonCrash = true;

    private TestFile tmpDir;
    private DurationMeasurement durationMeasurement;

    protected AbstractGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        this(distribution, testDirectoryProvider, GradleVersion.current());
    }

    protected AbstractGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion) {
        this(distribution, testDirectoryProvider, gradleVersion, IntegrationTestBuildContext.INSTANCE);
    }

    protected AbstractGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider, GradleVersion gradleVersion, IntegrationTestBuildContext buildContext) {
        this.distribution = distribution;
        this.testDirectoryProvider = testDirectoryProvider;
        this.gradleVersion = gradleVersion;
        this.logger = Logging.getLogger(getClass());
        this.buildContext = buildContext;
        this.gradleUserHomeDir = buildContext.getGradleUserHomeDir();
        this.daemonBaseDir = buildContext.getDaemonBaseDir();
        this.daemonCrashLogsBeforeTest = ImmutableSet.copyOf(DaemonLogsAnalyzer.findCrashLogs(daemonBaseDir));

        if (gradleVersion.compareTo(GradleVersion.version("4.5.0")) < 0) {
            warningMode = null;
        }
    }

    protected Logger getLogger() {
        return logger;
    }

    @Override
    public GradleExecuter reset() {
        args.clear();
        tasks.clear();
        initScripts.clear();
        workingDir = null;
        projectDir = null;
        buildScript = null;
        settingsFile = null;
        ignoreMissingSettingsFile = false;
        // ignoreCleanupAssertions is intentionally sticky
        // ignoreCleanupAssertions = false;
        quiet = false;
        taskList = false;
        dependencyList = false;
        executable = null;
        javaHome = null;
        jvm = null;
        environmentVars.clear();
        stdinPipe = null;
        defaultCharacterEncoding = null;
        defaultLocale = null;
        commandLineJvmOpts.clear();
        buildJvmOpts.clear();
        useOnlyRequestedJvmOpts = false;
        expectedGenericDeprecationWarnings = 0;
        expectedDeprecationWarnings.clear();
        stackTraceChecksOn = true;
        jdkWarningChecksOn = false;
        renderWelcomeMessage = false;
        disableToolchainDownload = true;
        disableToolchainDetection = true;
        debug = new JavaDebugOptionsInternal(Boolean.getBoolean(DEBUG_SYSPROP), DAEMON_DEBUG_PORT);
        debugLauncher = new JavaDebugOptionsInternal(Boolean.getBoolean(LAUNCHER_DEBUG_SYSPROP), LAUNCHER_DEBUG_PORT);
        profiler = System.getProperty(PROFILE_SYSPROP, "");
        interactive = false;
        checkDeprecations = true;
        filterJavaVersionDeprecation = true;
        durationMeasurement = null;
        consoleType = null;
        warningMode = WarningMode.All;
        return this;
    }

    @Override
    public GradleDistribution getDistribution() {
        return distribution;
    }

    @Override
    public TestDirectoryProvider getTestDirectoryProvider() {
        return testDirectoryProvider;
    }

    @Override
    public void beforeExecute(Action<? super GradleExecuter> action) {
        beforeExecute.add(action);
    }

    @Override
    public void beforeExecute(@DelegatesTo(GradleExecuter.class) Closure action) {
        beforeExecute.add(new ClosureBackedAction<>(action));
    }

    @Override
    public void afterExecute(Action<? super GradleExecuter> action) {
        afterExecute = afterExecute.add(action);
    }

    @Override
    public void afterExecute(@DelegatesTo(GradleExecuter.class) Closure action) {
        afterExecute(new ClosureBackedAction<>(action));
    }

    @Override
    public GradleExecuter inDirectory(File directory) {
        workingDir = directory;
        return this;
    }

    public File getWorkingDir() {
        return workingDir == null ? getTestDirectoryProvider().getTestDirectory() : workingDir;
    }

    @Override
    public GradleExecuter copyTo(GradleExecuter executer) {
        executer.withGradleUserHomeDir(gradleUserHomeDir);
        executer.withDaemonIdleTimeoutSecs(daemonIdleTimeoutSecs);
        executer.withDaemonBaseDir(daemonBaseDir);

        if (workingDir != null) {
            executer.inDirectory(workingDir);
        }
        if (projectDir != null) {
            executer.usingProjectDirectory(projectDir);
        }
        if (buildScript != null) {
            executer.usingBuildScript(buildScript);
        }
        if (settingsFile != null) {
            executer.usingSettingsFile(settingsFile);
        }
        if (ignoreMissingSettingsFile) {
            executer.ignoreMissingSettingsFile();
        }
        if (ignoreCleanupAssertions) {
            executer.ignoreCleanupAssertions();
        }
        if (javaHome != null) {
            executer.withJavaHome(javaHome);
        }
        if (jvm != null) {
            executer.withJvm(jvm);
        }
        for (File initScript : initScripts) {
            executer.usingInitScript(initScript);
        }
        executer.withTasks(tasks);
        executer.withArguments(args);
        executer.withEnvironmentVars(environmentVars);
        executer.usingExecutable(executable);
        if (quiet) {
            executer.withQuietLogging();
        }
        if (taskList) {
            executer.withTaskList();
        }
        if (dependencyList) {
            executer.withDependencyList();
        }

        if (userHomeDir != null) {
            executer.withUserHomeDir(userHomeDir);
        }

        if (stdinPipe != null) {
            executer.withStdinPipe(stdinPipe);
        }

        if (defaultCharacterEncoding != null) {
            executer.withDefaultCharacterEncoding(defaultCharacterEncoding);
        }
        if (noExplicitNativeServicesDir) {
            executer.withNoExplicitNativeServicesDir();
        }
        if (fullDeprecationStackTrace) {
            executer.withFullDeprecationStackTraceEnabled();
        }
        if (defaultLocale != null) {
            executer.withDefaultLocale(defaultLocale);
        }
        executer.withCommandLineGradleOpts(commandLineJvmOpts);
        executer.withBuildJvmOpts(buildJvmOpts);
        if (useOnlyRequestedJvmOpts) {
            executer.useOnlyRequestedJvmOpts();
        }
        executer.noExtraLogging();

        if (expectedGenericDeprecationWarnings > 0) {
            executer.expectDeprecationWarnings(expectedGenericDeprecationWarnings);
        }
        expectedDeprecationWarnings.forEach(executer::expectDeprecationWarning);
        if (!eagerClassLoaderCreationChecksOn) {
            executer.withEagerClassLoaderCreationCheckDisabled();
        }
        if (!stackTraceChecksOn) {
            executer.withStackTraceChecksDisabled();
        }
        if (jdkWarningChecksOn) {
            executer.withJdkWarningChecksEnabled();
        }
        if (useOwnUserHomeServices) {
            executer.withOwnUserHomeServices();
        }
        if (requireDaemon) {
            executer.requireDaemon();
        }
        if (!checkDaemonCrash) {
            executer.noDaemonCrashChecks();
        }
        if (gradleVersionOverride != null) {
            executer.withGradleVersionOverride(gradleVersionOverride);
        }

        if (debug.isEnabled()){
            executer.startBuildProcessInDebugger(opts -> debug.copyTo(opts));
        }
        if (debugLauncher.isEnabled()){
            executer.startLauncherInDebugger(opts -> debugLauncher.copyTo(opts));
        }

        executer
            .withProfiler(profiler)
            .withForceInteractive(interactive);

        if (!checkDeprecations) {
            executer.noDeprecationChecks();
        }

        if (!filterJavaVersionDeprecation) {
            executer.disableDaemonJavaVersionDeprecationFiltering();
        }

        if (durationMeasurement != null) {
            executer.withDurationMeasurement(durationMeasurement);
        }

        if (consoleType != null) {
            executer.withConsole(consoleType);
        }

        executer.withWarningMode(warningMode);

        if (showStacktrace) {
            executer.withStacktraceEnabled();
        }

        if (renderWelcomeMessage) {
            executer.withWelcomeMessageEnabled();
        }

        if (!disableToolchainDetection) {
            executer.withToolchainDetectionEnabled();
        }
        if (!disableToolchainDownload) {
            executer.withToolchainDownloadEnabled();
        }

        executer.withTestConsoleAttached(consoleAttachment);

        if (disablePluginRepositoryMirror) {
            executer.withPluginRepositoryMirrorDisabled();
        }

        return executer;
    }

    @Override
    @Deprecated
    public GradleExecuter usingBuildScript(File buildScript) {
        this.buildScript = buildScript;
        return this;
    }

    @Override
    public GradleExecuter usingProjectDirectory(File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    @Override
    @Deprecated
    public GradleExecuter usingSettingsFile(File settingsFile) {
        this.settingsFile = settingsFile;
        return this;
    }

    @Override
    public GradleExecuter usingInitScript(File initScript) {
        if (RepoScriptBlockUtil.isMirrorEnabled()) {
            initScripts.add(initScript);
        }
        return this;
    }

    @Override
    public TestFile getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    @Override
    public GradleExecuter withGradleUserHomeDir(File userHomeDir) {
        this.gradleUserHomeDir = userHomeDir == null ? null : new TestFile(userHomeDir);
        return this;
    }

    @Override
    public GradleExecuter withGradleVersionOverride(GradleVersion gradleVersion) {
        this.gradleVersionOverride = gradleVersion;
        return this;
    }

    @Override
    public GradleExecuter requireOwnGradleUserHomeDir() {
        return withGradleUserHomeDir(testDirectoryProvider.getTestDirectory().file("user-home"));
    }

    @Override
    public GradleExecuter requireOwnGradleUserHomeDir(String because) {
        return requireOwnGradleUserHomeDir();
    }

    public File getUserHomeDir() {
        return userHomeDir;
    }

    protected GradleInvocation buildInvocation() {
        validateDaemonVisibility();

        GradleInvocation gradleInvocation = new GradleInvocation();
        gradleInvocation.environmentVars.putAll(environmentVars);
        if (gradleVersionOverride != null) {
            gradleInvocation.environmentVars.put(VERSION_OVERRIDE_VAR, gradleVersionOverride.getVersion());
        }
        if (!useOnlyRequestedJvmOpts) {
            gradleInvocation.buildJvmArgs.addAll(getImplicitBuildJvmArgs());
        }
        gradleInvocation.buildJvmArgs.addAll(buildJvmOpts);
        calculateLauncherJvmArgs(gradleInvocation);
        gradleInvocation.args.addAll(getAllArgs());

        transformInvocation(gradleInvocation);

        if (!gradleInvocation.implicitLauncherJvmArgs.isEmpty()) {
            throw new IllegalStateException("Implicit JVM args have not been handled.");
        }

        return gradleInvocation;
    }

    protected void validateDaemonVisibility() {
        if (isUseDaemon() && isSharedDaemons()) {
            throw new IllegalStateException("Daemon that will be visible to other tests has been requested.");
        }
    }

    /**
     * Adjusts the calculated invocation prior to execution. This method is responsible for handling the implicit launcher JVM args in some way, by mutating the invocation appropriately.
     */
    protected void transformInvocation(GradleInvocation gradleInvocation) {
        gradleInvocation.launcherJvmArgs.addAll(0, gradleInvocation.implicitLauncherJvmArgs);
        gradleInvocation.implicitLauncherJvmArgs.clear();
    }

    /**
     * Returns the JVM opts that should be used to start a forked JVM.
     */
    private void calculateLauncherJvmArgs(GradleInvocation gradleInvocation) {
        // Add JVM args that were explicitly requested
        gradleInvocation.launcherJvmArgs.addAll(commandLineJvmOpts);

        if (isUseDaemon() && !gradleInvocation.buildJvmArgs.isEmpty()) {
            // Pass build JVM args through to daemon via system property on the launcher JVM
            String quotedArgs = joinAndQuoteJvmArgs(gradleInvocation.buildJvmArgs);
            gradleInvocation.implicitLauncherJvmArgs.add("-Dorg.gradle.jvmargs=" + quotedArgs);
        } else {
            // Have to pass build JVM args directly to launcher JVM
            gradleInvocation.launcherJvmArgs.addAll(gradleInvocation.buildJvmArgs);
        }

        // Set the implicit system properties regardless of whether default JVM args are required or not, this should not interfere with tests' intentions
        // These will also be copied across to any daemon used
        for (Map.Entry<String, String> entry : getImplicitJvmSystemProperties().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            gradleInvocation.implicitLauncherJvmArgs.add(String.format("-D%s=%s", key, value));
        }
        if (isDebugLauncher()) {
            if (System.getenv().containsKey("CI")) {
                throw new IllegalArgumentException("Builds cannot be started with the debugger enabled on CI. This will cause tests to hang forever. Remove the call to startLauncherInDebugger().");
            }
            gradleInvocation.implicitLauncherJvmArgs.add(debugLauncher.toDebugArgument());
        }
        gradleInvocation.implicitLauncherJvmArgs.add("-ea");
    }

    protected static String joinAndQuoteJvmArgs(List<String> buildJvmArgs) {
        return join(" ", buildJvmArgs, input -> {
            if (input.contains("'")) {
                throw new IllegalArgumentException("Cannot handle JVM args containing single quotes: " + input);
            }
            return "'" + input + "'";
        });
    }

    /**
     * Returns additional JVM args that should be used to start the build JVM.
     */
    protected List<String> getImplicitBuildJvmArgs() {
        List<String> buildJvmOpts = new ArrayList<>();
        buildJvmOpts.add("-ea");

        if (isDebug()) {
            if (System.getenv().containsKey("CI")) {
                throw new IllegalArgumentException("Builds cannot be started with the debugger enabled on CI. This will cause tests to hang forever. Remove the call to startBuildProcessInDebugger().");
            }
            buildJvmOpts.add(debug.toDebugArgument());
        }
        if (isProfile()) {
            buildJvmOpts.add(profiler);
        }

        if (isSharedDaemons()) {
            buildJvmOpts.add("-Xms256m");
            buildJvmOpts.add("-Xmx1024m");
        } else {
            buildJvmOpts.add("-Xms256m");
            buildJvmOpts.add("-Xmx512m");
        }
        buildJvmOpts.add("-XX:MaxMetaspaceSize=512m");

        buildJvmOpts.add("-XX:+HeapDumpOnOutOfMemoryError");
        buildJvmOpts.add("-XX:HeapDumpPath=" + buildContext.getGradleUserHomeDir());
        return buildJvmOpts;
    }

    @Override
    public GradleExecuter withUserHomeDir(File userHomeDir) {
        this.userHomeDir = userHomeDir;
        return this;
    }

    protected String getJavaHome() {
        if (javaHome != null) {
            return javaHome;
        }
        if (jvm != null) {
            return jvm.getJavaHome().getAbsolutePath();
        }
        return Jvm.current().getJavaHome().getAbsolutePath();
    }

    protected File getJavaHomeLocation() {
        return new File(getJavaHome());
    }

    @Override
    public GradleExecuter withJavaHome(String javaHome) {
        this.javaHome = javaHome;
        this.jvm = null;
        return this;
    }

    @Override
    public GradleExecuter withJvm(Jvm jvm) {
        if (jvm.getJavaVersion() == null) {
            throw new IllegalArgumentException(
                "The provided JVM does not have a version and was likely created with Jvm.forHome(File). " +
                    "This method should only be used with probed JVMs. " +
                    "Use withJavaHome(String) instead."
            );
        }

        this.jvm = jvm;
        this.javaHome = null;
        return this;
    }

    protected final JavaVersion getJavaVersionFromJavaHome() {
        try {
            return JavaVersion.toVersion(JVM_VERSION_DETECTOR.getJavaVersionMajor(Jvm.forHome(getJavaHomeLocation())));
        } catch (IllegalArgumentException | JavaHomeException e) {
            return JavaVersion.current();
        }
    }

    @Override
    public GradleExecuter usingExecutable(String script) {
        this.executable = script;
        return this;
    }

    public String getExecutable() {
        return executable;
    }

    @Override
    public GradleExecuter withStdinPipe() {
        return withStdinPipe(new PipedOutputStream());
    }

    @Override
    public GradleExecuter withStdinPipe(PipedOutputStream stdInPipe) {
        this.stdinPipe = stdInPipe;
        return this;
    }

    public InputStream connectStdIn() {
        try {
            return stdinPipe == null ? SafeStreams.emptyInput() : new PipedInputStream(stdinPipe);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public PipedOutputStream getStdinPipe() {
        return stdinPipe;
    }

    @Override
    public GradleExecuter withDefaultCharacterEncoding(String defaultCharacterEncoding) {
        this.defaultCharacterEncoding = defaultCharacterEncoding;
        return this;
    }

    public String getDefaultCharacterEncoding() {
        return defaultCharacterEncoding == null ? Charset.defaultCharset().name() : defaultCharacterEncoding;
    }

    @Override
    public GradleExecuter withDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
        return this;
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public boolean isQuiet() {
        return quiet;
    }

    @Override
    public GradleExecuter withQuietLogging() {
        quiet = true;
        return this;
    }

    @Override
    public GradleExecuter withTaskList() {
        taskList = true;
        return this;
    }

    @Override
    public GradleExecuter withDependencyList() {
        dependencyList = true;
        return this;
    }

    @Override
    public GradleExecuter withArguments(String... args) {
        return withArguments(Arrays.asList(args));
    }

    @Override
    public GradleExecuter withArguments(List<String> args) {
        this.args.clear();
        this.args.addAll(args);
        return this;
    }

    @Override
    public GradleExecuter withArgument(String arg) {
        this.args.add(arg);
        return this;
    }

    @Override
    public final GradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        Preconditions.checkArgument(!environment.containsKey("JAVA_HOME"), "Cannot provide JAVA_HOME to withEnvironmentVars, use withJavaHome instead");
        environmentVars.clear();
        for (Map.Entry<String, ?> entry : environment.entrySet()) {
            environmentVars.put(entry.getKey(), entry.getValue().toString());
        }
        return this;
    }

    protected Map<String, String> getEnvironmentVars() {
        return new HashMap<>(environmentVars);
    }

    protected String toJvmArgsString(Iterable<String> jvmArgs) {
        StringBuilder result = new StringBuilder();
        for (String jvmArg : jvmArgs) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (jvmArg.contains(" ")) {
                assert !jvmArg.contains("\"") : "jvmArg '" + jvmArg + "' contains '\"'";
                result.append('"');
                result.append(jvmArg);
                result.append('"');
            } else {
                result.append(jvmArg);
            }
        }

        return result.toString();
    }

    @Override
    public GradleExecuter withTasks(String... names) {
        return withTasks(Arrays.asList(names));
    }

    @Override
    public GradleExecuter withTasks(List<String> names) {
        tasks.clear();
        tasks.addAll(names);
        return this;
    }

    @Override
    public GradleExecuter withDaemonIdleTimeoutSecs(int secs) {
        daemonIdleTimeoutSecs = secs;
        return this;
    }

    @Override
    public GradleExecuter useOnlyRequestedJvmOpts() {
        useOnlyRequestedJvmOpts = true;
        return this;
    }

    @Override
    public GradleExecuter withDaemonBaseDir(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir;
        return this;
    }

    @Override
    public GradleExecuter withReadOnlyCacheDir(File cacheDir) {
        return withReadOnlyCacheDir(cacheDir.getAbsolutePath());
    }

    @Override
    public GradleExecuter withReadOnlyCacheDir(String cacheDir) {
        environmentVars.put(ArtifactCachesProvider.READONLY_CACHE_ENV_VAR, cacheDir);
        return this;
    }

    @Override
    public GradleExecuter requireIsolatedDaemons() {
        return withDaemonBaseDir(testDirectoryProvider.getTestDirectory().file("daemon"));
    }

    @Override
    public GradleExecuter withWorkerDaemonsExpirationDisabled() {
        return withCommandLineGradleOpts("-Dorg.gradle.workers.internal.disable-daemons-expiration=true");
    }

    @Override
    public boolean usesSharedDaemons() {
        return isSharedDaemons();
    }

    @Override
    public File getDaemonBaseDir() {
        return daemonBaseDir;
    }

    @Override
    public GradleExecuter requireDaemon() {
        this.requireDaemon = true;
        return this;
    }

    protected boolean isSharedDaemons() {
        return daemonBaseDir.equals(buildContext.getDaemonBaseDir());
    }

    @Override
    public boolean isUseDaemon() {
        CliDaemonArgument cliDaemonArgument = resolveCliDaemonArgument();
        if (cliDaemonArgument == NO_DAEMON || cliDaemonArgument == FOREGROUND) {
            return false;
        }
        return requireDaemon || cliDaemonArgument == DAEMON;
    }

    protected boolean isSingleUseDaemonRequested() {
        return resolveCliDaemonArgument() == NO_DAEMON && requireDaemon;
    }

    public static boolean isAgentInstrumentationEnabled() {
        return Boolean.parseBoolean(System.getProperty(ALLOW_INSTRUMENTATION_AGENT_SYSPROP, "true"));
    }

    @Override
    public GradleExecuter withOwnUserHomeServices() {
        useOwnUserHomeServices = true;
        return this;
    }

    @Override
    public GradleExecuter withWarningMode(WarningMode warningMode) {
        this.warningMode = warningMode;
        return this;
    }

    @Override
    public GradleExecuter withConsole(ConsoleOutput consoleType) {
        this.consoleType = consoleType;
        return this;
    }

    @Override
    public GradleExecuter withStacktraceEnabled() {
        showStacktrace = true;
        return this;
    }

    @Override
    public GradleExecuter withWelcomeMessageEnabled() {
        renderWelcomeMessage = true;
        return this;
    }

    @Override
    public GradleExecuter withToolchainDetectionEnabled() {
        disableToolchainDetection = false;
        return this;
    }

    @Override
    public GradleExecuter withToolchainDownloadEnabled() {
        disableToolchainDownload = false;
        return this;
    }

    @Override
    public GradleExecuter withRepositoryMirrors() {
        beforeExecute(gradleExecuter -> usingInitScript(RepoScriptBlockUtil.createMirrorInitScript()));
        return this;
    }

    @Override
    public GradleExecuter withGlobalRepositoryMirrors() {
        beforeExecute(gradleExecuter -> {
            TestFile userHome = testDirectoryProvider.getTestDirectory().file("user-home");
            withGradleUserHomeDir(userHome);
            userHome.file("init.d/mirrors.gradle").write(RepoScriptBlockUtil.mirrorInitScript());
        });
        return this;
    }

    @Override
    public GradleExecuter withPluginRepositoryMirrorDisabled() {
        disablePluginRepositoryMirror = true;
        return this;
    }

    @Override
    public GradleExecuter ignoreCleanupAssertions() {
        this.ignoreCleanupAssertions = true;
        return this;
    }

    @Override
    public void resetExpectations() {
        cleanup();
    }

    /**
     * Performs cleanup at completion of the test.
     */
    public void cleanup() {
        stopRunningBuilds();
        cleanupIsolatedDaemons();
        checkForDaemonCrashesInSharedLocations();
        assertVisitedExecutionResults();
    }

    private void stopRunningBuilds() {
        for (GradleHandle handle : running) {
            try {
                handle.abort().waitForExit();
            } catch (Exception e) {
                getLogger().warn("Problem stopping running build", e);
            }
        }
    }

    private void cleanupIsolatedDaemons() {
        List<DaemonLogsAnalyzer> analyzers = new ArrayList<>();
        List<GradleVersion> versions = (gradleVersionOverride != null)
            ? ImmutableList.of(gradleVersion, gradleVersionOverride)
            : ImmutableList.of(gradleVersion);
        for (File dir : isolatedDaemonBaseDirs) {
            for (GradleVersion version : versions) {
                try {
                    DaemonLogsAnalyzer analyzer = new DaemonLogsAnalyzer(dir, version.getVersion());
                    analyzers.add(analyzer);
                    analyzer.killAll();
                } catch (Exception e) {
                    getLogger().warn("Problem killing isolated daemons of Gradle version " + version + " in " + dir, e);
                }
            }
        }

        if (checkDaemonCrash) {
            analyzers.forEach(DaemonLogsAnalyzer::assertNoCrashedDaemon);
        }
    }

    private void checkForDaemonCrashesInSharedLocations() {
        checkForDaemonCrashes(getWorkingDir(), it -> true);
        checkForDaemonCrashes(buildContext.getDaemonBaseDir(), crashLog -> !daemonCrashLogsBeforeTest.contains(crashLog));
    }

    private void checkForDaemonCrashes(File dirToSearch, Predicate<File> crashLogFilter) {
        if (checkDaemonCrash) {
            List<File> crashLogs = DaemonLogsAnalyzer.findCrashLogs(dirToSearch).stream()
                .filter(crashLogFilter)
                .collect(Collectors.toList());
            if (!crashLogs.isEmpty()) {
                throw new AssertionError(String.format(
                    "Found crash logs: '%s'",
                    crashLogs.stream().map(File::getAbsolutePath).collect(joining("', '"))
                ));
            }
        }
    }

    private void assertVisitedExecutionResults() {
        if (!ignoreCleanupAssertions) {
            for (ExecutionResult result : results) {
                result.assertResultVisited();
            }
        }
    }

    enum CliDaemonArgument {
        NOT_DEFINED,
        DAEMON,
        NO_DAEMON,
        FOREGROUND
    }

    protected CliDaemonArgument resolveCliDaemonArgument() {
        for (int i = args.size() - 1; i >= 0; i--) {
            final String arg = args.get(i);
            if (arg.equals("--daemon")) {
                return DAEMON;
            }
            if (arg.equals("--no-daemon")) {
                return NO_DAEMON;
            }
            if (arg.equals("--foreground")) {
                return FOREGROUND;
            }
        }
        return NOT_DEFINED;
    }

    private boolean noDaemonArgumentGiven() {
        return resolveCliDaemonArgument() == NOT_DEFINED;
    }

    protected List<String> getAllArgs() {
        List<String> allArgs = new ArrayList<>();
        if (buildScript != null) {
            allArgs.add("--build-file");
            allArgs.add(buildScript.getAbsolutePath());
        }
        if (projectDir != null) {
            allArgs.add("--project-dir");
            allArgs.add(projectDir.getAbsolutePath());
        }
        for (File initScript : initScripts) {
            allArgs.add("--init-script");
            allArgs.add(initScript.getAbsolutePath());
        }
        if (settingsFile != null) {
            allArgs.add("--settings-file");
            allArgs.add(settingsFile.getAbsolutePath());
        }
        if (quiet) {
            allArgs.add("--quiet");
        }
        if (noDaemonArgumentGiven()) {
            if (isUseDaemon()) {
                allArgs.add("--daemon");
            } else {
                allArgs.add("--no-daemon");
            }
        }
        if (showStacktrace) {
            allArgs.add("--stacktrace");
        }
        if (taskList) {
            allArgs.add("tasks");
        }
        if (dependencyList) {
            allArgs.add("dependencies");
        }

        if (settingsFile == null && !ignoreMissingSettingsFile) {
            ensureSettingsFileAvailable();
        }

        if (getGradleUserHomeDir() != null) {
            allArgs.add("--gradle-user-home");
            allArgs.add(getGradleUserHomeDir().getAbsolutePath());
        }

        if (consoleType != null) {
            String s = consoleType.toString();
            allArgs.add("--console=" + s.toLowerCase(Locale.ROOT));
        }

        // Rich console output is difficult to check, so we disable warnings
        WarningMode warningMode = isRichConsole() ? WarningMode.None : this.warningMode;
        if (warningMode != null) {
            String s = warningMode.toString();
            allArgs.add("--warning-mode=" + s.toLowerCase(Locale.ROOT));
        }

        if (disableToolchainDownload) {
            allArgs.add("-P" + AutoInstalledInstallationSupplier.AUTO_DOWNLOAD + "=false");
        }
        if (disableToolchainDetection) {
            allArgs.add("-P" + ToolchainConfiguration.AUTO_DETECT + "=false");
        }

        boolean hasAgentArgument = args.stream().anyMatch(s -> s.contains(DaemonBuildOptions.ApplyInstrumentationAgentOption.GRADLE_PROPERTY));
        if (!hasAgentArgument && !isAgentInstrumentationEnabled()) {
            allArgs.add("-D" + DaemonBuildOptions.ApplyInstrumentationAgentOption.GRADLE_PROPERTY + "=false");
        }

        allArgs.addAll(args);
        allArgs.addAll(tasks);
        return allArgs;
    }

    @Override
    public GradleExecuter ignoreMissingSettingsFile() {
        ignoreMissingSettingsFile = true;
        return this;
    }

    private void ensureSettingsFileAvailable() {
        TestFile workingDir = new TestFile(getWorkingDir());
        TestFile dir = workingDir;
        while (dir != null && getTestDirectoryProvider().getTestDirectory().isSelfOrDescendant(dir)) {
            if (hasSettingsFile(dir) || hasSettingsFile(dir.file("master"))) {
                return;
            }
            dir = dir.getParentFile();
        }
        workingDir.createFile("settings.gradle");
    }

    private static boolean hasSettingsFile(TestFile dir) {
        if (dir.isDirectory()) {
            String[] settingsFileNames = ScriptFileUtil.getValidSettingsFileNames();
            for (String settingsFileName : settingsFileNames) {
                if (dir.file(settingsFileName).isFile()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns the set of system properties that should be set on every JVM used by this executer.
     */
    protected Map<String, String> getImplicitJvmSystemProperties() {
        Map<String, String> properties = new LinkedHashMap<>();

        if (getUserHomeDir() != null) {
            properties.put("user.home", getUserHomeDir().getAbsolutePath());
        }

        properties.put(DaemonBuildOptions.IdleTimeoutOption.GRADLE_PROPERTY, "" + (daemonIdleTimeoutSecs * 1000));
        properties.put(DaemonBuildOptions.BaseDirOption.GRADLE_PROPERTY, daemonBaseDir.getAbsolutePath());
        if (!noExplicitNativeServicesDir) {
            properties.put(NativeServices.NATIVE_DIR_OVERRIDE, buildContext.getNativeServicesDir().getAbsolutePath());
        }
        properties.put(LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME, Boolean.toString(fullDeprecationStackTrace));

        boolean useCustomGradleUserHomeDir = gradleUserHomeDir != null && !gradleUserHomeDir.equals(buildContext.getGradleUserHomeDir());
        if (useOwnUserHomeServices || useCustomGradleUserHomeDir) {
            properties.put(REUSE_USER_HOME_SERVICES, "false");
        }
        if (buildJvmOpts.stream().noneMatch(arg -> arg.startsWith("-Djava.io.tmpdir="))) {
            if (tmpDir == null) {
                tmpDir = getDefaultTmpDir();
            }
            String tmpDirPath = tmpDir.createDir().getAbsolutePath();
            if (!tmpDirPath.contains(" ") || (getDistribution().isSupportsSpacesInGradleAndJavaOpts() && supportsWhiteSpaceInEnvVars())) {
                properties.put("java.io.tmpdir", tmpDirPath);
            }
        }

        if (!disablePluginRepositoryMirror) {
            properties.put(PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY, gradlePluginRepositoryMirrorUrl());
        }

        properties.put("file.encoding", getDefaultCharacterEncoding());
        if (getJavaVersionFromJavaHome() == JavaVersion.VERSION_18) {
            properties.put("sun.stdout.encoding", getDefaultCharacterEncoding());
            properties.put("sun.stderr.encoding", getDefaultCharacterEncoding());
        } else if (getJavaVersionFromJavaHome().isCompatibleWith(JavaVersion.VERSION_19)) {
            properties.put("stdout.encoding", getDefaultCharacterEncoding());
            properties.put("stderr.encoding", getDefaultCharacterEncoding());
        }
        Locale locale = getDefaultLocale();
        if (locale != null) {
            properties.put("user.language", locale.getLanguage());
            properties.put("user.country", locale.getCountry());
            properties.put("user.variant", locale.getVariant());
        }

        properties.put(DefaultClassLoaderScope.STRICT_MODE_PROPERTY, Boolean.toString(eagerClassLoaderCreationChecksOn));

        if (interactive) {
            properties.put(TestOverrideConsoleDetector.INTERACTIVE_TOGGLE, "true");
        }

        properties.put(WelcomeMessageAction.WELCOME_MESSAGE_ENABLED_SYSTEM_PROPERTY, Boolean.toString(renderWelcomeMessage));

        // Having this unset is now deprecated, will default to `false` in Gradle 9.0
        // TODO remove - see https://github.com/gradle/gradle/issues/26810
        properties.put("org.gradle.kotlin.dsl.skipMetadataVersionCheck", "false");

        return properties;
    }

    protected boolean supportsWhiteSpaceInEnvVars() {
        return true;
    }

    @Override
    public final GradleHandle start() {
        assert afterExecute.isEmpty() : "afterExecute actions are not implemented for async execution";
        beforeBuildSetup();
        try {
            GradleHandle handle = createGradleHandle();
            running.add(handle);
            return new ResultCollectingHandle(handle);
        } finally {
            reset();
        }
    }

    @Override
    public final ExecutionResult run() {
        return run(() -> {
            ExecutionResult result = doRun();
            if (errorsShouldAppearOnStdout()) {
                result = new ErrorsOnStdoutScrapingExecutionResult(result);
            }
            return result;
        });
    }

    /**
     * Allows a subclass to expose additional APIs for running builds.
     */
    protected ExecutionResult run(Supplier<ExecutionResult> action) {
        beforeBuildSetup();
        try {
            ExecutionResult result = action.get();
            afterBuildCleanup(result);
            return result;
        } finally {
            finished();
        }
    }

    protected void finished() {
        reset();
    }

    @Override
    public final ExecutionFailure runWithFailure() {
        beforeBuildSetup();
        try {
            ExecutionFailure executionFailure = doRunWithFailure();
            if (errorsShouldAppearOnStdout()) {
                executionFailure = new ErrorsOnStdoutScrapingExecutionFailure(executionFailure);
            }
            afterBuildCleanup(executionFailure);
            return executionFailure;
        } finally {
            finished();
        }
    }

    private void collectStateBeforeExecution() {
        if (!isSharedDaemons()) {
            isolatedDaemonBaseDirs.add(daemonBaseDir);
        }
    }

    private void beforeBuildSetup() {
        for (ExecutionResult result : results) {
            result.assertResultVisited();
        }
        beforeExecute.execute(this);
        assertCanExecute();
        assert !(usesSharedDaemons() && (args.contains("--stop") || tasks.contains("--stop"))) : "--stop cannot be used with daemons that are shared with other tests, since this will cause other tests to fail.";
        collectStateBeforeExecution();
    }

    private void afterBuildCleanup(ExecutionResult result) {
        afterExecute.execute(this);
        results.add(result);
        checkForDaemonCrashes(getWorkingDir(), it -> true);
    }

    protected GradleHandle createGradleHandle() {
        throw new UnsupportedOperationException(String.format("%s does not support running asynchronously.", getClass().getSimpleName()));
    }

    protected abstract ExecutionResult doRun();

    protected abstract ExecutionFailure doRunWithFailure();

    @Override
    public GradleExecuter withCommandLineGradleOpts(Iterable<String> jvmOpts) {
        CollectionUtils.addAll(commandLineJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public GradleExecuter withCommandLineGradleOpts(String... jvmOpts) {
        CollectionUtils.addAll(commandLineJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public AbstractGradleExecuter withBuildJvmOpts(String... jvmOpts) {
        CollectionUtils.addAll(buildJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public GradleExecuter withBuildJvmOpts(Iterable<String> jvmOpts) {
        CollectionUtils.addAll(buildJvmOpts, jvmOpts);
        return this;
    }

    @Override
    public GradleExecuter withBuildCacheEnabled() {
        return withArgument("--build-cache");
    }

    protected Action<ExecutionResult> getResultAssertion() {
        boolean shouldCheckDeprecations = checkDeprecations;

        // Rich consoles mess with our deprecation log scraping,
        // and we anyway set --warning-mode=none for rich consoles, see #getAllArgs()
        if (isRichConsole()) {
            shouldCheckDeprecations = false;
        }

        List<ExpectedDeprecationWarning> maybeExpectedDeprecationWarnings = new ArrayList<>();
        if (filterJavaVersionDeprecation) {
            maybeExpectedDeprecationWarnings.add(ExpectedDeprecationWarning.withMessage(normalizeDocumentationLink(
                "Executing Gradle on JVM versions 16 and lower has been deprecated. " +
                    "This will fail with an error in Gradle 9.0. " +
                    "Use JVM 17 or greater to execute Gradle. " +
                    "Projects can continue to use older JVM versions via toolchains. " +
                    "Consult the upgrading guide for further information: " +
                    "https://docs.gradle.org/current/userguide/upgrading_version_8.html#minimum_daemon_jvm_version"
            )));
        }

        return new ResultAssertion(
            expectedGenericDeprecationWarnings,
            expectedDeprecationWarnings,
            maybeExpectedDeprecationWarnings,
            !stackTraceChecksOn,
            shouldCheckDeprecations,
            jdkWarningChecksOn
        );
    }

    private boolean isRichConsole() {
        boolean isAuto = consoleType == null || consoleType == ConsoleOutput.Auto;
        return isAuto && consoleAttachment != ConsoleAttachment.NOT_ATTACHED
            || consoleType == ConsoleOutput.Verbose
            || consoleType == ConsoleOutput.Rich;
    }

    @Override
    public GradleExecuter expectDeprecationWarning() {
        return expectDeprecationWarnings(1);
    }

    @Override
    public GradleExecuter expectDeprecationWarnings(int count) {
        Preconditions.checkState(expectedGenericDeprecationWarnings == 0, "expected deprecation count is already set for this execution");
        Preconditions.checkArgument(count > 0, "expected deprecation count must be positive");
        expectedGenericDeprecationWarnings = count;
        return this;
    }

    @Override
    public GradleExecuter expectDeprecationWarning(ExpectedDeprecationWarning warning) {
        expectedDeprecationWarnings.add(warning);
        return this;
    }

    @Override
    public GradleExecuter expectDocumentedDeprecationWarning(String warning) {
        return expectDeprecationWarning(normalizeDocumentationLink(warning));
    }

    private String normalizeDocumentationLink(String warning) {
        if (gradleVersionOverride != null) {
            return DocumentationUtils.normalizeDocumentationLink(warning, gradleVersionOverride);
        } else {
            return DocumentationUtils.normalizeDocumentationLink(warning, gradleVersion);
        }
    }

    @Override
    public GradleExecuter noDeprecationChecks() {
        checkDeprecations = false;
        return this;
    }

    @Override
    public GradleExecuter disableDaemonJavaVersionDeprecationFiltering() {
        filterJavaVersionDeprecation = false;
        return this;
    }

    @Override
    public GradleExecuter noDaemonCrashChecks() {
        checkDaemonCrash = false;
        return this;
    }

    @Override
    public GradleExecuter withEagerClassLoaderCreationCheckDisabled() {
        eagerClassLoaderCreationChecksOn = false;
        return this;
    }

    @Override
    public GradleExecuter withStackTraceChecksDisabled() {
        stackTraceChecksOn = false;
        return this;
    }

    @Override
    public GradleExecuter withJdkWarningChecksEnabled() {
        jdkWarningChecksOn = true;
        return this;
    }

    protected TestFile getDefaultTmpDir() {
        return buildContext.getTmpDir().createDir();
    }

    @Override
    public GradleExecuter noExtraLogging() {
        this.allowExtraLogging = false;
        return this;
    }

    public boolean isAllowExtraLogging() {
        return allowExtraLogging;
    }

    @Override
    public GradleExecuter startBuildProcessInDebugger(boolean flag) {
        debug.setEnabled(flag);
        return this;
    }


    @Override
    public GradleExecuter startBuildProcessInDebugger(Action<JavaDebugOptionsInternal> action) {
        debug.setEnabled(true);
        action.execute(debug);
        return this;
    }

    @Override
    public GradleExecuter startLauncherInDebugger(boolean flag) {
        debugLauncher.setEnabled(flag);
        return this;
    }

    @Override
    public GradleExecuter startLauncherInDebugger(Action<JavaDebugOptionsInternal> action) {
        debugLauncher.setEnabled(true);
        action.execute(debugLauncher);
        return this;
    }

    @Override
    public boolean isDebugLauncher() {
        return debugLauncher.isEnabled();
    }

    @Override
    public GradleExecuter withProfiler(String args) {
        profiler = args;
        return this;
    }

    @Override
    public GradleExecuter withForceInteractive(boolean flag) {
        interactive = flag;
        return this;
    }

    @Override
    public GradleExecuter withNoExplicitNativeServicesDir() {
        noExplicitNativeServicesDir = true;
        return this;
    }

    @Override
    public GradleExecuter withFullDeprecationStackTraceEnabled() {
        fullDeprecationStackTrace = true;
        return this;
    }

    @Override
    public GradleExecuter withFileLeakDetection(String... args) {
        return withFileLeakDetection(checkNotNull(Jvm.current().getJavaVersion()), args);
    }

    @Override
    public GradleExecuter withFileLeakDetection(JavaVersion javaVersion, String... args) {
        String leakDetectionVersion = javaVersion.isCompatibleWith(JavaVersion.VERSION_11) ? "1.17" : "1.14";
        String leakDetectionJar = String.format("file-leak-detector-%s-jar-with-dependencies.jar", leakDetectionVersion);
        String leakDetectorUrl = String.format("https://repo.jenkins-ci.org/releases/org/kohsuke/file-leak-detector/%s/%s", leakDetectionVersion, leakDetectionJar);
        this.beforeExecute(executer -> {
            File leakDetectorJar = new File(this.gradleUserHomeDir, leakDetectionJar);
            if (!leakDetectorJar.exists()) {
                // Need to download the jar
                GFileUtils.parentMkdirs(leakDetectorJar);
                GFileUtils.touch(leakDetectorJar);
                try (OutputStream out = Files.newOutputStream(leakDetectorJar.toPath());
                     InputStream in = new URL(leakDetectorUrl).openStream()) {
                    ByteStreams.copy(in, out);
                } catch (IOException e) {
                    throw new RuntimeException("Couldn't download " + leakDetectorUrl, e);
                }
            }

            String joinedArgs;
            if (args.length == 0) {
                // Default arguments to pass to the java agent
                joinedArgs = "http=19999";
            } else {
                joinedArgs = Joiner.on(',').join(args);
            }
            withBuildJvmOpts("-javaagent:" + leakDetectorJar + "=" + joinedArgs);
        });

        return this;
    }

    @Override
    public boolean isDebug() {
        return debug.isEnabled();
    }

    @Override
    public boolean isProfile() {
        return !profiler.isEmpty();
    }

    protected static class GradleInvocation {
        final Map<String, String> environmentVars = new HashMap<>();
        final List<String> args = new ArrayList<>();
        // JVM args that must be used for the build JVM
        final List<String> buildJvmArgs = new ArrayList<>();
        // JVM args that must be used to fork a JVM
        final List<String> launcherJvmArgs = new ArrayList<>();
        // Implicit JVM args that should be used to fork a JVM
        final List<String> implicitLauncherJvmArgs = new ArrayList<>();

        protected Map<String, String> getEnvironmentVars() {
            return environmentVars;
        }

        protected List<String> getArgs() {
            return args;
        }

        protected List<String> getBuildJvmArgs() {
            return buildJvmArgs;
        }

        protected List<String> getLauncherJvmArgs() {
            return launcherJvmArgs;
        }

        protected List<String> getImplicitLauncherJvmArgs() {
            return implicitLauncherJvmArgs;
        }
    }

    @Override
    public void stop() {
        cleanup();
    }

    @Override
    public GradleExecuter withDurationMeasurement(DurationMeasurement durationMeasurement) {
        this.durationMeasurement = durationMeasurement;
        return this;
    }

    protected void startMeasurement() {
        if (durationMeasurement != null) {
            durationMeasurement.start();
        }
    }

    protected void stopMeasurement() {
        if (durationMeasurement != null) {
            durationMeasurement.stop();
        }
    }

    protected DurationMeasurement getDurationMeasurement() {
        return durationMeasurement;
    }

    private static ServiceRegistry newCommandLineProcessLogging() {
        ServiceRegistry loggingServices = LoggingServiceRegistry.newEmbeddableLogging();
        LoggingManagerInternal rootLoggingManager = loggingServices.get(DefaultLoggingManagerFactory.class).getRoot();
        rootLoggingManager.attachSystemOutAndErr();
        return loggingServices;
    }

    @Override
    public GradleExecuter withTestConsoleAttached() {
        return withTestConsoleAttached(ConsoleAttachment.ATTACHED);
    }

    @Override
    public GradleExecuter withTestConsoleAttached(ConsoleAttachment consoleAttachment) {
        this.consoleAttachment = consoleAttachment;
        return configureConsoleCommandLineArgs();
    }

    protected GradleExecuter configureConsoleCommandLineArgs() {
        if (consoleAttachment == ConsoleAttachment.NOT_ATTACHED) {
            return this;
        } else {
            return withCommandLineGradleOpts(consoleAttachment.getConsoleMetaData().getCommandLineArgument());
        }
    }

    private boolean errorsShouldAppearOnStdout() {
        // If stdout and stderr are attached to the console
        return consoleAttachment.isStderrAttached() && consoleAttachment.isStdoutAttached();
    }

    private class ResultCollectingHandle implements GradleHandle {
        private final GradleHandle delegate;

        public ResultCollectingHandle(GradleHandle delegate) {
            this.delegate = delegate;
        }

        @Override
        public PipedOutputStream getStdinPipe() {
            return delegate.getStdinPipe();
        }

        @Override
        public String getStandardOutput() {
            return delegate.getStandardOutput();
        }

        @Override
        public String getErrorOutput() {
            return delegate.getErrorOutput();
        }

        @Override
        public GradleHandle abort() {
            return delegate.abort();
        }

        @Override
        public GradleHandle cancel() {
            return delegate.cancel();
        }

        @Override
        public GradleHandle cancelWithEOT() {
            return delegate.cancelWithEOT();
        }

        @Override
        public ExecutionResult waitForFinish() {
            ExecutionResult result = delegate.waitForFinish();
            results.add(result);
            return result;
        }

        @Override
        public ExecutionFailure waitForFailure() {
            ExecutionFailure failure = delegate.waitForFailure();
            results.add(failure);
            return failure;
        }

        @Override
        public void waitForExit() {
            delegate.waitForExit();
        }

        @Override
        public boolean isRunning() {
            return delegate.isRunning();
        }
    }
}
