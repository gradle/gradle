/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.BuildTreeModelAction;
import org.gradle.internal.buildtree.BuildTreeModelController;
import org.gradle.internal.buildtree.BuildTreeModelTarget;
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.action.GrpcModelQueryAction;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Runs a {@link GrpcModelQueryAction} for the native gRPC tooling API prototype. It mirrors
 * {@link BuildModelActionRunner} but, instead of always fetching the default-target model, it fetches
 * the model for the requested logical project path via {@link BuildTreeModelTarget#ofProject}. This
 * gives a native (non-JVM) client the same per-project targeting a Tooling API {@code BuildController}
 * offers, without a client-supplied JVM {@code BuildAction}.
 */
@NullMarked
public class GrpcModelQueryActionRunner implements BuildActionRunner {
    private final PayloadSerializer payloadSerializer;

    public GrpcModelQueryActionRunner(PayloadSerializer payloadSerializer) {
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        if (!(action instanceof GrpcModelQueryAction)) {
            return Result.nothing();
        }

        GrpcModelQueryAction modelQuery = (GrpcModelQueryAction) action;

        ModelCreateAction createAction = new ModelCreateAction(modelQuery);
        try {
            ToolingModelBuilderResultInternal result = buildController.fromBuildModel(false, createAction);
            SerializedPayload serializedResult = payloadSerializer.serialize(result.getModel());
            return Result.of(serializedResult);
        } catch (RuntimeException e) {
            RuntimeException clientFailure = e;
            if (createAction.modelLookupFailure != null) {
                clientFailure = (RuntimeException) new InternalUnsupportedModelException().initCause(createAction.modelLookupFailure);
            }
            return Result.failed(e, clientFailure);
        }
    }

    private static class ModelCreateAction implements BuildTreeModelAction<ToolingModelBuilderResultInternal> {
        private final GrpcModelQueryAction modelQuery;
        private @Nullable UnknownModelException modelLookupFailure;

        public ModelCreateAction(GrpcModelQueryAction modelQuery) {
            this.modelQuery = modelQuery;
        }

        @Override
        public void beforeTasks(BuildTreeModelController controller) {
            // Ignore
        }

        @Override
        public @Nullable ToolingModelBuilderResultInternal fromBuildModel(BuildTreeModelController controller) {
            String modelName = modelQuery.getModelName();
            String projectPath = modelQuery.getProjectPath();
            BuildTreeModelTarget target = projectPath.isEmpty()
                ? BuildTreeModelTarget.ofDefault()
                : BuildTreeModelTarget.ofProject(modelQuery.getBuildRootDir(), projectPath);
            try {
                ToolingModelRequestContext modelRequestContext = new ToolingModelRequestContext(modelName, null, false);
                return controller.getModel(target, modelRequestContext);
            } catch (UnknownModelException e) {
                modelLookupFailure = e;
                throw e;
            }
        }
    }
}
