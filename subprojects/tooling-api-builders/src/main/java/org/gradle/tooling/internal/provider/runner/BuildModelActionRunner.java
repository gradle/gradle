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

import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.provider.model.internal.ProjectSensitiveToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

public class BuildModelActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        BuildModelAction buildModelAction = (BuildModelAction) action;
        GradleInternal gradle = buildController.getGradle();

        if (buildModelAction.isRunTasks()) {
            buildController.run();
        } else {
            buildController.configure();
            // Currently need to force everything to be configured
            gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
            for (Project project : gradle.getRootProject().getAllprojects()) {
                ProjectInternal projectInternal = (ProjectInternal) project;
                projectInternal.getTasks().discoverTasks();
                projectInternal.bindAllModelRules();
            }
        }

        String modelName = buildModelAction.getModelName();
        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        ToolingModelBuilder builder;
        try {
            builder = builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }

        Object modelResult = createModelResult(gradle, modelName, builder);
        buildController.setResult(modelResult);
    }

    protected Object createModelResult(GradleInternal gradle, String modelName, ToolingModelBuilder builder) {
        Object result;
        if (builder instanceof ProjectSensitiveToolingModelBuilder) {
            result = ((ProjectSensitiveToolingModelBuilder) builder).buildAll(modelName, gradle.getDefaultProject(), true);
        } else {
            result = builder.buildAll(modelName, gradle.getDefaultProject());
        }

        PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);
        return new BuildActionResult(payloadSerializer.serialize(result), null);
    }

    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }
}
