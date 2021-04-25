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

import org.gradle.BuildResult;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.tooling.internal.protocol.InternalBuildActionFailureException;
import org.gradle.tooling.internal.protocol.InternalBuildActionVersion2;
import org.gradle.tooling.internal.protocol.PhasedActionResult;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractClientProvidedBuildActionRunner implements BuildActionRunner {
    protected Result runClientAction(ClientAction action, BuildTreeLifecycleController buildController) {

        GradleInternal gradle = buildController.getGradle();

        ActionRunningListener listener = new ActionRunningListener(gradle, action);

        Throwable buildFailure = null;
        RuntimeException clientFailure = null;
        try {
            gradle.addBuildListener(listener);
            if (action.isRunTasks()) {
                buildController.run();
            } else {
                buildController.configure();
            }
        } catch (RuntimeException e) {
            buildFailure = e;
            clientFailure = e;
        }
        if (listener.actionFailure != null) {
            clientFailure = new InternalBuildActionFailureException(listener.actionFailure);
        }

        if (buildFailure != null) {
            return Result.failed(buildFailure, clientFailure);
        }
        return Result.of(action.getResult());
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

    private class ActionRunningListener extends InternalBuildAdapter {
        private final GradleInternal gradle;
        private final ClientAction clientAction;
        RuntimeException actionFailure;

        ActionRunningListener(GradleInternal gradle, ClientAction clientAction) {
            this.gradle = gradle;
            this.clientAction = clientAction;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            forceFullConfiguration(this.gradle, new HashSet<>());
            runAction(clientAction.getProjectsEvaluatedAction(), PhasedActionResult.Phase.PROJECTS_LOADED);
        }

        @Override
        public void buildFinished(BuildResult result) {
            if (result.getFailure() != null) {
                return;
            }
            runAction(clientAction.getBuildFinishedAction(), PhasedActionResult.Phase.BUILD_FINISHED);
        }

        @SuppressWarnings("deprecation")
        private void runAction(@Nullable Object action, PhasedActionResult.Phase phase) {
            if (action == null || actionFailure != null) {
                return;
            }
            DefaultBuildController internalBuildController = new DefaultBuildController(gradle,
                gradle.getServices().get(BuildCancellationToken.class),
                gradle.getServices().get(BuildOperationExecutor.class),
                gradle.getServices().get(ProjectLeaseRegistry.class));
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
