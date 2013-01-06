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
import org.gradle.internal.jvm.Jvm;
import org.gradle.listener.ActionBroadcast;
import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.TextUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.*;

import static java.util.Arrays.asList;
import static org.gradle.util.Matchers.*;

public abstract class AbstractGradleExecuter implements GradleExecuter {

    protected final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext();

    private final List<String> args = new ArrayList<String>();
    private final List<String> tasks = new ArrayList<String>();
    protected boolean allowExtraLogging = true;
    private File workingDir;
    private boolean quiet;
    private boolean taskList;
    private boolean dependencyList;
    private boolean searchUpwards;
    private Map<String, String> environmentVars = new HashMap<String, String>();
    private List<File> initScripts = new ArrayList<File>();
    private String executable;
    private File gradleUserHomeDir = buildContext.getGradleUserHomeDir();
    private File userHomeDir;
    private File javaHome;
    private File buildScript;
    private File projectDir;
    private File settingsFile;
    private InputStream stdin;
    private String defaultCharacterEncoding;
    private Integer daemonIdleTimeoutSecs = 10;
    private File daemonBaseDir = buildContext.getDaemonBaseDir();
    //gradle opts make sense only for forking executer but having them here makes more sense
    protected final List<String> gradleOpts = new ArrayList<String>();
    protected boolean noDefaultJvmArgs;
    private boolean requireGradleHome;

    private boolean deprecationChecksOn = true;
    private boolean stackTraceChecksOn = true;

    private final ActionBroadcast<GradleExecuter> beforeExecute = new ActionBroadcast<GradleExecuter>();

    private final TestDirectoryProvider testDirectoryProvider;

    protected AbstractGradleExecuter(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider;
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
        allowExtraLogging = true;
        return this;
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

    public GradleExecuter inDirectory(File directory) {
        workingDir = directory;
        return this;
    }

    public File getWorkingDir() {
        return workingDir == null ? getTestDirectoryProvider().getTestDirectory() : workingDir;
    }

    protected void copyTo(GradleExecuter executer) {
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
        executer.withEnvironmentVars(getAllEnvironmentVars());
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
        executer.setAllowExtraLogging(allowExtraLogging);

        if (!deprecationChecksOn) {
            executer.withDeprecationChecksDisabled();
        }
        if (!stackTraceChecksOn) {
            executer.withStackTraceChecksDisabled();
        }
        executer.requireGradleHome(isRequireGradleHome());
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

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public GradleExecuter withGradleUserHomeDir(File userHomeDir) {
        this.gradleUserHomeDir = userHomeDir;
        return this;
    }

    public GradleExecuter requireOwnGradleUserHomeDir() {
        return withGradleUserHomeDir(testDirectoryProvider.getTestDirectory().file("user-home"));
    }

    public File getUserHomeDir() {
        return userHomeDir;
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

    protected Map<String, String> getAllEnvironmentVars() {
        return environmentVars;
    }

    public Map<String, String> getEnvironmentVars() {
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

        if (gradleUserHomeDir != null) {
            allArgs.add("--gradle-user-home");
            allArgs.add(gradleUserHomeDir.getAbsolutePath());
        }

        allArgs.add("-Dorg.gradle.daemon.idletimeout=" + daemonIdleTimeoutSecs * 1000);
        allArgs.add("-Dorg.gradle.daemon.registry.base=" + daemonBaseDir.getAbsolutePath());

        TestFile tmpDir = getTmpDir();
        tmpDir.createDir();
        allArgs.add(String.format("-Djava.io.tmpdir=%s", tmpDir));

        allArgs.addAll(args);
        allArgs.addAll(tasks);
        return allArgs;
    }

    public final GradleHandle start() {
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
            return checkResult(doRun());
        } finally {
            reset();
        }
    }

    public final ExecutionFailure runWithFailure() {
        fireBeforeExecute();
        assertCanExecute();
        try {
            return checkResult(doRunWithFailure());
        } finally {
            reset();
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
    public AbstractGradleExecuter withGradleOpts(String ... gradleOpts) {
        this.gradleOpts.addAll(asList(gradleOpts));
        return this;
    }

    protected <T extends ExecutionResult> T checkResult(T result) {
        if (stackTraceChecksOn) {
            // Assert that nothing unexpected was logged
            assertOutputHasNoStackTraces(result);
            assertErrorHasNoStackTraces(result);
        }
        if (deprecationChecksOn) {
            assertOutputHasNoDeprecationWarnings(result);
        }

        if (getExecutable() == null) {
            // Assert that no temp files are left lying around
            // Note: don't do this if a custom executable is used, as we don't know (and probably don't care) whether the executable cleans up or not
            TestFile[] testFiles = getTmpDir().listFiles();
            if (testFiles != null) {
                List<String> unexpectedFiles = new ArrayList<String>();
                for (File file : testFiles) {
                    if (!file.getName().matches("maven-artifact\\d+.tmp")) {
                        unexpectedFiles.add(file.getName());
                    }
                }
//            Assert.assertThat(unexpectedFiles, Matchers.isEmpty());
            }
        }

        return result;
    }

    private void assertOutputHasNoStackTraces(ExecutionResult result) {
        assertNoStackTraces(result.getOutput(), "Standard output");
    }

    public void assertErrorHasNoStackTraces(ExecutionResult result) {
        String error = result.getError();
        if (result instanceof ExecutionFailure) {
            // Axe everything after the expected exception
            int pos = error.indexOf("* Exception is:" + TextUtil.getPlatformLineSeparator());
            if (pos >= 0) {
                error = error.substring(0, pos);
            }
        }
        assertNoStackTraces(error, "Standard error");
    }

    public void assertOutputHasNoDeprecationWarnings(ExecutionResult result) {
        assertNoDeprecationWarnings(result.getOutput(), "Standard output");
        assertNoDeprecationWarnings(result.getError(), "Standard error");
    }

    private void assertNoDeprecationWarnings(String output, String displayName) {
        boolean javacWarning = containsLine(matchesRegexp(".*use(s)? or override(s)? a deprecated API\\.")).matches(output);
        boolean deprecationWarning = containsLine(matchesRegexp(".* deprecated.*")).matches(output);
        if (deprecationWarning && !javacWarning) {
            throw new AssertionError(String.format("%s contains a deprecation warning:%n=====%n%s%n=====%n", displayName, output));
        }
    }

    private void assertNoStackTraces(String output, String displayName) {
        if (containsLine(matchesRegexp("\\s+(at\\s+)?[\\w.$_]+\\([\\w._]+:\\d+\\)")).matches(output)) {
            throw new AssertionError(String.format("%s contains an unexpected stack trace:%n=====%n%s%n=====%n", displayName, output));
        }
    }

    public GradleExecuter withDeprecationChecksDisabled() {
        deprecationChecksOn = false;
        // turn off stack traces too
        stackTraceChecksOn = false;
        return this;
    }

    public GradleExecuter withStackTraceChecksDisabled() {
        stackTraceChecksOn = false;
        return this;
    }

    private TestFile getTmpDir() {
        return new TestFile(getTestDirectoryProvider().getTestDirectory(), "tmp");
    }

    /**
     * set true to allow the executer to increase the log level if necessary
     * to help out debugging. Set false to make the executer never update the log level.
     */
    public GradleExecuter setAllowExtraLogging(boolean allowExtraLogging) {
        this.allowExtraLogging = allowExtraLogging;
        return this;
    }

    public boolean isAllowExtraLogging() {
        return allowExtraLogging;
    }

    public boolean isRequireGradleHome() {
        return requireGradleHome;
    }

    public GradleExecuter requireGradleHome(boolean requireGradleHome) {
        this.requireGradleHome = requireGradleHome;
        return this;
    }
}
