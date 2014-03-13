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

package org.gradle.tooling.model.internal;

import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

/**
 * {@link ToolingModelBuilder} that can behave differently when called from
 * a simple {@code ProjectConnection.getModel(Class)} and when called inside
 * {@code BuildAction} with a passed Project parameter
 */
public abstract class ProjectSensitiveToolingModelBuilder implements ToolingModelBuilder{

    /**
     * Callback to create requested model.
     *
     * Default implementation uses fallback to {@link ToolingModelBuilder#buildAll(String, org.gradle.api.Project)}.
     * @param modelName model name accepted bu {@link #canBuild(String)}
     * @param project project used when calling {@link org.gradle.tooling.BuildController#getModel(org.gradle.tooling.model.Model, Class)} or default project
     * @param implicitProject {@code true} if no project was specified when request for this model was made
     * @return model
     */
    public Object buildAll(String modelName, Project project, boolean implicitProject) {
        return buildAll(modelName, project);
    }
}
