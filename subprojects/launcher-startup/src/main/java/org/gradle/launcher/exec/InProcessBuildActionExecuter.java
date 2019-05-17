/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.api.Transformer;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

public class InProcessBuildActionExecuter implements BuildActionExecuter<BuildActionParameters> {
    private final BuildActionRunner buildActionRunner;

    public InProcessBuildActionExecuter(BuildActionRunner buildActionRunner) {
        this.buildActionRunner = buildActionRunner;
    }

    @Override
    public BuildActionResult execute(final BuildAction action, final BuildRequestContext buildRequestContext, BuildActionParameters actionParameters, ServiceRegistry contextServices) {
        BuildStateRegistry buildRegistry = contextServices.get(BuildStateRegistry.class);
        final PayloadSerializer payloadSerializer = contextServices.get(PayloadSerializer.class);
        BuildOperationNotificationValve buildOperationNotificationValve = contextServices.get(BuildOperationNotificationValve.class);

        buildOperationNotificationValve.start();
        try {
            RootBuildState rootBuild = buildRegistry.createRootBuild(BuildDefinition.fromStartParameter(action.getStartParameter(), null));
            return rootBuild.run(new Transformer<BuildActionResult, BuildController>() {
                @Override
                public BuildActionResult transform(BuildController buildController) {
                    BuildActionRunner.Result result = buildActionRunner.run(action, buildController);
                    if (result.getBuildFailure() == null) {
                        return BuildActionResult.of(payloadSerializer.serialize(result.getClientResult()));
                    }
                    if (buildRequestContext.getCancellationToken().isCancellationRequested()) {
                        return BuildActionResult.cancelled(payloadSerializer.serialize(result.getBuildFailure()));
                    }
                    return BuildActionResult.failed(payloadSerializer.serialize(result.getClientFailure()));
                }
            });
        } finally {
            buildOperationNotificationValve.stop();
        }
    }
}
