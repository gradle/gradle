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
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.tooling.internal.protocol.InternalBuildAction;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildController;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicReference;

class ClientProvidedBuildAction implements BuildAction<BuildActionResult>, Serializable {
    private final SerializedPayload action;

    public ClientProvidedBuildAction(SerializedPayload action) {
        this.action = action;
    }

    public BuildActionResult run(final BuildController buildController) {
        final DefaultGradleLauncher gradleLauncher = (DefaultGradleLauncher) buildController.getLauncher();
        final PayloadSerializer payloadSerializer = gradleLauncher.getGradle().getServices().get(PayloadSerializer.class);
        final InternalBuildAction<?> action = (InternalBuildAction<?>) payloadSerializer.deserialize(this.action);

        // The following is all very awkward because the contract for BuildController is still just a
        // rough wrapper around GradleLauncher, which means we can only get at the model and various
        // services by using listeners.

        final AtomicReference<Object> result = new AtomicReference<Object>();
        final AtomicReference<RuntimeException> failure = new AtomicReference<RuntimeException>();

        gradleLauncher.addListener(new ModelConfigurationListener() {
            public void onConfigure(final GradleInternal gradle) {
                // Currently need to force everything to be configured
                ensureAllProjectsEvaluated(gradle);
                InternalBuildController internalBuildController = new DefaultBuildController(gradle);
                Object model = null;
                try {
                    model = action.execute(internalBuildController);
                } catch (RuntimeException e) {
                    failure.set(new InternalBuildActionFailureException(e));
                }
                result.set(model);
            }
        });
        buildController.configure();

        if (failure.get() != null) {
            return new BuildActionResult(null, payloadSerializer.serialize(failure.get()));
        }
        return new BuildActionResult(payloadSerializer.serialize(result.get()), null);
    }

    private void ensureAllProjectsEvaluated(GradleInternal gradle) {
        gradle.getRootProject().allprojects((Action) new Action<ProjectInternal>() {
            public void execute(ProjectInternal projectInternal) {
                projectInternal.evaluate();
            }
        });
    }
}
