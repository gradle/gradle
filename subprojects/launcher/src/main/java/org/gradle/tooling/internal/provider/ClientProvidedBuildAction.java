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

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.initialization.DefaultGradleLauncher;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.tooling.internal.protocol.*;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;

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

        final AtomicReference<SerializedPayload> result = new AtomicReference<SerializedPayload>();
        final AtomicReference<SerializedPayload> failure = new AtomicReference<SerializedPayload>();

        gradleLauncher.addListener(new ModelConfigurationListener() {
            public void onConfigure(final GradleInternal gradle) {
                ToolingModelBuilderRegistry builderRegistry = gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
                InternalBuildController internalBuildController = new DefaultBuildController(gradle, builderRegistry);
                Object model = null;
                try {
                    model = action.execute(internalBuildController);
                } catch (RuntimeException e) {
                    failure.set(payloadSerializer.serialize(new InternalBuildActionFailureException(e)));
                }
                result.set(payloadSerializer.serialize(model));
            }
        });
        buildController.configure();

        return new BuildActionResult(result.get(), failure.get());
    }
}
