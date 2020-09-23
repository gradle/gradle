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
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;

public class BuildModelActionRunner implements BuildActionRunner {
    @Override
    public Result run(BuildAction action, final BuildController buildController) {
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
            if (buildModelAction.isModelRequest()) {
                gradle.getStartParameter().setConfigureOnDemand(false);
            }
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
            if (buildModelAction.isModelRequest()) {
                forceFullConfiguration((GradleInternal) gradle);
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
            ToolingModelBuilder builder = getModelBuilder(gradle, modelName);

            return builder.buildAll(modelName, gradle.getDefaultProject());
        }

        private static void forceFullConfiguration(GradleInternal gradle) {
            gradle.getServices().get(ProjectConfigurer.class).configureHierarchyFully(gradle.getRootProject());
            for (IncludedBuild includedBuild : gradle.getIncludedBuilds()) {
                GradleInternal build = ((IncludedBuildState) includedBuild).getConfiguredBuild();
                forceFullConfiguration(build);
            }
        }

        private ToolingModelBuilder getModelBuilder(GradleInternal gradle, String modelName) {
            ToolingModelBuilderLookup builderRegistry = getToolingModelBuilderRegistry(gradle);
            try {
                return builderRegistry.locateForClientOperation(modelName);
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
