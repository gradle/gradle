/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.internal.invocation.BuildAction;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.action.BuildModelAction;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;
import org.gradle.tooling.provider.model.UnknownModelException;

public class BuildModelActionRunner implements BuildActionRunner {
    private final PayloadSerializer payloadSerializer;

    public BuildModelActionRunner(PayloadSerializer payloadSerializer) {
        this.payloadSerializer = payloadSerializer;
    }

    @Override
    public Result run(BuildAction action, final BuildTreeLifecycleController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return Result.nothing();
        }

        BuildModelAction buildModelAction = (BuildModelAction) action;

        ModelCreateAction createAction = new ModelCreateAction(buildModelAction);
        try {
            if (buildModelAction.isCreateModel()) {
                Object result = buildController.fromBuildModel(buildModelAction.isRunTasks(), createAction);
                SerializedPayload serializedResult = payloadSerializer.serialize(result);
                return Result.of(serializedResult);
            } else {
                buildController.scheduleAndRunTasks();
                return Result.of(null);
            }
        } catch (RuntimeException e) {
            RuntimeException clientFailure = e;
            if (createAction.modelLookupFailure != null) {
                clientFailure = (RuntimeException) new InternalUnsupportedModelException().initCause(createAction.modelLookupFailure);
            }
            return Result.failed(e, clientFailure);
        }
    }

    private static class ModelCreateAction implements BuildTreeModelAction<Object> {
        private final BuildModelAction buildModelAction;
        private UnknownModelException modelLookupFailure;

        public ModelCreateAction(BuildModelAction buildModelAction) {
            this.buildModelAction = buildModelAction;
        }

        @Override
        public void beforeTasks(BuildTreeModelController controller) {
            // Ignore
        }

        @Override
        public Object fromBuildModel(BuildTreeModelController controller) {
            String modelName = buildModelAction.getModelName();
            try {
                return controller.getModel(null, modelName, null);
            } catch (UnknownModelException e) {
                modelLookupFailure = e;
                throw e;
            }
        }
    }
}
