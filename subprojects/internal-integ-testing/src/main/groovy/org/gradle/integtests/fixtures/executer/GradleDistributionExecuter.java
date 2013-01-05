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

import org.gradle.test.fixtures.file.TestDirectoryProvider;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.TextUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.Matchers.containsLine;
import static org.gradle.util.Matchers.matchesRegexp;

/**
 * A JUnit rule which provides a {@link GradleExecuter} implementation that executes Gradle using a given {@link
 * GradleDistribution}. If not supplied in the constructor, this rule locates a field on the test object with type
 * {@link GradleDistribution}.
 *
 * By default, this executer will execute Gradle in a forked process. There is a system property which enables executing
 * Gradle in the current process.
 */
public class GradleDistributionExecuter extends AbstractDelegatingGradleExecuter {

    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer";
    private static final String UNKNOWN_OS_SYS_PROP = "org.gradle.integtest.unknownos";
    private static final int DEFAULT_DAEMON_IDLE_TIMEOUT_SECS = 2 * 60;

    private TestDirectoryProvider testWorkDirProvider;
    private BasicGradleDistribution dist;

    private boolean workingDirSet;
    private boolean gradleUserHomeDirSet;
    private boolean deprecationChecksOn = true;
    private boolean stackTraceChecksOn = true;
    private Executer executerType;

    private boolean allowExtraLogging = true;
    private boolean usingIsolatedDaemons;

    private IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext();

    public enum Executer {
        embedded(false),
        forking(true),
        daemon(true),
        embeddedDaemon(false),
        parallel(true, true);

        final public boolean forks;
        final public boolean executeParallel;

        Executer(boolean forks) {
            this(forks, false);
        }

        Executer(boolean forks, boolean parallel) {
            this.forks = forks;
            this.executeParallel = parallel;
        }
    }

    public static Executer getSystemPropertyExecuter() {
        return Executer.valueOf(System.getProperty(EXECUTER_SYS_PROP, Executer.forking.toString()));
    }

    public GradleDistributionExecuter(GradleDistribution dist, TestDirectoryProvider testWorkDirProvider) {
        this(getSystemPropertyExecuter(), dist, testWorkDirProvider);
    }

    private GradleDistributionExecuter(Executer executerType, GradleDistribution dist, TestDirectoryProvider testWorkDirProvider) {
        this.executerType = executerType;
        this.dist = dist;
        this.testWorkDirProvider = testWorkDirProvider;
    }

    // Methods specific to this impl

    public void requireIsolatedDaemons() {
        this.usingIsolatedDaemons = true;
    }

    public TestFile getDefaultDaemonBaseDir() {
        if (usingIsolatedDaemons) {
            return testWorkDirProvider.getTestDirectory().file("daemon");
        } else {
            return buildContext.getDaemonBaseDir();
        }
    }

    public GradleDistributionExecuter requireOwnGradleUserHomeDir() {
        return withGradleUserHomeDir(testWorkDirProvider.getTestDirectory().file("user-home"));
    }

    @Override
    public GradleDistributionExecuter reset() {
        super.reset();
        workingDirSet = false;
        deprecationChecksOn = true;
        stackTraceChecksOn = true;
        DeprecationLogger.reset();
        return this;
    }

    @Override
    public GradleDistributionExecuter inDirectory(File directory) {
        super.inDirectory(directory);
        workingDirSet = true;
        return this;
    }

    @Override
    public GradleDistributionExecuter withGradleUserHomeDir(File userHomeDir) {
        super.withGradleUserHomeDir(userHomeDir);
        gradleUserHomeDirSet = true;
        return this;
    }

    public GradleDistributionExecuter withDeprecationChecksDisabled() {
        deprecationChecksOn = false;
        // turn off stack traces too
        stackTraceChecksOn = false;
        return this;
    }

    public GradleDistributionExecuter withStackTraceChecksDisabled() {
        stackTraceChecksOn = false;
        return this;
    }

    public Executer getExecuterType() {
        return executerType;
    }

    public void setExecuterType(Executer executerType) {
        this.executerType = executerType;
    }

    public GradleDistributionExecuter withForkingExecuter() {
        if (!executerType.forks) {
            executerType = Executer.forking;
        }
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
            List<String> unexpectedFiles = new ArrayList<String>();
            for (File file : getTmpDir().listFiles()) {
                if (!file.getName().matches("maven-artifact\\d+.tmp")) {
                    unexpectedFiles.add(file.getName());
                }
            }
//            Assert.assertThat(unexpectedFiles, Matchers.isEmpty());
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

    /**
     * set true to allow the executer to increase the log level if necessary
     * to help out debugging. Set false to make the executer never update the log level.
     */
    public GradleDistributionExecuter setAllowExtraLogging(boolean allowExtraLogging) {
        this.allowExtraLogging = allowExtraLogging;
        return this;
    }

    protected GradleExecuter configureExecuter() {
        if (!workingDirSet) {
            inDirectory(testWorkDirProvider.getTestDirectory());
        }
        if (!gradleUserHomeDirSet) {
            withGradleUserHomeDir(buildContext.getGradleUserHomeDir());
        }
        if (getDaemonIdleTimeoutSecs() == null) {
            if (usingIsolatedDaemons || getDaemonBaseDir() != null) {
                withDaemonIdleTimeoutSecs(20);
            } else {
                withDaemonIdleTimeoutSecs(DEFAULT_DAEMON_IDLE_TIMEOUT_SECS);
            }
        }
        if (getDaemonBaseDir() == null) {
            withDaemonBaseDir(getDefaultDaemonBaseDir());
        }

        if (!getClass().desiredAssertionStatus()) {
            throw new RuntimeException("Assertions must be enabled when running integration tests.");
        }

        GradleExecuter gradleExecuter = createExecuter(executerType);
        configureExecuter(gradleExecuter);
        try {
            gradleExecuter.assertCanExecute();
        } catch (AssertionError assertionError) {
            gradleExecuter = new ForkingGradleExecuter(dist.getGradleHomeDir());
            configureExecuter(gradleExecuter);
        }

        return gradleExecuter;
    }

    private void configureExecuter(GradleExecuter gradleExecuter) {
        copyTo(gradleExecuter);

        configureTmpDir(gradleExecuter);
        configureForSettingsFile(gradleExecuter);

        if (System.getProperty(UNKNOWN_OS_SYS_PROP) != null) {
            gradleExecuter.withGradleOpts("-Dos.arch=unknown architecture", "-Dos.name=unknown operating system", "-Dos.version=unknown version");
        }
    }

    private GradleExecuter createExecuter(Executer executerType) {
        switch (executerType) {
            case embeddedDaemon:
                return new EmbeddedDaemonGradleExecuter();
            case embedded:
                return new InProcessGradleExecuter();
            case daemon:
                return new DaemonGradleExecuter(dist.getGradleHomeDir(), !isQuiet() && allowExtraLogging, noDefaultJvmArgs);
            case parallel:
                return new ParallelForkingGradleExecuter(dist.getGradleHomeDir());
            case forking:
                return new ForkingGradleExecuter(dist.getGradleHomeDir());
            default:
                throw new RuntimeException("Not a supported executer type: " + executerType);
        }
    }

    private void configureTmpDir(GradleExecuter gradleExecuter) {
        TestFile tmpDir = getTmpDir();
        tmpDir.createDir();
        gradleExecuter.withGradleOpts(String.format("-Djava.io.tmpdir=%s", tmpDir));
    }

    private void configureForSettingsFile(GradleExecuter gradleExecuter) {
        boolean settingsFound = false;

        TestFile workingDir = new TestFile(getWorkingDir());
        TestFile dir = workingDir;
        while (dir != null && testWorkDirProvider.getTestDirectory().isSelfOrDescendent(dir)) {
            if (dir.file("settings.gradle").isFile()) {
                settingsFound = true;
                break;
            }
            dir = dir.getParentFile();
        }

        if (settingsFound) {
            gradleExecuter.withSearchUpwards();
        }
    }

    private TestFile getTmpDir() {
        return new TestFile(getWorkingDir(), "tmp");
    }
}
