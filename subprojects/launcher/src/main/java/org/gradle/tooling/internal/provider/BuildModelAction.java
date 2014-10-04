/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.BuildAction;
import org.gradle.initialization.BuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;

import java.io.Serializable;

public class BuildModelAction implements BuildAction<BuildActionResult>, Serializable {
    private final boolean runTasks;
    private final String modelName;

    public BuildModelAction(String modelName, boolean runTasks) {
        this.modelName = modelName;
        this.runTasks = runTasks;
    }

    public BuildActionResult run(BuildController buildController) {
        GradleInternal gradle = buildController.getGradle();

        if (runTasks) {
            buildController.run();
        } else {
            buildController.configure();
            // Currently need to force everything to be configured
            gradle.getServices().get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
        }

        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        ToolingModelBuilder builder;
        try {
            builder = builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException)new InternalUnsupportedModelException().initCause(e);
        }

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
