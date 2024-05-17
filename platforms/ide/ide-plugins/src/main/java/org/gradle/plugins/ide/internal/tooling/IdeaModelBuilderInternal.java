/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

@NonNullApi
public interface IdeaModelBuilderInternal extends ToolingModelBuilder {

    /**
     * Builds a structural implementation of the {@link org.gradle.tooling.model.idea.IdeaProject} model
     * for the root project of a given project.
     *
     * @param project used to discover the root project to build the model for
     */
    DefaultIdeaProject buildForRoot(Project project, boolean offlineDependencyResolution);

}
