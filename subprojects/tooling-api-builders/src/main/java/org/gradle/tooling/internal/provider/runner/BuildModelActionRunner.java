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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.BuildCancelledException;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.BuildExceptionVersion1;
import org.gradle.tooling.internal.protocol.InternalBuildCancelledException;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

public class BuildModelActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, final BuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        final BuildModelAction buildModelAction = (BuildModelAction) action;
        final GradleInternal gradle = buildController.getGradle();
        gradle.addBuildListener(new BuildResultAdapter(gradle, buildController, buildModelAction));

        if (buildModelAction.isRunTasks()) {
            buildController.run();
        } else {
            buildController.configure();
        }
    }

    private static class BuildResultAdapter extends BuildAdapter {
        private final GradleInternal gradle;
        private final BuildController buildController;
        private final BuildModelAction buildModelAction;

        private BuildResultAdapter(GradleInternal gradle, BuildController buildController, BuildModelAction buildModelAction) {
            this.buildController = buildController;
            this.gradle = gradle;
            this.buildModelAction = buildModelAction;
        }

        @Override
        public void buildFinished(BuildResult result) {
            if (result.getFailure() == null) {
                buildController.setResult(buildResult(gradle, buildModelAction));
            }
        }

        private static BuildActionResult buildResult(GradleInternal gradle, BuildModelAction buildModelAction) {
            PayloadSerializer serializer = gradle.getServices().get(PayloadSerializer.class);
            try {
                Object model = buildModel(gradle, buildModelAction);
                return new BuildActionResult(serializer.serialize(model), null);
            } catch (RuntimeException e) {
                return new BuildActionResult(null, serializer.serialize(e));
            }
        }

        private static Object buildModel(GradleInternal gradle, BuildModelAction buildModelAction) {

            if (!buildModelAction.isRunTasks()) {
                forceFullConfiguration(gradle);
            }

            String modelName = buildModelAction.getModelName();
            ToolingModelBuilder builder = getModelBuilder(gradle, modelName);

            return builder.buildAll(modelName, gradle.getDefaultProject());
        }

        private static void forceFullConfiguration(GradleInternal gradle) {
            try {
                ProjectInternal rootProject = gradle.getRootProject();
                getProjectConfigurer(gradle).configureHierarchyFully(rootProject);
            } catch (BuildCancelledException e) {
                throw new InternalBuildCancelledException(e);
            } catch (RuntimeException e) {
                throw new BuildExceptionVersion1(e);
            }
        }

        private static ProjectConfigurer getProjectConfigurer(GradleInternal gradle) {
            return gradle.getServices().get(ProjectConfigurer.class);
        }

        private static ToolingModelBuilder getModelBuilder(GradleInternal gradle, String modelName) {
            ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
            try {
                return builderRegistry.getBuilder(modelName);
            } catch (UnknownModelException e) {
                throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
            }
        }

        private static ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
            return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
        }

    }
}
