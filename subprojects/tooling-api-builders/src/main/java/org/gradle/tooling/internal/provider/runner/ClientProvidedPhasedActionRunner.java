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

import org.gradle.BuildResult;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalPhasedAction;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
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

        try {
            if (clientProvidedPhasedAction.isRunTasks()) {
                buildController.run();
            } else {
                buildController.configure();
            }
        } catch (RuntimeException e) {
            // Failures in BuildActions in a PhasedAction will be wrapped inside other runtime exceptions by the build itself.
            // This adapter unwraps the expected exception so it is in the correct format and client will receive it correctly.
            RuntimeException unwrappedException = new PhasedActionExceptionTransformer().transform(e);
            if (unwrappedException == null) {
                throw e;
            } else {
                buildController.setResult(new BuildActionResult(null, payloadSerializer.serialize(unwrappedException)));
            }
        }
        if (!buildController.hasResult()) {
            buildController.setResult(new BuildActionResult(payloadSerializer.serialize(null), null));
        }
    }

    private void addBuildListener(final InternalPhasedAction phasedAction, final BuildController buildController) {
        final GradleInternal gradleInternal = buildController.getGradle();
        gradleInternal.addBuildListener(new InternalBuildAdapter() {
            @Override
            public void projectsEvaluated(Gradle gradle) {
                run(phasedAction.getProjectsLoadedAction(), PhasedActionResult.Phase.PROJECTS_LOADED);
            }

            @Override
            public void buildFinished(BuildResult result) {
                if (result.getFailure() == null) {
                    run(phasedAction.getBuildFinishedAction(), PhasedActionResult.Phase.BUILD_FINISHED);
                }
            }

            private void run(@Nullable InternalBuildActionVersion2<?> action, PhasedActionResult.Phase phase) {
                if (action != null) {
                    BuildActionResult result = runAction(action, gradleInternal);
                    PhasedBuildActionResult res = new PhasedBuildActionResult(result.result, phase);
                    getBuildEventConsumer(gradleInternal).dispatch(res);
                }
            }
        });
    }

    private <T> BuildActionResult runAction(InternalBuildActionVersion2<T> action, GradleInternal gradle) {
        DefaultBuildController internalBuildController = new DefaultBuildController(gradle);
        T model;
        try {
            model = action.execute(internalBuildController);
        } catch (BuildCancelledException e) {
            throw new InternalBuildCancelledException(e);
        } catch (RuntimeException e) {
            throw new InternalBuildActionFailureException(e);
        }

        PayloadSerializer payloadSerializer = getPayloadSerializer(gradle);
        return new BuildActionResult(payloadSerializer.serialize(model), null);
    }

    private PayloadSerializer getPayloadSerializer(GradleInternal gradle) {
        return gradle.getServices().get(PayloadSerializer.class);
    }

    private BuildEventConsumer getBuildEventConsumer(GradleInternal gradle) {
        return gradle.getServices().get(BuildEventConsumer.class);
    }

    class PhasedActionExceptionTransformer implements Transformer<RuntimeException, RuntimeException> {
        public RuntimeException transform(RuntimeException e) {
            for (Throwable t = e; t != null; t = t.getCause()) {
                if (t instanceof InternalBuildActionFailureException || t instanceof InternalBuildCancelledException) {
                    return (RuntimeException) t;
                }
            }
            return null;
        }
    }
}
