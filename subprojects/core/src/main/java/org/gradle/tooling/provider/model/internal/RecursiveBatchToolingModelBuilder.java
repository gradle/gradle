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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ProjectToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderContext;

/**
 * A basic implementation for {@link ProjectToolingModelBuilder} that just walks the project
 * hierarchy and builds the requested model for each project.
 */
public abstract class RecursiveBatchToolingModelBuilder implements ToolingModelBuilder, ProjectToolingModelBuilder {

    @Override
    public void addModels(String modelName, Project project, ToolingModelBuilderContext context) {
        try {
            context.addModel(project, buildAll(modelName, project));
        } catch (RuntimeException e) {
            context.addFailure(project, e);
        }
        for (Project childProject : project.getChildProjects().values()) {
            addModels(modelName, childProject, context);
        }
    }
}
