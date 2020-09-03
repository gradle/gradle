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

import org.gradle.api.internal.BuildDefinition;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.operations.notify.BuildOperationNotificationValve;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

public class InProcessBuildActionExecuter implements BuildTreeBuildActionExecutor {
    private final BuildActionRunner buildActionRunner;
    private final BuildStateRegistry buildStateRegistry;
    private final PayloadSerializer payloadSerializer;
    private final BuildOperationNotificationValve buildOperationNotificationValve;
    private final BuildCancellationToken buildCancellationToken;

    public InProcessBuildActionExecuter(BuildStateRegistry buildStateRegistry,
                                        PayloadSerializer payloadSerializer,
                                        BuildOperationNotificationValve buildOperationNotificationValve,
                                        BuildCancellationToken buildCancellationToken,
                                        BuildActionRunner buildActionRunner) {
        this.buildActionRunner = buildActionRunner;
        this.buildStateRegistry = buildStateRegistry;
        this.payloadSerializer = payloadSerializer;
        this.buildOperationNotificationValve = buildOperationNotificationValve;
        this.buildCancellationToken = buildCancellationToken;
    }

    @Override
    public BuildActionResult execute(BuildAction action, BuildActionParameters actionParameters, BuildTreeContext buildTree) {
        buildOperationNotificationValve.start();
        try {
            RootBuildState rootBuild = buildStateRegistry.createRootBuild(BuildDefinition.fromStartParameter(action.getStartParameter(), null));
            return rootBuild.run(buildController -> {
                BuildActionRunner.Result result = buildActionRunner.run(action, buildController);
                if (result.getBuildFailure() == null) {
                    return BuildActionResult.of(payloadSerializer.serialize(result.getClientResult()));
                }
                if (buildCancellationToken.isCancellationRequested()) {
                    return BuildActionResult.cancelled(payloadSerializer.serialize(result.getBuildFailure()));
                }
                return BuildActionResult.failed(payloadSerializer.serialize(result.getClientFailure()));
            });
        } finally {
            buildOperationNotificationValve.stop();
        }
    }
}
