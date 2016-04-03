/*
 * Copyright 2016 the original author or authors.
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

import com.beust.jcommander.internal.Maps;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.provider.model.internal.ProjectToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

import java.util.Map;

public class BuildModelsActionRunner implements BuildActionRunner {
    @Override
    public void run(BuildAction action, BuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        BuildModelAction buildModelAction = (BuildModelAction) action;
        GradleInternal gradle = buildController.getGradle();

        buildController.configure();
        // Currently need to force everything to be configured
        gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
        // TODO:DAZ Probably need to do this
/*
        for (Project project : gradle.getRootProject().getAllprojects()) {
            ProjectInternal projectInternal = (ProjectInternal) project;
            projectInternal.getTasks().discoverTasks();
            projectInternal.bindAllModelRules();
        }
*/

        String modelName = buildModelAction.getModelName();
        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        ToolingModelBuilder builder;
        try {
            builder = builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }

        Map<String, Object> models = Maps.newLinkedHashMap();
        if (builder instanceof ProjectToolingModelBuilder) {
            ((ProjectToolingModelBuilder) builder).addModels(modelName, gradle.getDefaultProject(), models);
        } else {
            Object result = builder.buildAll(modelName, gradle.getDefaultProject());
            models.put(gradle.getDefaultProject().getPath(), result);
        }
        buildController.setResult(models);
    }

    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }
}
