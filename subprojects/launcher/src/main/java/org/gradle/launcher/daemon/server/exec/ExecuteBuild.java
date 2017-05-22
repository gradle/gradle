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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.daemon.logging.DaemonMessages;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;

/**
 * Actually executes the build.
 *
 * Typically the last action in the pipeline.
 */
public class ExecuteBuild extends BuildCommandOnly {

    private static final Logger LOGGER = Logging.getLogger(ExecuteBuild.class);

    final private BuildActionExecuter<BuildActionParameters> actionExecuter;
    private final DaemonRunningStats runningStats;
    final private ServiceRegistry contextServices;

    public ExecuteBuild(BuildActionExecuter<BuildActionParameters> actionExecuter, DaemonRunningStats runningStats, ServiceRegistry contextServices) {
        this.actionExecuter = actionExecuter;
        this.runningStats = runningStats;
        this.contextServices = contextServices;
    }

    protected void doBuild(final DaemonCommandExecution execution, Build build) {
        LOGGER.debug(DaemonMessages.STARTED_BUILD);
        LOGGER.debug("Executing build with daemon context: {}", execution.getDaemonContext());
        runningStats.buildStarted();
        try {
            BuildCancellationToken cancellationToken = execution.getDaemonStateControl().getCancellationToken();
            BuildRequestContext buildRequestContext = new DefaultBuildRequestContext(build.getBuildRequestMetaData(), cancellationToken, new DaemonConnectionBackedEventConsumer(execution));
            if (!build.getParameters().isContinuous()) {
                buildRequestContext.getCancellationToken().addCallback(new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.warn(DaemonMessages.CANCELED_BUILD);
                    }
                });
            }
            Object result = actionExecuter.execute(build.getAction(), buildRequestContext, build.getParameters(), contextServices);
            execution.setResult(result);
        } catch (ReportedException e) {
            /*
                We have to wrap in a ReportedException so the other side doesn't re-log this exception, because it's already
                been logged by the GradleLauncher infrastructure, and that logging has been shipped over to the other side.

                This doesn't seem right. Perhaps we should assume on the client side that all “build failures” (opposed to daemon infrastructure failures)
                have already been logged and do away with this wrapper.
            */
            execution.setException(e);
        } finally {
            runningStats.buildFinished();
            LOGGER.debug(DaemonMessages.FINISHED_BUILD);
        }

        execution.proceed(); // ExecuteBuild should be the last action, but in case we want to decorate the result in the future
    }

    private static class DaemonConnectionBackedEventConsumer implements BuildEventConsumer {
        private final DaemonCommandExecution execution;

        public DaemonConnectionBackedEventConsumer(DaemonCommandExecution execution) {
            this.execution = execution;
        }

        @Override
        public void dispatch(Object event) {
            execution.getConnection().event(event);
        }
    }
}
