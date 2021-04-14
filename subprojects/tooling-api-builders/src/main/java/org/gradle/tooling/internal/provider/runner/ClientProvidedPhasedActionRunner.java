/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.provider.ClientProvidedPhasedAction;
import org.gradle.tooling.internal.provider.PhasedBuildActionResult;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

public class ClientProvidedPhasedActionRunner extends AbstractClientProvidedBuildActionRunner implements BuildActionRunner {
    @Override
    public Result run(BuildAction action, BuildTreeLifecycleController buildController) {
        if (!(action instanceof ClientProvidedPhasedAction)) {
            return Result.nothing();
        }

        GradleInternal gradle = buildController.getGradle();

        ClientProvidedPhasedAction clientProvidedPhasedAction = (ClientProvidedPhasedAction) action;
        PayloadSerializer payloadSerializer = getPayloadSerializer(gradle);

        InternalPhasedAction phasedAction = (InternalPhasedAction) payloadSerializer.deserialize(clientProvidedPhasedAction.getPhasedAction());

        return runClientAction(new ClientActionImpl(phasedAction, gradle, action), buildController);
    }

    private PayloadSerializer getPayloadSerializer(GradleInternal gradle) {
        return gradle.getServices().get(PayloadSerializer.class);
    }

    private BuildEventConsumer getBuildEventConsumer(GradleInternal gradle) {
        return gradle.getServices().get(BuildEventConsumer.class);
    }

    private class ClientActionImpl implements ClientAction {
        private final InternalPhasedAction phasedAction;
        private final GradleInternal gradle;
        private final BuildAction action;

        public ClientActionImpl(InternalPhasedAction phasedAction, GradleInternal gradle, BuildAction action) {
            this.phasedAction = phasedAction;
            this.gradle = gradle;
            this.action = action;
        }

        @Override
        public Object getProjectsEvaluatedAction() {
            return phasedAction.getProjectsLoadedAction();
        }

        @Override
        public Object getBuildFinishedAction() {
            return phasedAction.getBuildFinishedAction();
        }

        @Override
        public void collectActionResult(Object result, PhasedActionResult.Phase phase) {
            PayloadSerializer payloadSerializer = getPayloadSerializer(gradle);
            SerializedPayload serializedResult = payloadSerializer.serialize(result);
            PhasedBuildActionResult res = new PhasedBuildActionResult(serializedResult, phase);
            getBuildEventConsumer(gradle).dispatch(res);
        }

        @Override
        public boolean isRunTasks() {
            return action.isRunTasks();
        }

        @Override
        public Object getResult() {
            return null;
        }
    }
}
