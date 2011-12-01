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
package org.gradle.integtests.fixtures;

import org.gradle.util.DeprecationLogger;
import org.gradle.util.TestFile;
import org.gradle.util.TextUtil;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.util.Matchers.containsLine;
import static org.gradle.util.Matchers.matchesRegexp;

/**
 * A Junit rule which provides a {@link GradleExecuter} implementation that executes Gradle using a given {@link
 * GradleDistribution}. If not supplied in the constructor, this rule locates a field on the test object with type
 * {@link GradleDistribution}.
 *
 * By default, this executer will execute Gradle in a forked process. There is a system property which enables executing
 * Gradle in the current process.
 */
public class GradleDistributionExecuter extends AbstractDelegatingGradleExecuter implements MethodRule {
    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer";

    private GradleDistribution dist;
    private boolean workingDirSet;
    private boolean userHomeSet;
    private boolean deprecationChecksOn = true;
    private final Executer executerType;

    public enum Executer {
        embedded(false),
        forking(true),
        daemon(true),
        embeddedDaemon(false);

        final public boolean forks;

        Executer(boolean forks) {
            this.forks = forks;
        }
    }

    public static Executer getSystemPropertyExecuter() {
        return Executer.valueOf(System.getProperty(EXECUTER_SYS_PROP, Executer.forking.toString()));
    }

    public GradleDistributionExecuter() {
        this(getSystemPropertyExecuter());
    }

    public GradleDistributionExecuter(Executer executerType) {
        this.executerType = executerType;
    }

    public GradleDistributionExecuter(GradleDistribution dist) {
        this(getSystemPropertyExecuter(), dist);
    }

    public GradleDistributionExecuter(Executer executerType, GradleDistribution dist) {
        this(executerType);
        this.dist = dist;
        reset();
    }

    public Executer getType() {
        return executerType;
    }

    public Statement apply(Statement base, final FrameworkMethod method, Object target) {
        if (dist == null) {
            dist = RuleHelper.getField(target, GradleDistribution.class);
        }
        reset();
        return base;
    }

    @Override
    public GradleExecuter reset() {
        super.reset();
        workingDirSet = false;
        userHomeSet = false;
        deprecationChecksOn = true;
        DeprecationLogger.reset();
        return this;
    }

    @Override
    public GradleExecuter inDirectory(File directory) {
        super.inDirectory(directory);
        workingDirSet = true;
        return this;
    }

    @Override
    public GradleExecuter withUserHomeDir(File userHomeDir) {
        super.withUserHomeDir(userHomeDir);
        userHomeSet = true;
        return this;
    }

    public GradleDistributionExecuter withDeprecationChecksDisabled() {
        deprecationChecksOn = false;
        return this;
    }

    protected <T extends ExecutionResult> T checkResult(T result) {
        // Assert that nothing unexpected was logged
        assertOutputHasNoStackTraces(result);
        assertErrorHasNoStackTraces(result);
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

        /*
        File resolversFile = new File(getUserHomeDir(), "caches/artifacts-2/.wharf/resolvers.kryo");
        Assert.assertTrue(resolversFile.getParentFile().isDirectory());
        if (resolversFile.exists()) {
            Assert.assertThat(resolversFile.length(), Matchers.greaterThan(0L));
        }
        */

        return result;
    }

    private void assertOutputHasNoStackTraces(ExecutionResult result) {
        assertNoStackTraces(result.getOutput(), "Standard output");
    }

    public void assertErrorHasNoStackTraces(ExecutionResult result) {
        String error = result.getError();
        if (result instanceof ExecutionFailure) {
            // Axe everything after the expected exception
            int pos = error.lastIndexOf("* Exception is:" + TextUtil.getPlatformLineSeparator());
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
        boolean deprecationWarning = containsLine(matchesRegexp(".*deprecated.*")).matches(output);
        if (deprecationWarning && !javacWarning) {
            throw new RuntimeException(String.format("%s contains a deprecation warning:%n=====%n%s%n=====%n", displayName, output));
        }
    }

    private void assertNoStackTraces(String output, String displayName) {
        if (containsLine(matchesRegexp("\\s+at [\\w.$_]+\\([\\w._]+:\\d+\\)")).matches(output)) {
            throw new RuntimeException(String.format("%s contains an unexpected stack trace:%n=====%n%s%n=====%n", displayName, output));
        }
    }

    protected GradleExecuter configureExecuter() {
        if (!workingDirSet) {
            inDirectory(dist.getTestDir());
        }
        if (!userHomeSet) {
            withUserHomeDir(dist.getUserHomeDir());
        }

        if (!getClass().desiredAssertionStatus()) {
            throw new RuntimeException("Assertions must be enabled when running integration tests.");
        }

        InProcessGradleExecuter inProcessGradleExecuter = new InProcessGradleExecuter();
        copyTo(inProcessGradleExecuter);

        GradleExecuter returnedExecuter = inProcessGradleExecuter;

        TestFile tmpDir = getTmpDir();
        tmpDir.deleteDir().createDir();

        if (executerType.forks || !inProcessGradleExecuter.canExecute()) {
            boolean useDaemon = executerType == Executer.daemon && getExecutable() == null;
            ForkingGradleExecuter forkingGradleExecuter = useDaemon ? new DaemonGradleExecuter(dist) : new ForkingGradleExecuter(dist.getGradleHomeDir());
            copyTo(forkingGradleExecuter);
            forkingGradleExecuter.addGradleOpts(String.format("-Djava.io.tmpdir=%s", tmpDir));
            returnedExecuter = forkingGradleExecuter;
//        } else {
//            System.setProperty("java.io.tmpdir", tmpDir.getAbsolutePath());
        }

        if (executerType == Executer.embeddedDaemon) {
            GradleExecuter embeddedDaemonExecutor = new EmbeddedDaemonGradleExecuter();
            copyTo(embeddedDaemonExecutor);
            returnedExecuter = embeddedDaemonExecutor;
        }

        boolean settingsFound = false;
        for (
                TestFile dir = new TestFile(getWorkingDir()); dir != null && dist.isFileUnderTest(dir) && !settingsFound;
                dir = dir.getParentFile()) {
            if (dir.file("settings.gradle").isFile()) {
                settingsFound = true;
            }
        }
        if (settingsFound) {
            returnedExecuter.withSearchUpwards();
        }

        return returnedExecuter;
    }

    private TestFile getTmpDir() {
        return dist.getTestDir().file("tmp");
    }
}
