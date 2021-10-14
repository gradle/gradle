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
            ActionResults actionResults = buildController.fromBuildModel(action.isRunTasks(), adapter);
            // The result may have been cached and the actions not executed; push the results to the client if required
            adapter.maybeApplyResult(actionResults);
            return Result.of(action.getResult());
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

    private static class ActionResults {
        @Nullable
        final SerializedPayload projectsLoadedResult;

        @Nullable
        final SerializedPayload buildFinishedResult;

        ActionResults(@Nullable SerializedPayload projectsLoadedResult, @Nullable SerializedPayload buildFinishedResult) {
            this.projectsLoadedResult = projectsLoadedResult;
            this.buildFinishedResult = buildFinishedResult;
        }
    }

    private class ActionAdapter implements BuildTreeModelAction<ActionResults> {
        private final ClientAction clientAction;
        private final PayloadSerializer payloadSerializer;
        SerializedPayload projectsEvaluatedResult;
        SerializedPayload buildFinishedResult;
        RuntimeException actionFailure;
        boolean executed;

        ActionAdapter(ClientAction clientAction, PayloadSerializer payloadSerializer) {
            this.clientAction = clientAction;
            this.payloadSerializer = payloadSerializer;
        }

        @Override
        public void beforeTasks(BuildTreeModelController controller) {
            projectsEvaluatedResult = runAction(controller, clientAction.getProjectsEvaluatedAction(), PhasedActionResult.Phase.PROJECTS_LOADED);
        }

        @Override
        public ActionResults fromBuildModel(BuildTreeModelController controller) {
            buildFinishedResult = runAction(controller, clientAction.getBuildFinishedAction(), PhasedActionResult.Phase.BUILD_FINISHED);
            executed = true;
            return new ActionResults(projectsEvaluatedResult, buildFinishedResult);
        }

        public void maybeApplyResult(ActionResults actionResults) {
            if (executed) {
                return;
            }
            // Using a cached result
            if (actionResults.projectsLoadedResult != null) {
                clientAction.collectActionResult(actionResults.projectsLoadedResult, PhasedActionResult.Phase.PROJECTS_LOADED);
            }
            if (actionResults.buildFinishedResult != null) {
                clientAction.collectActionResult(actionResults.buildFinishedResult, PhasedActionResult.Phase.BUILD_FINISHED);
            }
        }

        @SuppressWarnings("deprecation")
        private SerializedPayload runAction(BuildTreeModelController controller, @Nullable Object action, PhasedActionResult.Phase phase) {
            if (action == null || actionFailure != null) {
                return null;
            }
            DefaultBuildController internalBuildController = buildControllerFactory.controllerFor(controller);
            try {
                Object result;
                if (action instanceof InternalBuildActionVersion2<?>) {
                    result = ((InternalBuildActionVersion2) action).execute(internalBuildController);
                } else {
                    result = ((org.gradle.tooling.internal.protocol.InternalBuildAction) action).execute(internalBuildController);
                }
                SerializedPayload serializedResult = payloadSerializer.serialize(result);
                clientAction.collectActionResult(serializedResult, phase);
                return serializedResult;
            } catch (RuntimeException e) {
                actionFailure = e;
                throw e;
            }
        }
    }
}
