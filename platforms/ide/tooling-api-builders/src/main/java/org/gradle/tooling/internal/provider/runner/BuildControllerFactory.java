/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.buildtree.BuildTreeModelController;
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier;

@ServiceScope(Scope.BuildTree.class)
public class BuildControllerFactory {
    private final WorkerThreadRegistry workerThreadRegistry;
    private final BuildCancellationToken buildCancellationToken;
    private final BuildStateRegistry buildStateRegistry;
    private final ToolingModelParameterCarrier.Factory parameterCarrierFactory;
    private final BuildTreeModelSideEffectExecutor sideEffectExecutor;
    private final PayloadSerializer payloadSerializer;

    public BuildControllerFactory(
        WorkerThreadRegistry workerThreadRegistry,
        BuildCancellationToken buildCancellationToken,
        BuildStateRegistry buildStateRegistry,
        ToolingModelParameterCarrier.Factory parameterCarrierFactory,
        BuildTreeModelSideEffectExecutor sideEffectExecutor,
        PayloadSerializer payloadSerializer
    ) {
        this.workerThreadRegistry = workerThreadRegistry;
        this.buildCancellationToken = buildCancellationToken;
        this.buildStateRegistry = buildStateRegistry;
        this.sideEffectExecutor = sideEffectExecutor;
        this.parameterCarrierFactory = parameterCarrierFactory;
        this.payloadSerializer = payloadSerializer;
    }

    public DefaultBuildController controllerFor(BuildTreeModelController controller) {
        return new DefaultBuildController(controller, workerThreadRegistry, buildCancellationToken, buildStateRegistry, parameterCarrierFactory, sideEffectExecutor, payloadSerializer);
    }
}
