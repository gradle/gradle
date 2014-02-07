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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.initialization.DefaultClassLoaderScope;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.jvm.Jvm;
import org.gradle.launcher.daemon.configuration.GradleProperties;
import org.gradle.listener.ActionBroadcast;
import org.gradle.process.internal.JvmOptions;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.TextUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;

import static java.util.Arrays.asList;
import static org.gradle.util.Matchers.containsLine;
import static org.gradle.util.Matchers.matchesRegexp;

public abstract class AbstractGradleExecuter implements GradleExecuter {

    private final Logger logger;

    protected final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext();

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
    private TestFile gradleUserHomeDir = buildContext.getGradleUserHomeDir();
    private File userHomeDir;
    private File javaHome;
    private File buildScript;
    private File projectDir;
    private File settingsFile;
    private InputStream stdin;
    private String defaultCharacterEncoding;
    private int daemonIdleTimeoutSecs = 60;
    private File daemonBaseDir = buildContext.getDaemonBaseDir();
    private final List<String> gradleOpts = new ArrayList<String>();
    private boolean noDefaultJvmArgs;
    private boolean requireGradleHome;

    private boolean deprecationChecksOn = true;
    private boolean eagerClassLoaderCreationChecksOn = true;
    private boolean stackTraceChecksOn = true;

    private final ActionBroadcast<GradleExecuter> beforeExecute = new ActionBroadcast<GradleExecuter>();
    private final Set<Action<? super GradleExecuter>> afterExecute = new LinkedHashSet<Action<? super GradleExecuter>>();

    private final TestDirectoryProvider testDirectoryProvider;
    private final GradleDistribution distribution;

    protected AbstractGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        this.distribution = distribution;
        this.testDirectoryProvider = testDirectoryProvider;
        logger = Logging.getLogger(getClass());
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
        stdin = null;
        defaultCharacterEncoding = null;
        noDefaultJvmArgs = false;
        deprecationChecksOn = true;
        stackTraceChecksOn = true;
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

    public void beforeExecute(Closure action) {
        beforeExecute.add(new ClosureBackedAction<GradleExecuter>(action));
    }

    public void afterExecute(Action<? super GradleExecuter> action) {
        afterExecute.add(action);
    }

    public void afterExecute(Closure action) {
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
        executer.withEnvironmentVars(getMergedEnvironmentVars());
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
        if (stdin != null) {
            executer.withStdIn(stdin);
        }
        if (defaultCharacterEncoding != null) {
            executer.withDefaultCharacterEncoding(defaultCharacterEncoding);
        }
        executer.withGradleOpts(gradleOpts.toArray(new String[gradleOpts.size()]));
        if (noDefaultJvmArgs) {
            executer.withNoDefaultJvmArgs();
        }
        executer.noExtraLogging();

        if (!deprecationChecksOn) {
            executer.withDeprecationChecksDisabled();
        }
        if (!eagerClassLoaderCreationChecksOn) {
            executer.withEagerClassLoaderCreationCheckDisabled();
        }
        if (!stackTraceChecksOn) {
            executer.withStackTraceChecksDisabled();
        }
        if (requireGradleHome) {
            executer.requireGradleHome();
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

    /**
     * Returns the gradle opts set with withGradleOpts() (does not consider any set via withEnvironmentVars())
     */
    protected List<String> getGradleOpts() {
        return gradleOpts;
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

    public GradleExecuter withStdIn(String text) {
        this.stdin = new ByteArrayInputStream(TextUtil.toPlatformLineSeparators(text).getBytes());
        return this;
    }

    public GradleExecuter withStdIn(InputStream stdin) {
        this.stdin = stdin;
        return this;
    }

    public InputStream getStdin() {
        return stdin == null ? new ByteArrayInputStream(new byte[0]) : stdin;
    }

    public GradleExecuter withDefaultCharacterEncoding(String defaultCharacterEncoding) {
        this.defaultCharacterEncoding = defaultCharacterEncoding;
        return this;
    }

    public String getDefaultCharacterEncoding() {
        return defaultCharacterEncoding == null ? Charset.defaultCharset().name() : defaultCharacterEncoding;
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

    /**
     * Returns the effective env vars, having merged in specific settings.
     *
     * For example, GRADLE_OPTS will be anything that was specified via withEnvironmentVars() and withGradleOpts(). JAVA_HOME will also be set according to getJavaHome().
     */
    protected Map<String, String> getMergedEnvironmentVars() {
        Map<String, String> environmentVars = new HashMap<String, String>(getEnvironmentVars());
        environmentVars.put("GRADLE_OPTS", toJvmArgsString(getMergedGradleOpts()));
        if (!environmentVars.containsKey("JAVA_HOME")) {
            environmentVars.put("JAVA_HOME", getJavaHome().getAbsolutePath());
        }
        return environmentVars;
    }

    protected String toJvmArgsString(Iterable<String> jvmArgs) {
        StringBuilder result = new StringBuilder();
        for (String jvmArg : jvmArgs) {
            if (result.length() > 0) {
                result.append(" ");
            }
            if (jvmArg.contains(" ")) {
                assert !jvmArg.contains("\"");
                result.append('"');
                result.append(jvmArg);
                result.append('"');
            } else {
                result.append(jvmArg);
            }
        }

        return result.toString();
    }

    private List<String> getMergedGradleOpts() {
        List<String> gradleOpts = new ArrayList<String>(getGradleOpts());
        String gradleOptsEnv = getEnvironmentVars().get("GRADLE_OPTS");
        if (gradleOptsEnv != null) {
            gradleOpts.addAll(JvmOptions.fromString(gradleOptsEnv));
        }

        return gradleOpts;
    }

    protected Map<String, String> getEnvironmentVars() {
        return environmentVars;
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

    public GradleExecuter withNoDefaultJvmArgs() {
        noDefaultJvmArgs = true;
        return this;
    }

    public GradleExecuter withDaemonBaseDir(File daemonBaseDir) {
        this.daemonBaseDir = daemonBaseDir;
        return this;
    }

    public GradleExecuter requireIsolatedDaemons() {
        return withDaemonBaseDir(testDirectoryProvider.getTestDirectory().file("daemon"));
    }

    protected File getDaemonBaseDir() {
        return daemonBaseDir;
    }

    protected boolean isNoDefaultJvmArgs() {
        return noDefaultJvmArgs;
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

    protected Map<String, String> getImplicitJvmSystemProperties() {
        Map<String, String> properties = new LinkedHashMap<String, String>();

        if (getUserHomeDir() != null) {
            properties.put("user.home", getUserHomeDir().getAbsolutePath());
        }

        properties.put(GradleProperties.IDLE_TIMEOUT_PROPERTY, "" + (daemonIdleTimeoutSecs * 1000));
        properties.put(GradleProperties.DAEMON_BASE_DIR_PROPERTY, daemonBaseDir.getAbsolutePath());
        properties.put(DeprecationLogger.ORG_GRADLE_DEPRECATION_TRACE_PROPERTY_NAME, "true");

        String tmpDirPath = getTmpDir().createDir().getAbsolutePath();
        if (!tmpDirPath.contains(" ") || getDistribution().isSupportsSpacesInGradleAndJavaOpts()) {
            properties.put("java.io.tmpdir", tmpDirPath);
        }

        properties.put("file.encoding", getDefaultCharacterEncoding());

        if (eagerClassLoaderCreationChecksOn) {
            properties.put(DefaultClassLoaderScope.STRICT_MODE_PROPERTY, "true");
        }

        return properties;
    }

    public final GradleHandle start() {
        assert afterExecute.isEmpty() : "afterExecute actions are not implemented for async execution";
        fireBeforeExecute();
        assertCanExecute();
        try {
            return doStart();
        } finally {
            reset();
        }
    }

    public final ExecutionResult run() {
        fireBeforeExecute();
        assertCanExecute();
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
        try {
            return doRunWithFailure();
        } finally {
            finished();
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

    /**
     * {@inheritDoc}
     */
    public AbstractGradleExecuter withGradleOpts(String... gradleOpts) {
        this.gradleOpts.addAll(asList(gradleOpts));
        return this;
    }

    protected Action<ExecutionResult> getResultAssertion() {
        ActionBroadcast<ExecutionResult> assertions = new ActionBroadcast<ExecutionResult>();

        if (stackTraceChecksOn) {
            assertions.add(new Action<ExecutionResult>() {
                public void execute(ExecutionResult executionResult) {
                    assertNoStackTraces(executionResult.getOutput(), "Standard output");

                    String error = executionResult.getError();
                    if (executionResult instanceof ExecutionFailure) {
                        // Axe everything after the expected exception
                        int pos = error.indexOf("* Exception is:" + TextUtil.getPlatformLineSeparator());
                        if (pos >= 0) {
                            error = error.substring(0, pos);
                        }
                    }
                    assertNoStackTraces(error, "Standard error");
                }

                private void assertNoStackTraces(String output, String displayName) {
                    if (containsLine(matchesRegexp("\\s+(at\\s+)?[\\w.$_]+\\([\\w._]+:\\d+\\)")).matches(output)) {
                        throw new AssertionError(String.format("%s contains an unexpected stack trace:%n=====%n%s%n=====%n", displayName, output));
                    }
                }
            });
        }

        if (deprecationChecksOn) {
            assertions.add(new Action<ExecutionResult>() {
                public void execute(ExecutionResult executionResult) {
                    assertNoDeprecationWarnings(executionResult.getOutput(), "Standard output");
                    assertNoDeprecationWarnings(executionResult.getError(), "Standard error");
                }

                private void assertNoDeprecationWarnings(String output, String displayName) {
                    boolean javacWarning = containsLine(matchesRegexp(".*use(s)? or override(s)? a deprecated API\\.")).matches(output);
                    boolean deprecationWarning = containsLine(matchesRegexp(".* deprecated.*")).matches(output);
                    if (deprecationWarning && !javacWarning) {
                        throw new AssertionError(String.format("%s contains a deprecation warning:%n=====%n%s%n=====%n", displayName, output));
                    }
                }
            });
        }

        return assertions;
    }

    public GradleExecuter withDeprecationChecksDisabled() {
        deprecationChecksOn = false;
        // turn off stack traces too
        stackTraceChecksOn = false;
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

    protected TestFile getTmpDir() {
        return new TestFile(getTestDirectoryProvider().getTestDirectory(), "tmp");
    }

    public GradleExecuter noExtraLogging() {
        this.allowExtraLogging = false;
        return this;
    }

    public boolean isAllowExtraLogging() {
        return allowExtraLogging;
    }

    public boolean isRequireGradleHome() {
        return requireGradleHome;
    }

    public GradleExecuter requireGradleHome() {
        this.requireGradleHome = true;
        return this;
    }
}
