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
import org.gradle.internal.build.BuildState;
import org.gradle.tooling.provider.model.internal.IntermediateToolingModelProvider;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NonNullApi
public class DefaultIntermediateToolingModelProvider implements IntermediateToolingModelProvider {

    public DefaultIntermediateToolingModelProvider() {
    }

    @Override
    public List<Object> getModels(List<Project> targets, String modelName) {
        if (targets.isEmpty()) {
            return Collections.emptyList();
        }

        BuildState buildState = extractBuildState(targets);
        return buildState.withToolingModels(controller -> {
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

    private static BuildState extractBuildState(List<Project> targets) {
        BuildState result = null;
        for (Project target : targets) {
            BuildState projectBuildState = ((ProjectInternal) target).getOwner().getOwner();
            if (result == null) {
                result = projectBuildState;
            } else if (result != projectBuildState) {
                throw new IllegalArgumentException(
                    String.format("Expected target projects to share the same build state. Found at least two: %s and %s",
                        result.getDisplayName(), projectBuildState.getDisplayName())
                );
            }
        }

        if (result == null) {
            throw new IllegalStateException("Cannot find build state without target projects");
        }

        return result;
    }
}
