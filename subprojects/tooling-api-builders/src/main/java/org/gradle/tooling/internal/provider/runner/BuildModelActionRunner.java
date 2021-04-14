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
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.buildtree.BuildActionRunner;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;

import java.util.HashSet;
import java.util.Set;

public class BuildModelActionRunner implements BuildActionRunner {
    @Override
    public Result run(BuildAction action, final BuildTreeLifecycleController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return Result.nothing();
        }

        BuildModelAction buildModelAction = (BuildModelAction) action;
        GradleInternal gradle = buildController.getGradle();
        BuildResultAdapter listener = new BuildResultAdapter(gradle, buildModelAction);

        Throwable buildFailure = null;
        RuntimeException clientFailure = null;
        try {
            gradle.addBuildListener(listener);
            if (buildModelAction.isRunTasks()) {
                buildController.run();
            } else {
                buildController.configure();
            }
        } catch (RuntimeException e) {
            buildFailure = e;
            clientFailure = e;
        }
        if (listener.modelFailure != null) {
            clientFailure = (RuntimeException) new InternalUnsupportedModelException().initCause(listener.modelFailure);
        }
        if (buildFailure != null) {
            return Result.failed(buildFailure, clientFailure);
        }
        return Result.of(listener.result);
    }

    private static class BuildResultAdapter extends InternalBuildAdapter {
        private final GradleInternal gradle;
        private final BuildModelAction buildModelAction;
        private Object result;
        private RuntimeException modelFailure;

        private BuildResultAdapter(GradleInternal gradle, BuildModelAction buildModelAction) {
            this.gradle = gradle;
            this.buildModelAction = buildModelAction;
        }

        @Override
        public void projectsEvaluated(Gradle gradle) {
            if (buildModelAction.isCreateModel()) {
                forceFullConfiguration((GradleInternal) gradle, new HashSet<>());
            }
        }

        @Override
        public void buildFinished(BuildResult result) {
            if (result.getFailure() == null) {
                this.result = buildModel(gradle, buildModelAction);
            }
        }

        private Object buildModel(GradleInternal gradle, BuildModelAction buildModelAction) {
            String modelName = buildModelAction.getModelName();
            ToolingModelBuilderLookup.Builder builder = getModelBuilder(modelName, gradle);
            return builder.build(null);
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

        private ToolingModelBuilderLookup.Builder getModelBuilder(String modelName, GradleInternal gradle) {
            ToolingModelBuilderLookup builderRegistry = getToolingModelBuilderRegistry(gradle);
            try {
                return builderRegistry.locateForClientOperation(modelName, false, gradle);
            } catch (UnknownModelException e) {
                modelFailure = e;
                throw e;
            }
        }

        private static ToolingModelBuilderLookup getToolingModelBuilderRegistry(GradleInternal gradle) {
            return gradle.getDefaultProject().getServices().get(ToolingModelBuilderLookup.class);
        }
    }
}
