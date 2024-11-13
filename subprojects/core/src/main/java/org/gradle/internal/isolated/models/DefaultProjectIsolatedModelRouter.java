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

import org.gradle.api.Project;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.Provider;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class DefaultProjectIsolatedModelRouter implements IsolatedModelRouter {

    private final IsolatedModelScope projectScope;
    private final IsolatedModelController modelController;
    private final ProjectRegistry<ProjectInternal> projectRegistry;

    public DefaultProjectIsolatedModelRouter(
        ProjectInternal project,
        IsolatedModelController modelController,
        ProjectRegistry<ProjectInternal> projectRegistry
    ) {
        this.projectScope = new IsolatedModelScope(project.getBuildPath(), project.getProjectPath());
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
            .collect(toMap(
                ProjectInternal::getPath,
                it -> modelController.obtain(projectScope, key, scope(it))
            ));
    }

    private static IsolatedModelScope scope(ProjectInternal project) {
        return new IsolatedModelScope(project.getBuildPath(), project.getProjectPath());
    }

    @Override
    public <T> Provider<T> getBuildModel(IsolatedModelKey<T> key) {
        return modelController.obtain(projectScope, key, projectScope.getBuildScope());
    }

    @Override
    public <T> void postModel(IsolatedModelKey<T> key, IsolatedModelWork<T> work) {
        modelController.register(projectScope, key, work);
    }

    @Override
    public <T> void shareModel(String name, Class<T> type, Provider<T> provider) {
        postModel(key(name, type), work(provider));
    }

    @Override
    public <T> Map<IsolatedProject, Provider<T>> getModelPerProject(String name, Class<T> type, Collection<Project> projects) {
        Map<String, Provider<T>> byPath = getProjectModels(key(name, type), projects.stream().map(Project::getPath).collect(Collectors.toList()));
        return byPath.keySet().stream()
            .collect(Collectors.toMap(
                it -> projectRegistry.getProject(it).getIsolated(),
                byPath::get
            ));
    }

    @Override
    public <T> Provider<T> getModelForBuild(String name, Class<T> type) {
        return getBuildModel(key(name, type));
    }
}
