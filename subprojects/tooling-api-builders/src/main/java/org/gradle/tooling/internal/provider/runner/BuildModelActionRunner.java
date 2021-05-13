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
import org.gradle.internal.invocation.BuildAction;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.action.BuildModelAction;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class BuildModelActionRunner implements BuildActionRunner {
    @Override
    public Result run(BuildAction action, final BuildTreeLifecycleController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return Result.nothing();
        }

        BuildModelAction buildModelAction = (BuildModelAction) action;
        GradleInternal gradle = buildController.getGradle();

        ModelCreateAction createAction = new ModelCreateAction(buildModelAction);
        try {
            if (buildModelAction.isCreateModel()) {
                gradle.addBuildListener(new ForceFullConfigurationListener());
            }
            Object result = buildController.fromBuildModel(buildModelAction.isRunTasks(), createAction);
            return Result.of(result);
        } catch (RuntimeException e) {
            RuntimeException clientFailure = e;
            if (createAction.modelLookupFailure != null) {
                clientFailure = (RuntimeException) new InternalUnsupportedModelException().initCause(createAction.modelLookupFailure);
            }
            return Result.failed(e, clientFailure);
        }
    }

    private static ToolingModelBuilderLookup getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderLookup.class);
    }

    private static class ModelCreateAction implements Function<GradleInternal, Object> {
        private final BuildModelAction buildModelAction;
        private UnknownModelException modelLookupFailure;

        public ModelCreateAction(BuildModelAction buildModelAction) {
            this.buildModelAction = buildModelAction;
        }

        @Override
        public Object apply(GradleInternal gradle) {
            String modelName = buildModelAction.getModelName();
            ToolingModelBuilderLookup builderRegistry = getToolingModelBuilderRegistry(gradle);
            ToolingModelBuilderLookup.Builder builder;
            try {
                builder = builderRegistry.locateForClientOperation(modelName, false, gradle);
            } catch (UnknownModelException e) {
                modelLookupFailure = e;
                throw e;
            }
            return builder.build(null);
        }
    }

    private static class ForceFullConfigurationListener extends InternalBuildAdapter {
        @Override
        public void projectsEvaluated(Gradle gradle) {
            forceFullConfiguration((GradleInternal) gradle, new HashSet<>());
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
    }
}
