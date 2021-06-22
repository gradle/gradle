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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.PhasedActionResult;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public abstract class AbstractClientProvidedBuildActionRunner implements BuildActionRunner {
    private final BuildControllerFactory buildControllerFactory;
    private final PayloadSerializer payloadSerializer;

    public AbstractClientProvidedBuildActionRunner(BuildControllerFactory buildControllerFactory, PayloadSerializer payloadSerializer) {
        this.buildControllerFactory = buildControllerFactory;
        this.payloadSerializer = payloadSerializer;
    }

    protected Result runClientAction(ClientAction action, BuildTreeLifecycleController buildController) {

        GradleInternal gradle = buildController.getGradle();

        ActionRunningListener listener = new ActionRunningListener(action, payloadSerializer);

        try {
            gradle.addBuildListener(listener);
            ActionResults actionResults = buildController.fromBuildModel(action.isRunTasks(), listener);
            // The result may have been cached and the actions not executed; push the results to the client if required
            listener.maybeApplyResult(actionResults);
            return Result.of(action.getResult());
        } catch (RuntimeException e) {
            RuntimeException clientFailure = e;
            if (listener.actionFailure != null) {
                clientFailure = new InternalBuildActionFailureException(listener.actionFailure);
            }
            return Result.failed(e, clientFailure);
        }
    }

    private void forceFullConfiguration(GradleInternal gradle, Set<GradleInternal> alreadyConfigured) {
        gradle.getServices().get(ProjectConfigurer.class).configureHierarchyFully(gradle.getRootProject());
        for (IncludedBuildInternal reference : gradle.includedBuilds()) {
            BuildState target = reference.getTarget();
            if (target instanceof IncludedBuildState) {
                GradleInternal build = ((IncludedBuildState) target).getConfiguredBuild();
                if (!alreadyConfigured.contains(build)) {
                    alreadyConfigured.add(build);
                    forceFullConfiguration(build, alreadyConfigured);
                }
            }
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

    private class ActionRunningListener extends InternalBuildAdapter implements Function<GradleInternal, ActionResults> {
        private final ClientAction clientAction;
        private final PayloadSerializer payloadSerializer;
        SerializedPayload projectsEvaluatedResult;
        SerializedPayload buildFinishedResult;
        RuntimeException actionFailure;
        boolean executed;

        ActionRunningListener(ClientAction clientAction, PayloadSerializer payloadSerializer) {
            this.clientAction = clientAction;
            this.payloadSerializer = payloadSerializer;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            GradleInternal gradleInternal = (GradleInternal) gradle;
            forceFullConfiguration(gradleInternal, new HashSet<>());
            projectsEvaluatedResult = runAction(gradleInternal, clientAction.getProjectsEvaluatedAction(), PhasedActionResult.Phase.PROJECTS_LOADED);
        }

        @Override
        public ActionResults apply(GradleInternal gradle) {
            buildFinishedResult = runAction(gradle, clientAction.getBuildFinishedAction(), PhasedActionResult.Phase.BUILD_FINISHED);
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
        private SerializedPayload runAction(GradleInternal gradle, @Nullable Object action, PhasedActionResult.Phase phase) {
            if (action == null || actionFailure != null) {
                return null;
            }
            DefaultBuildController internalBuildController = buildControllerFactory.controllerFor(gradle);
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
