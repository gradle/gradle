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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.initialization.DefaultClassLoaderScope;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.logging.services.LoggingServiceRegistry;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.configuration.GradleProperties;
import org.gradle.listener.ActionBroadcast;
import org.gradle.process.internal.streams.SafeStreams;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.gradle.integtests.fixtures.executer.AbstractGradleExecuter.CliDaemonArgument.*;
import static org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult.STACK_TRACE_ELEMENT;
import static org.gradle.internal.service.scopes.DefaultGradleUserHomeScopeServiceRegistry.REUSE_USER_HOME_SERVICES;
import static org.gradle.util.CollectionUtils.collect;
import static org.gradle.util.CollectionUtils.join;

public abstract class AbstractGradleExecuter implements GradleExecuter {
    protected static final ServiceRegistry GLOBAL_SERVICES = ServiceRegistryBuilder.builder()
        .displayName("Global services")
        .parent(LoggingServiceRegistry.newCommandLineProcessLogging())
        .parent(NativeServicesTestFixture.getInstance())
        .provider(new GlobalScopeServices(true))
        .build();
    protected final static Set<String> PROPAGATED_SYSTEM_PROPERTIES = Sets.newHashSet();

    public static void propagateSystemProperty(String name) {
        PROPAGATED_SYSTEM_PROPERTIES.add(name);
    }

    private static final String DEBUG_SYSPROP = "org.gradle.integtest.debug";
    private static final String LAUNCHER_DEBUG_SYSPROP = "org.gradle.integtest.launcher.debug";
    private static final String PROFILE_SYSPROP = "org.gradle.integtest.profile";

    protected static final List<String> DEBUG_ARGS = ImmutableList.of(
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005"
    );

    private final Logger logger;

    protected final IntegrationTestBuildContext buildContext;

    private final Set<File> isolatedDaemonBaseDirs = new HashSet<File>();
    private final List<String> args = new ArrayList<String>();
    private final List<String> tasks = new ArrayList<String>();
    private boolean allowExtraLogging = true;
    private File workingDir;
    private boolean quiet;
    private boolean taskList;
    private boolean dependencyList;
    private boolean searchUpwards;
    private Map<String, String> environmentVars = new HashMap<String, String>();
    private List<File> initScripts = new ArrayList<File>();
    private String executable;
    private TestFile gradleUserHomeDir;
    private File userHomeDir;
    private File javaHome;
    private File buildScript;
    private File projectDir;
    private File settingsFile;
    private PipedOutputStream stdinPipe;
    private String defaultCharacterEncoding;
    private Locale defaultLocale;
    private int daemonIdleTimeoutSecs = 120;
    private boolean requireDaemon;
    private File daemonBaseDir;
    private final List<String> buildJvmOpts = new ArrayList<String>();
    private final List<String> commandLineJvmOpts = new ArrayList<String>();
    private boolean useOnlyRequestedJvmOpts;
    private boolean requiresGradleDistribution;

    private int expectedDeprecationWarnings;
    private boolean eagerClassLoaderCreationChecksOn = true;
    private boolean stackTraceChecksOn = true;

    private final ActionBroadcast<GradleExecuter> beforeExecute = new ActionBroadcast<GradleExecuter>();
    private final Set<Action<? super GradleExecuter>> afterExecute = new LinkedHashSet<Action<? super GradleExecuter>>();

    private final TestDirectoryProvider testDirectoryProvider;
    protected final GradleVersion gradleVersion;
    private final GradleDistribution distribution;

    private boolean debug = Boolean.getBoolean(DEBUG_SYSPROP);
    private boolean debugLauncher = Boolean.getBoolean(LAUNCHER_DEBUG_SYSPROP);
    private String profiler = System.getProperty(PROFILE_SYSPROP, "");

    protected boolean interactive;

    private boolean noExplicitTmpDir;
    protected boolean noExplicitNativeServicesDir;
    private boolean fullDeprecationStackTrace = true;
    private boolean checkDeprecations = true;

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
        logger = Logging.getLogger(getClass());
        this.buildContext = buildContext;
        gradleUserHomeDir = buildContext.getGradleUserHomeDir();
        daemonBaseDir = buildContext.getDaemonBaseDir();
    }

    protected Logger getLogger() {
        return logger;
    }

    public GradleExecuter reset() {
        args.clear();
        tasks.clear();
        initScripts.clear();
        workingDir = null;
        projectDir = null;
        buildScript = null;
        settingsFile = null;
        quiet = false;
        taskList = false;
        dependencyList = false;
        searchUpwards = false;
        executable = null;
        javaHome = null;
        environmentVars.clear();
        stdinPipe = null;
        defaultCharacterEncoding = null;
        defaultLocale = null;
        commandLineJvmOpts.clear();
        buildJvmOpts.clear();
        useOnlyRequestedJvmOpts = false;
        expectedDeprecationWarnings = 0;
        stackTraceChecksOn = true;
        debug = Boolean.getBoolean(DEBUG_SYSPROP);
        debugLauncher = Boolean.getBoolean(LAUNCHER_DEBUG_SYSPROP);
        profiler = System.getProperty(PROFILE_SYSPROP, "");
        interactive = false;
        checkDeprecations = true;
        durationMeasurement = null;
        return this;
    }

    public GradleDistribution getDistribution() {
        return distribution;
    }

    public TestDirectoryProvider getTestDirectoryProvider() {
        return testDirectoryProvider;
    }

    public void beforeExecute(Action<? super GradleExecuter> action) {
        beforeExecute.add(action);
    }

    public void beforeExecute(@DelegatesTo(GradleExecuter.class) Closure action) {
        beforeExecute.add(new ClosureBackedAction<GradleExecuter>(action));
    }

    public void afterExecute(Action<? super GradleExecuter> action) {
        afterExecute.add(action);
    }

    public void afterExecute(@DelegatesTo(GradleExecuter.class) Closure action) {
        afterExecute.add(new ClosureBackedAction<GradleExecuter>(action));
    }

    public GradleExecuter inDirectory(File directory) {
        workingDir = directory;
        return this;
    }

    public File getWorkingDir() {
        return workingDir == null ? getTestDirectoryProvider().getTestDirectory() : workingDir;
    }

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
        if (javaHome != null) {
            executer.withJavaHome(javaHome);
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
        if (noExplicitTmpDir) {
            executer.withNoExplicitTmpDir();
        }
        if (noExplicitNativeServicesDir) {
            executer.withNoExplicitNativeServicesDir();
        }
        if (!fullDeprecationStackTrace) {
            executer.withFullDeprecationStackTraceDisabled();
        }
        if (defaultLocale != null) {
            executer.withDefaultLocale(defaultLocale);
        }
        executer.withCommandLineGradleOpts(commandLineJvmOpts);
        executer.withBuildJvmOpts(buildJvmOpts);
        if (useOnlyRequestedJvmOpts) {
            executer.useDefaultBuildJvmArgs();
        }
        executer.noExtraLogging();

        for (int i = 0; i < expectedDeprecationWarnings; i++) {
            executer.expectDeprecationWarning();
        }
        if (!eagerClassLoaderCreationChecksOn) {
            executer.withEagerClassLoaderCreationCheckDisabled();
        }
        if (!stackTraceChecksOn) {
            executer.withStackTraceChecksDisabled();
        }
        if (requiresGradleDistribution) {
            executer.requireGradleDistribution();
        }
        if (requireDaemon) {
            executer.requireDaemon();
        }

        executer.startBuildProcessInDebugger(debug);
        executer.startLauncherInDebugger(debugLauncher);
        executer.withProfiler(profiler);
        executer.withForceInteractive(interactive);

        if (!checkDeprecations) {
            executer.noDeprecationChecks();
        }

        if (durationMeasurement != null) {
            executer.withDurationMeasurement(durationMeasurement);
        }

        return executer;
    }

    public GradleExecuter usingBuildScript(File buildScript) {
        this.buildScript = buildScript;
        return this;
    }

    public GradleExecuter usingProjectDirectory(File projectDir) {
        this.projectDir = projectDir;
        return this;
    }

    public GradleExecuter usingSettingsFile(File settingsFile) {
        this.settingsFile = settingsFile;
        return this;
    }

    public GradleExecuter usingInitScript(File initScript) {
        initScripts.add(initScript);
        return this;
    }

    public TestFile getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public GradleExecuter withGradleUserHomeDir(File userHomeDir) {
        this.gradleUserHomeDir = userHomeDir == null ? null : new TestFile(userHomeDir);
        return this;
    }

    public GradleExecuter requireOwnGradleUserHomeDir() {
        return withGradleUserHomeDir(testDirectoryProvider.getTestDirectory().file("user-home"));
    }

    public File getUserHomeDir() {
        return userHomeDir;
    }

    protected GradleInvocation buildInvocation() {
        validateDaemonVisibility();

        GradleInvocation gradleInvocation = new GradleInvocation();
        gradleInvocation.environmentVars.putAll(environmentVars);
        gradleInvocation.buildJvmArgs.addAll(buildJvmOpts);
        if (!useOnlyRequestedJvmOpts) {
            gradleInvocation.buildJvmArgs.addAll(getImplicitBuildJvmArgs());
        }
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
            String quotedArgs = join(" ", collect(gradleInvocation.buildJvmArgs, new Transformer<String, String>() {
                public String transform(String input) {
                    return String.format("'%s'", input);
                }
            }));
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
            gradleInvocation.implicitLauncherJvmArgs.addAll(DEBUG_ARGS);
        }
        gradleInvocation.implicitLauncherJvmArgs.add("-ea");
    }

    /**
     * Returns additional JVM args that should be used to start the build JVM.
     */
    protected List<String> getImplicitBuildJvmArgs() {
        List<String> buildJvmOpts = new ArrayList<String>();
        buildJvmOpts.add("-ea");

        if (isDebug()) {
            buildJvmOpts.addAll(DEBUG_ARGS);
        }
        if (isProfile()) {
            buildJvmOpts.add(profiler);
        }
        return buildJvmOpts;
    }

    public GradleExecuter withUserHomeDir(File userHomeDir) {
        this.userHomeDir = userHomeDir;
        return this;
    }

    public File getJavaHome() {
        return javaHome == null ? Jvm.current().getJavaHome() : javaHome;
    }

    public GradleExecuter withJavaHome(File javaHome) {
        this.javaHome = javaHome;
        return this;
    }

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

    public GradleExecuter withDefaultCharacterEncoding(String defaultCharacterEncoding) {
        this.defaultCharacterEncoding = defaultCharacterEncoding;
        return this;
    }

    public String getDefaultCharacterEncoding() {
        return defaultCharacterEncoding == null ? Charset.defaultCharset().name() : defaultCharacterEncoding;
    }

    public GradleExecuter withDefaultLocale(Locale defaultLocale) {
        this.defaultLocale = defaultLocale;
        return this;
    }

    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    public GradleExecuter withSearchUpwards() {
        searchUpwards = true;
        return this;
    }

    public boolean isQuiet() {
        return quiet;
    }

    public GradleExecuter withQuietLogging() {
        quiet = true;
        return this;
    }

    public GradleExecuter withTaskList() {
        taskList = true;
        return this;
    }

    public GradleExecuter withDependencyList() {
        dependencyList = true;
        return this;
    }

    public GradleExecuter withArguments(String... args) {
        return withArguments(Arrays.asList(args));
    }

    public GradleExecuter withArguments(List<String> args) {
        this.args.clear();
        this.args.addAll(args);
        return this;
    }

    public GradleExecuter withArgument(String arg) {
        this.args.add(arg);
        return this;
    }

    public GradleExecuter withEnvironmentVars(Map<String, ?> environment) {
        environmentVars.clear();
        for (Map.Entry<String, ?> entry : environment.entrySet()) {
            environmentVars.put(entry.getKey(), entry.getValue().toString());
        }
        return this;
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

    public GradleExecuter withTasks(String... names) {
        return withTasks(Arrays.asList(names));
    }

    public GradleExecuter withTasks(List<String> names) {
        tasks.clear();
        tasks.addAll(names);
        return this;
    }

    public GradleExecuter withDaemonIdleTimeoutSecs(int secs) {
        daemonIdleTimeoutSecs = secs;
        return this;
    }

    public GradleExecuter useDefaultBuildJvmArgs() {
        useOnlyRequestedJvmOpts = true;
        return this;
    }

    public GradleExecuter withDaemonBaseDir(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir;
        return this;
    }

    public GradleExecuter requireIsolatedDaemons() {
        return withDaemonBaseDir(testDirectoryProvider.getTestDirectory().file("daemon"));
    }

    public boolean usesSharedDaemons() {
        return isSharedDaemons();
    }

    public File getDaemonBaseDir() {
        return daemonBaseDir;
    }

    @Override
    public GradleExecuter requireDaemon() {
        requireDaemon = true;
        return this;
    }

    protected boolean isSharedDaemons() {
        return daemonBaseDir.equals(buildContext.getDaemonBaseDir());
    }

    public boolean isUseDaemon() {
        CliDaemonArgument cliDaemonArgument = resolveCliDaemonArgument();
        if (cliDaemonArgument == DAEMON) {
            return true;
        }
        return requireDaemon;
    }

    /**
     * Performs cleanup at completion of the test.
     */
    public void cleanup() {
        cleanupIsolatedDaemons();
    }

    protected void cleanupIsolatedDaemons() {
        for (File baseDir : isolatedDaemonBaseDirs) {
            try {
                new DaemonLogsAnalyzer(baseDir, gradleVersion.getVersion()).killAll();
            } catch (Exception e) {
                getLogger().warn("Problem killing isolated daemons of Gradle version " + gradleVersion + " in " + baseDir, e);
            }
        }
    }

    enum CliDaemonArgument {
        NOT_DEFINED,
        DAEMON,
        NO_DAEMON,
        FOREGROUND
    }

    private CliDaemonArgument resolveCliDaemonArgument() {
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
        List<String> allArgs = new ArrayList<String>();
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
        allArgs.add("--stacktrace");
        if (taskList) {
            allArgs.add("tasks");
        }
        if (dependencyList) {
            allArgs.add("dependencies");
        }

        if (!searchUpwards) {
            boolean settingsFoundAboveInTestDir = false;
            TestFile dir = new TestFile(getWorkingDir());
            while (dir != null && getTestDirectoryProvider().getTestDirectory().isSelfOrDescendent(dir)) {
                if (dir.file("settings.gradle").isFile()) {
                    settingsFoundAboveInTestDir = true;
                    break;
                }
                dir = dir.getParentFile();
            }

            if (!settingsFoundAboveInTestDir) {
                allArgs.add("--no-search-upward");
            }
        }

        // This will cause problems on Windows if the path to the Gradle executable that is used has a space in it (e.g. the user's dir is c:/Users/Luke Daley/)
        // This is fundamentally a windows issue: You can't have arguments with spaces in them if the path to the batch script has a space
        // We could work around this by setting -Dgradle.user.home but GRADLE-1730 (which affects 1.0-milestone-3) means that that
        // is problematic as well. For now, we just don't support running the int tests from a path with a space in it on Windows.
        // When we stop testing against M3 we should change to use the system property.
        if (getGradleUserHomeDir() != null) {
            allArgs.add("--gradle-user-home");
            allArgs.add(getGradleUserHomeDir().getAbsolutePath());
        }

        allArgs.addAll(args);
        allArgs.addAll(tasks);
        return allArgs;
    }

    /**
     * Returns the set of system properties that should be set on every JVM used by this executer.
     */
    protected Map<String, String> getImplicitJvmSystemProperties() {
        Map<String, String> properties = new LinkedHashMap<String, String>();

        if (getUserHomeDir() != null) {
            properties.put("user.home", getUserHomeDir().getAbsolutePath());
        }

        properties.put(GradleProperties.IDLE_TIMEOUT_PROPERTY, "" + (daemonIdleTimeoutSecs * 1000));
        properties.put(GradleProperties.DAEMON_BASE_DIR_PROPERTY, daemonBaseDir.getAbsolutePath());
        if (!noExplicitNativeServicesDir) {
            properties.put(NativeServices.NATIVE_DIR_OVERRIDE, buildContext.getNativeServicesDir().getAbsolutePath());
        }
        properties.put(LoggingDeprecatedFeatureHandler.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME, Boolean.toString(fullDeprecationStackTrace));

        if (gradleUserHomeDir != null && !gradleUserHomeDir.equals(buildContext.getGradleUserHomeDir())) {
            properties.put(REUSE_USER_HOME_SERVICES, "false");
        }
        if (!noExplicitTmpDir) {
            if (tmpDir == null) {
                tmpDir = getDefaultTmpDir();
            }
            String tmpDirPath = tmpDir.createDir().getAbsolutePath();
            if (!tmpDirPath.contains(" ") || (getDistribution().isSupportsSpacesInGradleAndJavaOpts() && supportsWhiteSpaceInEnvVars())) {
                properties.put("java.io.tmpdir", tmpDirPath);
            }
        }

        properties.put("file.encoding", getDefaultCharacterEncoding());
        Locale locale = getDefaultLocale();
        if (locale != null) {
            properties.put("user.language", locale.getLanguage());
            properties.put("user.country", locale.getCountry());
            properties.put("user.variant", locale.getVariant());
        }

        if (eagerClassLoaderCreationChecksOn) {
            properties.put(DefaultClassLoaderScope.STRICT_MODE_PROPERTY, "true");
        }

        if (interactive) {
            properties.put(DaemonParameters.INTERACTIVE_TOGGLE, "true");
        }

        return properties;
    }

    protected boolean supportsWhiteSpaceInEnvVars() {
        return true;
    }

    public final GradleHandle start() {
        assert afterExecute.isEmpty() : "afterExecute actions are not implemented for async execution";
        fireBeforeExecute();
        assertCanExecute();
        collectStateBeforeExecution();
        try {
            return doStart();
        } finally {
            reset();
        }
    }

    public final ExecutionResult run() {
        fireBeforeExecute();
        assertCanExecute();
        collectStateBeforeExecution();
        try {
            return doRun();
        } finally {
            finished();
        }
    }

    private void finished() {
        try {
            new ActionBroadcast<GradleExecuter>(afterExecute).execute(this);
        } finally {
            reset();
        }
    }

    public final ExecutionFailure runWithFailure() {
        fireBeforeExecute();
        assertCanExecute();
        collectStateBeforeExecution();
        try {
            return doRunWithFailure();
        } finally {
            finished();
        }
    }

    private void collectStateBeforeExecution() {
        if (!isSharedDaemons()) {
            isolatedDaemonBaseDirs.add(daemonBaseDir);
        }
    }

    private void fireBeforeExecute() {
        beforeExecute.execute(this);
    }

    protected GradleHandle doStart() {
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
        return withArgument("-Dorg.gradle.cache.tasks=true");
    }

    @Override
    public GradleExecuter withLocalBuildCache(File cacheDir) {
        return withBuildCacheEnabled().withArgument("-Dorg.gradle.cache.tasks.directory=" + cacheDir.getAbsolutePath());
    }

    protected Action<ExecutionResult> getResultAssertion() {
        return new Action<ExecutionResult>() {
            int expectedDeprecationWarnings = AbstractGradleExecuter.this.expectedDeprecationWarnings;
            boolean expectStackTraces = !AbstractGradleExecuter.this.stackTraceChecksOn;
            boolean checkDeprecations = AbstractGradleExecuter.this.checkDeprecations;

            @Override
            public void execute(ExecutionResult executionResult) {
                validate(executionResult.getNormalizedOutput(), "Standard output");
                String error = executionResult.getError();
                if (executionResult instanceof ExecutionFailure) {
                    // Axe everything after the expected exception
                    int pos = error.indexOf("* Exception is:\n");
                    if (pos >= 0) {
                        error = error.substring(0, pos);
                    }
                }
                validate(error, "Standard error");
            }

            private void validate(String output, String displayName) {
                List<String> lines;
                try {
                    lines = CharSource.wrap(output).readLines();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                int i = 0;
                while (i < lines.size()) {
                    String line = lines.get(i);
                    if (line.matches(".*use(s)? or override(s)? a deprecated API\\.")) {
                        // A javac warning, ignore
                        i++;
                    } else if (line.matches(".*\\s+deprecated.*")) {
                        if (checkDeprecations && expectedDeprecationWarnings <= 0) {
                            throw new AssertionError(String.format("%s line %d contains a deprecation warning: %s%n=====%n%s%n=====%n", displayName, i+1, line, output));
                        }
                        expectedDeprecationWarnings--;
                        // skip over stack trace
                        i++;
                        while (i < lines.size() && STACK_TRACE_ELEMENT.matcher(lines.get(i)).matches()) {
                            i++;
                        }
                    } else if (!expectStackTraces && STACK_TRACE_ELEMENT.matcher(line).matches() && i < lines.size()-1 && STACK_TRACE_ELEMENT.matcher(lines.get(i+1)).matches()) {
                        // 2 or more lines that look like stack trace elements
                        throw new AssertionError(String.format("%s line %d contains an unexpected stack trace: %s%n=====%n%s%n=====%n", displayName, i+1, line, output));
                    } else {
                        i++;
                    }
                }
            }
        };
    }

    public GradleExecuter expectDeprecationWarning() {
        expectedDeprecationWarnings++;
        return this;
    }
    public GradleExecuter noDeprecationChecks() {
        checkDeprecations = false;
        return this;
    }

    public GradleExecuter withEagerClassLoaderCreationCheckDisabled() {
        eagerClassLoaderCreationChecksOn = false;
        return this;
    }

    public GradleExecuter withStackTraceChecksDisabled() {
        stackTraceChecksOn = false;
        return this;
    }

    protected TestFile getDefaultTmpDir() {
        return buildContext.getTmpDir().createDir();
    }

    public GradleExecuter noExtraLogging() {
        this.allowExtraLogging = false;
        return this;
    }

    public boolean isAllowExtraLogging() {
        return allowExtraLogging;
    }

    public boolean isRequiresGradleDistribution() {
        return requiresGradleDistribution;
    }

    public GradleExecuter requireGradleDistribution() {
        this.requiresGradleDistribution = true;
        return this;
    }

    @Override
    public GradleExecuter startBuildProcessInDebugger(boolean flag) {
        debug = flag;
        return this;
    }

    @Override
    public GradleExecuter startLauncherInDebugger(boolean flag) {
        debugLauncher = flag;
        return this;
    }

    @Override
    public boolean isDebugLauncher() {
        return debugLauncher;
    }

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
    public GradleExecuter withNoExplicitTmpDir() {
        noExplicitTmpDir = true;
        return this;
    }

    @Override
    public GradleExecuter withNoExplicitNativeServicesDir() {
        noExplicitNativeServicesDir = true;
        return this;
    }

    public GradleExecuter withFullDeprecationStackTraceDisabled() {
        fullDeprecationStackTrace = false;
        return this;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    public boolean isProfile() {
        return !profiler.isEmpty();
    }

    protected static class GradleInvocation {
        final Map<String, String> environmentVars = new HashMap<String, String>();
        final List<String> args = new ArrayList<String>();
        // JVM args that must be used for the build JVM
        final List<String> buildJvmArgs = new ArrayList<String>();
        // JVM args that must be used to fork a JVM
        final List<String> launcherJvmArgs = new ArrayList<String>();
        // Implicit JVM args that should be used to fork a JVM
        final List<String> implicitLauncherJvmArgs = new ArrayList<String>();
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
}
