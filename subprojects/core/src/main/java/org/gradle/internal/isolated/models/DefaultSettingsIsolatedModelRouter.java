/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.isolated.models;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.provider.Provider;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultSettingsIsolatedModelRouter implements IsolatedModelRouter {

    private final IsolatedModelScope buildScope;
    private final IsolatedModelController modelController;
    private final ProjectRegistry<ProjectInternal> projectRegistry;

    public DefaultSettingsIsolatedModelRouter(
        GradleInternal gradle,
        IsolatedModelController modelController,
        ProjectRegistry<ProjectInternal> projectRegistry
    ) {
        this.buildScope = new IsolatedModelScope(gradle.getIdentityPath());
        this.modelController = modelController;
        this.projectRegistry = projectRegistry;
    }

    @Override
    public <T> IsolatedModelKey<T> key(String name, Class<T> type) {
        return new DefaultIsolatedModelKey<>(name, type);
    }

    @Override
    public <T> IsolatedModelWork<T> work(Provider<T> provider) {
        return new DefaultIsolatedModelWork<>(provider);
    }

    @Override
    public <T> Map<String, Provider<T>> getProjectModels(IsolatedModelKey<T> key, Collection<String> projectPaths) {
        return projectPaths.stream()
            .map(projectRegistry::getProject)
            .collect(Collectors.toMap(
                ProjectInternal::getPath,
                it -> modelController.obtain(buildScope, key, scope(it))
            ));
    }

    private static IsolatedModelScope scope(ProjectInternal project) {
        return new IsolatedModelScope(project.getBuildPath(), project.getProjectPath());
    }

    @Override
    public <T> Provider<T> getBuildModel(IsolatedModelKey<T> key) {
        return modelController.obtain(buildScope, key, buildScope);
    }

    @Override
    public <T> void postModel(IsolatedModelKey<T> key, IsolatedModelWork<T> work) {
        modelController.register(buildScope, key, work);
    }
}
