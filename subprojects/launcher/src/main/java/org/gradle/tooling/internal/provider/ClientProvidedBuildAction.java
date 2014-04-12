/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildController;

import java.io.Serializable;

class ClientProvidedBuildAction implements BuildAction<BuildActionResult>, Serializable {
    private final SerializedPayload action;

    public ClientProvidedBuildAction(SerializedPayload action) {
        this.action = action;
    }

    public BuildActionResult run(final BuildController buildController) {
        GradleInternal gradle = buildController.getGradle();
        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        final InternalBuildAction<?> action = (InternalBuildAction<?>) payloadSerializer.deserialize(this.action);

        gradle.addListener(new ModelConfigurationListener() {
            public void onConfigure(final GradleInternal gradle) {
                // Currently need to force everything to be configured
                ensureAllProjectsEvaluated(gradle);
            }
        });
        buildController.configure();

        InternalBuildController internalBuildController = new DefaultBuildController(gradle);
        Object model = null;
        Throwable failure = null;
        try {
            model = action.execute(internalBuildController);
        } catch (RuntimeException e) {
            failure = new InternalBuildActionFailureException(e);
        }

        if (failure != null) {
            return new BuildActionResult(null, payloadSerializer.serialize(failure));
        }
        return new BuildActionResult(payloadSerializer.serialize(model), null);
    }

    private void ensureAllProjectsEvaluated(GradleInternal gradle) {
        gradle.getRootProject().allprojects((Action) new Action<ProjectInternal>() {
            public void execute(ProjectInternal projectInternal) {
                projectInternal.evaluate();
            }
        });
    }
}
