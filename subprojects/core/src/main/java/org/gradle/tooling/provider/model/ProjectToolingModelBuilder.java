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

package org.gradle.tooling.provider.model;

import org.gradle.api.Incubating;
import org.gradle.api.Project;

/**
 * A {@link ToolingModelBuilder} that builds models pertaining to a specific project.
 *
 * @since 3.3
 */
@Incubating
public interface ProjectToolingModelBuilder extends ToolingModelBuilder {
    /**
     * Builds the model for the given root project and all of its subprojects, adding them to the context.
     * If the model cannot be built for any of the subprojects, a failure should be registered in
     * the context.
     *
     * @param modelName The model name, usually the same as the name of the Java interface used by the client.
     * @param rootProject The root project to create the models for.
     * @param context The context that accepts models and failures.
     */
    void addModels(String modelName, Project rootProject, ToolingModelBuilderContext context);

}
