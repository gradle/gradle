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

    public Statement apply(Statement base, FrameworkMethod method, Object target) {
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
        result.assertOutputHasNoStackTraces();
        result.assertErrorHasNoStackTraces();
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
            returnedExecuter = forkingGradleExecuter;
        }

        boolean settingsFound = false;
        for (
                File dir = new TestFile(getWorkingDir()); dir != null && dist.isFileUnderTest(dir) && !settingsFound;
                dir = dir.getParentFile()) {
            if (new File(dir, "settings.gradle").isFile()) {
                settingsFound = true;
            }
        }
        if (settingsFound) {
            returnedExecuter.withSearchUpwards();
        }

        return returnedExecuter;
    }
}
