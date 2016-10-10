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
 * Used together with {@link ProjectToolingModelBuilder} to build models for several projects at once.
 *
 * @since 3.3
 */
@Incubating
public interface ToolingModelBuilderContext {
    /**
     * Associates the given model with the given project.
     *
     * @param project The project the model was requested for.
     * @param model The model built for this project.
     */
    void addModel(Project project, Object model);

    /**
     * Associates the given failure with the given project.
     *
     * @param project The project for which failed to provide a model.
     * @param failure The failure encountered while building the model.
     */
    void addFailure(Project project, RuntimeException failure);
}
