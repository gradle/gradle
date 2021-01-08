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
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.DefaultBuildCancellationToken;
import org.gradle.initialization.DefaultBuildRequestContext;
import org.gradle.initialization.DefaultBuildRequestMetaData;
import org.gradle.initialization.NoOpBuildEventConsumer;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.nativeintegration.console.ConsoleDetector;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.cli.action.ExecuteBuildAction;
import org.gradle.launcher.exec.BuildActionExecuter;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.launcher.exec.BuildActionResult;

public class RunBuildAction implements Runnable {
    private final BuildActionExecuter<BuildActionParameters, BuildRequestContext> executer;
    private final StartParameterInternal startParameter;
    private final BuildClientMetaData clientMetaData;
    private final long startTime;
    private final BuildActionParameters buildActionParameters;
    private final ServiceRegistry sharedServices;
    private final Stoppable stoppable;

    public RunBuildAction(BuildActionExecuter<BuildActionParameters, BuildRequestContext> executer, StartParameterInternal startParameter, BuildClientMetaData clientMetaData, long startTime,
                          BuildActionParameters buildActionParameters, ServiceRegistry sharedServices, Stoppable stoppable) {
        this.executer = executer;
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
            BuildActionResult result = executer.execute(
                new ExecuteBuildAction(startParameter),
                buildActionParameters,
                new DefaultBuildRequestContext(new DefaultBuildRequestMetaData(clientMetaData, startTime, sharedServices.get(ConsoleDetector.class).isConsoleInput()), new DefaultBuildCancellationToken(), new NoOpBuildEventConsumer())
            );
            if (result.hasFailure()) {
                // Don't need to unpack the serialized failure. It will already have been reported and is not used by anything downstream of this action.
                throw new ReportedException();
            }
        } finally {
            if (stoppable != null) {
                stoppable.stop();
            }
        }
    }
}
