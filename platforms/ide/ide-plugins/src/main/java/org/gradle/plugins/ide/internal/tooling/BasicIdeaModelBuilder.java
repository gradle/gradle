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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaProject;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

public class BasicIdeaModelBuilder implements ToolingModelBuilder {
    private final IdeaModelBuilder ideaModelBuilder;

    public BasicIdeaModelBuilder(IdeaModelBuilder ideaModelBuilder) {
        this.ideaModelBuilder = ideaModelBuilder;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.idea.BasicIdeaProject");
    }

    @Override
    public DefaultIdeaProject buildAll(String modelName, Project project) {
        return ideaModelBuilder
                .setOfflineDependencyResolution(true)
                .buildAll(modelName, project);
    }
}
