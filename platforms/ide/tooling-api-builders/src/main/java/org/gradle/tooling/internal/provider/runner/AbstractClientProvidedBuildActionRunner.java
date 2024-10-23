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
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

import javax.annotation.Nullable;

public abstract class AbstractClientProvidedBuildActionRunner implements BuildActionRunner {
    private final BuildControllerFactory buildControllerFactory;
    private final PayloadSerializer payloadSerializer;

    public AbstractClientProvidedBuildActionRunner(BuildControllerFactory buildControllerFactory, PayloadSerializer payloadSerializer) {
        this.buildControllerFactory = buildControllerFactory;
        this.payloadSerializer = payloadSerializer;
    }

    protected Result runClientAction(ClientAction action, BuildTreeLifecycleController buildController) {
        ActionAdapter adapter = new ActionAdapter(action, payloadSerializer);
        try {
            SerializedPayload result = buildController.fromBuildModel(action.isRunTasks(), adapter);
            return Result.of(result);
        } catch (RuntimeException e) {
            RuntimeException clientFailure = e;
            if (adapter.actionFailure != null) {
                clientFailure = new InternalBuildActionFailureException(adapter.actionFailure);
            }
            return Result.failed(e, clientFailure);
        }
    }

    protected interface ClientAction {
        @Nullable
        Object getProjectsEvaluatedAction();

        @Nullable
        Object getBuildFinishedAction();

        void collectActionResult(SerializedPayload serializedResult, PhasedActionResult.Phase phase);

        @Nullable
        SerializedPayload getResult();

        boolean isRunTasks();
    }

    private class ActionAdapter implements BuildTreeModelAction<SerializedPayload> {
        private final ClientAction clientAction;
        private final PayloadSerializer payloadSerializer;
        RuntimeException actionFailure;

        ActionAdapter(ClientAction clientAction, PayloadSerializer payloadSerializer) {
            this.clientAction = clientAction;
            this.payloadSerializer = payloadSerializer;
        }

        @Override
        public void beforeTasks(BuildTreeModelController controller) {
            runAction(controller, clientAction.getProjectsEvaluatedAction(), PhasedActionResult.Phase.PROJECTS_LOADED);
        }

        @Override
        public SerializedPayload fromBuildModel(BuildTreeModelController controller) {
            runAction(controller, clientAction.getBuildFinishedAction(), PhasedActionResult.Phase.BUILD_FINISHED);
            return clientAction.getResult();
        }

        private void runAction(BuildTreeModelController controller, @Nullable Object action, PhasedActionResult.Phase phase) {
            if (action == null || actionFailure != null) {
                return;
            }

            DefaultBuildController internalBuildController = buildControllerFactory.controllerFor(controller);
            try {
                Object result = executeAction(action, internalBuildController);
                SerializedPayload serializedResult = payloadSerializer.serialize(result);
                clientAction.collectActionResult(serializedResult, phase);
            } catch (RuntimeException e) {
                actionFailure = e;
                throw e;
            }
        }

        @SuppressWarnings({"rawtypes", "deprecation"})
        private Object executeAction(Object action, DefaultBuildController internalBuildController) {
            if (action instanceof InternalBuildActionVersion2<?>) {
                return ((InternalBuildActionVersion2) action).execute(internalBuildController);
            } else {
                return ((org.gradle.tooling.internal.protocol.InternalBuildAction) action).execute(internalBuildController);
            }
        }
    }
}
