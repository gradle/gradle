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

import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.PhasedActionResult;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public abstract class AbstractClientProvidedBuildActionRunner implements BuildActionRunner {
    private final BuildControllerFactory buildControllerFactory;

    public AbstractClientProvidedBuildActionRunner(BuildControllerFactory buildControllerFactory) {
        this.buildControllerFactory = buildControllerFactory;
    }

    protected Result runClientAction(ClientAction action, BuildTreeLifecycleController buildController) {

        GradleInternal gradle = buildController.getGradle();

        ActionRunningListener listener = new ActionRunningListener(action);

        try {
            gradle.addBuildListener(listener);
            buildController.fromBuildModel(action.isRunTasks(), listener);
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
        for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
            if (includedBuild instanceof IncludedBuildState) {
                GradleInternal build = ((IncludedBuildState) includedBuild).getConfiguredBuild();
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

        void collectActionResult(Object result, PhasedActionResult.Phase phase);

        boolean isRunTasks();

        Object getResult();
    }

    private class ActionRunningListener extends InternalBuildAdapter implements Function<GradleInternal, Object> {
        private final ClientAction clientAction;
        RuntimeException actionFailure;

        ActionRunningListener(ClientAction clientAction) {
            this.clientAction = clientAction;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            GradleInternal gradleInternal = (GradleInternal) gradle;
            forceFullConfiguration(gradleInternal, new HashSet<>());
            runAction(gradleInternal, clientAction.getProjectsEvaluatedAction(), PhasedActionResult.Phase.PROJECTS_LOADED);
        }

        @Override
        public Object apply(GradleInternal gradle) {
            runAction(gradle, clientAction.getBuildFinishedAction(), PhasedActionResult.Phase.BUILD_FINISHED);
            return null;
        }

        @SuppressWarnings("deprecation")
        private void runAction(GradleInternal gradle, @Nullable Object action, PhasedActionResult.Phase phase) {
            if (action == null || actionFailure != null) {
                return;
            }
            DefaultBuildController internalBuildController = buildControllerFactory.controllerFor(gradle);
            try {
                Object result;
                if (action instanceof InternalBuildActionVersion2<?>) {
                    result = ((InternalBuildActionVersion2) action).execute(internalBuildController);
                } else {
                    result = ((org.gradle.tooling.internal.protocol.InternalBuildAction) action).execute(internalBuildController);
                }
                clientAction.collectActionResult(result, phase);
            } catch (RuntimeException e) {
                actionFailure = e;
                throw e;
            }
        }
    }
}
