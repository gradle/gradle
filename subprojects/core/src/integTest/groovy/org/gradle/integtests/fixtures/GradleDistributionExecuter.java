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

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.util.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.io.File;

/**
 * A Junit rule which provides a {@link GradleExecuter} implementation that executes Gradle using a given {@link
 * GradleDistribution}. If not supplied in the constructor, this rule locates a field on the test object with type
 * {@link GradleDistribution}.
 *
 * By default, this executer will execute Gradle in a forked process. There is a system property which enables executing
 * Gradle in the current process.
 */
public class GradleDistributionExecuter extends AbstractGradleExecuter implements MethodRule {
    private static final String IGNORE_SYS_PROP = "org.gradle.integtest.ignore";
    private static final String EXECUTER_SYS_PROP = "org.gradle.integtest.executer";
    private static final Executer EXECUTER;
    private GradleDistribution dist;
    private boolean workingDirSet;
    private boolean userHomeSet;

    private enum Executer {
        forking, embedded, daemon
    }

    static {
        EXECUTER = Executer.valueOf(System.getProperty(EXECUTER_SYS_PROP, Executer.forking.toString()).toLowerCase());
    }

    public GradleDistributionExecuter(GradleDistribution dist) {
        this.dist = dist;
        reset();
    }

    public GradleDistributionExecuter() {
    }

    public Statement apply(Statement base, final FrameworkMethod method, Object target) {
        if (System.getProperty(IGNORE_SYS_PROP) != null) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    System.out.println(String.format("Skipping test '%s'", method.getName()));
                }
            };
        }

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

    @Override
    protected ExecutionResult doRun() {
        return checkResult(configureExecuter().run());
    }

    @Override
    protected ExecutionFailure doRunWithFailure() {
        return checkResult(configureExecuter().runWithFailure());
    }

    private <T extends ExecutionResult> T checkResult(T result) {
        // Assert that nothing unexpected was logged
        result.assertOutputHasNoStackTraces();
        result.assertErrorHasNoStackTraces();
        if (getExecutable() == null) {
            // Assert that no temp files are left lying around
            // Note: don't do this if a custom executable is used, as we don't know (and probably don't care) whether the executable cleans up or not
            getTmpDir().assertIsEmptyDir();
        }
        return result;
    }

    private GradleExecuter configureExecuter() {
        if (!workingDirSet) {
            inDirectory(dist.getTestDir());
        }
        if (!userHomeSet) {
            withUserHomeDir(dist.getUserHomeDir());
        }

        if (!getClass().desiredAssertionStatus()) {
            throw new RuntimeException("Assertions must be enabled when running integration tests.");
        }

        StartParameter parameter = new StartParameter();
        parameter.setLogLevel(LogLevel.INFO);
        parameter.setSearchUpwards(false);

        InProcessGradleExecuter inProcessGradleExecuter = new InProcessGradleExecuter(parameter);
        copyTo(inProcessGradleExecuter);

        GradleExecuter returnedExecuter = inProcessGradleExecuter;

        if (EXECUTER != Executer.embedded || !inProcessGradleExecuter.canExecute()) {
            boolean useDaemon = EXECUTER == Executer.daemon && getExecutable() == null;
            ForkingGradleExecuter forkingGradleExecuter = useDaemon ? new DaemonGradleExecuter(dist.getGradleHomeDir()) : new ForkingGradleExecuter(dist.getGradleHomeDir());
            copyTo(forkingGradleExecuter);
            TestFile tmpDir = getTmpDir();
            if (tmpDir != null) {
                tmpDir.deleteDir().createDir();
                forkingGradleExecuter.addGradleOpts(String.format("-Djava.io.tmpdir=%s", tmpDir));
            }
            returnedExecuter = forkingGradleExecuter;
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
        return new TestFile(dist.getUserHomeDir(), "tmp");
    }
}
