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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.ClientProvidedPhasedAction;
import org.gradle.tooling.internal.provider.PhasedBuildActionResult;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

import javax.annotation.Nullable;

public class ClientProvidedPhasedActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, final BuildController buildController) {
        if (!(action instanceof ClientProvidedPhasedAction)) {
            return;
        }

        GradleInternal gradle = buildController.getGradle();

        gradle.getStartParameter().setConfigureOnDemand(false);

        ClientProvidedPhasedAction clientProvidedPhasedAction = (ClientProvidedPhasedAction) action;
        PayloadSerializer payloadSerializer = getPayloadSerializer(gradle);

        InternalPhasedAction phasedAction = (InternalPhasedAction) payloadSerializer.deserialize(clientProvidedPhasedAction.getPhasedAction());

        addBuildListener(phasedAction, buildController);

        // We don't know if the model builders invoked in the after configuration action will add tasks to the graph or not
        // so we have to execute build until Build stage before finishing it.
        // TODO: try to figure out a way to finish the build earlier if possible (no tasks passed by the client and no tasks added by the model builder)
        buildController.run();
        if (!buildController.hasResult()) {
            buildController.setResult(new BuildActionResult(payloadSerializer.serialize(null), null));
        }
    }

    private void addBuildListener(final InternalPhasedAction phasedAction, final BuildController buildController) {
        final GradleInternal gradleInternal = buildController.getGradle();
        gradleInternal.addBuildListener(new BuildAdapter() {
            @Override
            public void projectsLoaded(Gradle gradle) {
                // If build controller has a result at this point, it is a failure.
                if (!buildController.hasResult()) {
                    run(phasedAction.getAfterLoadingAction(), PhasedBuildActionResult.Type.AFTER_LOADING);
                }
            }

            @Override
            public void projectsEvaluated(Gradle gradle) {
                // If build controller has a result at this point, it is a failure.
                if (!buildController.hasResult()) {
                    run(phasedAction.getAfterConfigurationAction(), PhasedBuildActionResult.Type.AFTER_CONFIGURATION);
                }
            }

            @Override
            public void buildFinished(BuildResult result) {
                // If build controller has a result at this point, it is a failure.
                if (result.getFailure() == null && !buildController.hasResult()) {
                    run(phasedAction.getAfterBuildAction(), PhasedBuildActionResult.Type.AFTER_BUILD);
                }
            }

            private void run(@Nullable InternalBuildActionVersion2<?> action, PhasedBuildActionResult.Type type) {
                if (action != null) {
                    PhasedBuildActionResult result = runAction(action, gradleInternal, type);
                    if (result.failure != null) {
                        buildController.setResult(new BuildActionResult(result.result, result.failure));
                    }
                    getBuildEventConsumer(gradleInternal).dispatch(result);
                }
            }
        });
    }

    private <T> PhasedBuildActionResult runAction(InternalBuildActionVersion2<T> action, GradleInternal gradle, PhasedBuildActionResult.Type type) {
        DefaultBuildController internalBuildController = new DefaultBuildController(gradle);
        T model = null;
        Throwable failure = null;
        try {
            model = action.execute(internalBuildController);
        } catch (BuildCancelledException e) {
            failure = new InternalBuildCancelledException(e);
        } catch (RuntimeException e) {
            failure = new InternalBuildActionFailureException(e);
        }

        PayloadSerializer payloadSerializer = getPayloadSerializer(gradle);
        if (failure != null) {
            return new PhasedBuildActionResult(null, payloadSerializer.serialize(failure), type);
        } else {
            return new PhasedBuildActionResult(payloadSerializer.serialize(model), null, type);
        }
    }

    private PayloadSerializer getPayloadSerializer(GradleInternal gradle) {
        return gradle.getServices().get(PayloadSerializer.class);
    }

    private BuildEventConsumer getBuildEventConsumer(GradleInternal gradle) {
        return gradle.getServices().get(BuildEventConsumer.class);
    }
}
