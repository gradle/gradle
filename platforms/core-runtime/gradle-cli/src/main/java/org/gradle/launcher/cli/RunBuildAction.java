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
package org.gradle.launcher.cli;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.daemon.client.execution.ClientBuildRequestContext;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.BuildActionExecutor;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;
import org.gradle.tooling.internal.provider.action.ExecuteBuildAction;

public class RunBuildAction implements Runnable {
    private final BuildActionExecutor<BuildActionParameters, ClientBuildRequestContext> executor;
    private final StartParameterInternal startParameter;
    private final GradleLauncherMetaData clientMetaData;
    private final long startTime;
    private final BuildActionParameters buildActionParameters;
    private final ServiceRegistry sharedServices;
    private final Stoppable stoppable;

    public RunBuildAction(
        BuildActionExecutor<BuildActionParameters, ClientBuildRequestContext> executor, StartParameterInternal startParameter, GradleLauncherMetaData clientMetaData, long startTime,
        BuildActionParameters buildActionParameters, ServiceRegistry sharedServices, Stoppable stoppable) {
        this.executor = executor;
        this.startParameter = startParameter;
        this.clientMetaData = clientMetaData;
        this.startTime = startTime;
        this.buildActionParameters = buildActionParameters;
        this.sharedServices = sharedServices;
        this.stoppable = stoppable;
    }

    @Override
    public void run() {
        try {
            BuildActionResult result = executor.execute(
                new ExecuteBuildAction(startParameter),
                buildActionParameters,
                new ClientBuildRequestContext(clientMetaData, startTime, sharedServices.get(ConsoleDetector.class).isConsoleInput(), new DefaultBuildCancellationToken(), new NoOpBuildEventConsumer())
            );
            if (result.hasFailure()) {
                // Don't need to unpack the serialized failure. It will already have been reported and is not used by anything downstream of this action.
                throw new ReportedException();
            }
        } finally {
            stoppable.stop();
        }
    }
}
