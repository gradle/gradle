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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;

import java.util.ArrayList;
import java.util.List;

@NonNullApi
public class DefaultIntermediateToolingModelProvider implements IntermediateToolingModelProvider {

    private final BuildStateRegistry buildStateRegistry;

    public DefaultIntermediateToolingModelProvider(BuildStateRegistry buildStateRegistry) {
        this.buildStateRegistry = buildStateRegistry;
    }

    @Override
    public List<Object> getModels(List<Project> targets, String modelName) {
        // TODO: is it correct to always use the root build here?
        return buildStateRegistry.getRootBuild().withToolingModels(controller -> {
            ArrayList<Object> models = new ArrayList<>();
            for (Project targetProject : targets) {
                ProjectState builderTarget = ((ProjectInternal) targetProject).getOwner();
                ToolingModelScope toolingModelScope = controller.locateBuilderForTarget(builderTarget, modelName, false);
                Object model = toolingModelScope.getModel(modelName, null);
                models.add(model);
            }
            return models;
        });
    }
}
