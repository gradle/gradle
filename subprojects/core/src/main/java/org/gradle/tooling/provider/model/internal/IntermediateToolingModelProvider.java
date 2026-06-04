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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Fetches models for given projects in an Isolated Project-compatible manner.
 * <p>
 * It should be used by tooling model builders when they need to aggregate models
 * from <b>multiple projects of the same build</b>.
 */
@NullMarked
@ServiceScope(Scope.Build.class)
public interface IntermediateToolingModelProvider {

    /**
     * Fetches models of a given type for the given projects passing a parameter to the underlying builder.
     * <p>
     * Throws {@link org.gradle.internal.operations.MultipleBuildOperationFailures} if any target fails or
     * produces a model of an unexpected type.
     */
    <T> List<T> getModels(ProjectState requester, List<ProjectState> targets, String modelName, Class<T> modelType, @Nullable Object parameter);

    /**
     * Like {@link #getModels(ProjectState, List, String, Class, Object)}, with the model name derived
     * from the binary name of {@code modelType}.
     */
    default <T> List<T> getModels(ProjectState requester, List<ProjectState> targets, Class<T> modelType, @Nullable Object parameter) {
        return getModels(requester, targets, modelType.getName(), modelType, parameter);
    }

    /**
     * Like {@link #getModelsAllowingFailures(ProjectState, List, String, Class, Object)}, with the model name derived
     * from the binary name of {@code modelType}.
     */
    default <T> List<IntermediateToolingModelResult<T>> getModelsAllowingFailures(ProjectState requester, List<ProjectState> targets, Class<T> modelType, @Nullable Object parameter) {
        return getModelsAllowingFailures(requester, targets, modelType.getName(), modelType, parameter);
    }

    /**
     * Fetches models of a given type for the given projects passing a parameter to the underlying builder.
     * <p>
     * Returns a per-target {@link IntermediateToolingModelResult<T>} so the caller can keep successful sibling models when
     * individual targets fail. Each result carries the (possibly {@code null}) model and any attached
     * failures. Intended for model builders that can run on projects that failed during configuration (e.g., KotlinDSL scripts model).
     */
    <T> List<IntermediateToolingModelResult<T>> getModelsAllowingFailures(ProjectState requester, List<ProjectState> targets, String modelName, Class<T> modelType, @Nullable Object parameter);

    /**
     * Applies a plugin of a given type to the given projects.
     */
    <P extends Plugin<Project>> void applyPlugin(ProjectState requester, List<ProjectState> targets, Class<P> pluginClass);

    interface IntermediateToolingModelResult<T> {
        @Nullable
        T getModel();
        List<Failure> getFailures();
    }
}
