/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.StartParameter;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.launcher.cli.ExecuteBuildAction;
import org.gradle.launcher.daemon.client.DaemonClient;
import org.gradle.launcher.daemon.client.EmbeddedDaemonClientServices;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.DefaultBuildActionParameters;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.StreamBackedStandardOutputListener;
import org.gradle.test.fixtures.file.TestDirectoryProvider;

import java.lang.management.ManagementFactory;

class EmbeddedDaemonGradleExecuter extends AbstractGradleExecuter {
    private final EmbeddedDaemonClientServices daemonClientServices = new EmbeddedDaemonClientServices(LoggingServiceRegistry.newEmbeddableLogging());

    EmbeddedDaemonGradleExecuter(GradleDistribution distribution, TestDirectoryProvider testDirectoryProvider) {
        super(distribution, testDirectoryProvider);
    }

    public DaemonRegistry getDaemonRegistry() {
        return daemonClientServices.get(DaemonRegistry.class);
    }

    public void assertCanExecute() throws AssertionError {
    }

    protected ExecutionResult doRun() {
        return doRun(false);
    }

    protected ExecutionFailure doRunWithFailure() {
        return (ExecutionFailure)doRun(true);
    }

    protected ExecutionResult doRun(boolean expectFailure) {
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();

        LoggingManagerInternal loggingManager = createLoggingManager(output, error);
        loggingManager.start();

        ExecuteBuildAction buildAction = createBuildAction();
        BuildActionParameters buildActionParameters = createBuildActionParameters();
        DaemonClient daemonClient = daemonClientServices.get(DaemonClient.class);

        Exception failure = null;
        try {
            daemonClient.execute(buildAction, buildActionParameters);
        } catch (Exception e) {
            failure = e;
        } finally {
            daemonClient.stop();
            loggingManager.stop();
        }

        boolean didFail = failure != null;
        if (expectFailure != didFail) {
            String didOrDidntSnippet = didFail ? "DID fail" : "DID NOT fail";
            throw new RuntimeException(String.format("Gradle execution in %s %s with: %nOutput:%n%s%nError:%n%s%n-----%n", getWorkingDir(), didOrDidntSnippet, output, error), failure);
        }

        if (expectFailure) {
            return new OutputScrapingExecutionFailure(output.toString(), error.toString());
        } else {
            return new OutputScrapingExecutionResult(output.toString(), error.toString());
        }
    }

    private LoggingManagerInternal createLoggingManager(StringBuilder output, StringBuilder error) {
        LoggingManagerInternal loggingManager = daemonClientServices.newInstance(LoggingManagerInternal.class);
        loggingManager.addStandardOutputListener(new StreamBackedStandardOutputListener(output));
        loggingManager.addStandardErrorListener(new StreamBackedStandardOutputListener(error));
        return loggingManager;
    }

    private ExecuteBuildAction createBuildAction() {
        DefaultCommandLineConverter commandLineConverter = new DefaultCommandLineConverter();
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(getWorkingDir());
        commandLineConverter.convert(getAllArgs(), startParameter);
        return new ExecuteBuildAction(startParameter);
    }

    private BuildActionParameters createBuildActionParameters() {
        return new DefaultBuildActionParameters(daemonClientServices.get(BuildClientMetaData.class), getStartTime(), System.getProperties(), getEnvironmentVars(), getWorkingDir(), LogLevel.LIFECYCLE);
    }

    private long getStartTime() {
        return ManagementFactory.getRuntimeMXBean().getStartTime();
    }
}