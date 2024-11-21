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
import org.gradle.api.isolated.models.IsolatedModelRouter;
import org.gradle.api.project.IsolatedProject;
import org.gradle.api.provider.Provider;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

public class AbstractIsolatedModelRouter implements IsolatedModelRouter {

    private final IsolatedModelScope ownerScope;
    private final IsolatedModelController modelController;
    private final ProjectRegistry<ProjectInternal> projectRegistry;

    protected AbstractIsolatedModelRouter(
        IsolatedModelScope ownerScope,
        IsolatedModelController modelController,
        ProjectRegistry<ProjectInternal> projectRegistry
    ) {
        this.ownerScope = ownerScope;
        this.modelController = modelController;
        this.projectRegistry = projectRegistry;
    }

    @Override
    public <T> void register(String name, Class<T> type, Provider<T> provider) {
        register(key(name, type), work(provider));
    }

    @Override
    public <T> Map<IsolatedProject, Provider<T>> fromProjects(String name, Class<T> type, Collection<Project> projects) {
        return fromProjects(key(name, type), projects);
    }

    @Override
    public <T> Provider<T> fromBuild(String name, Class<T> type) {
        return fromBuild(key(name, type));
    }

    public <T> Map<String, Provider<T>> getProjectModels(IsolatedModelKey<T> key, Collection<String> projectPaths) {
        return projectPaths.stream()
            .map(projectRegistry::getProject)
            .collect(toMap(
                ProjectInternal::getPath,
                it -> modelController.obtain(ownerScope, key, scope(it))
            ));
    }

    private <T> void register(IsolatedModelKey<T> key, IsolatedModelWork<T> work) {
        modelController.register(ownerScope, key, work);
    }

    private <T> Provider<T> fromBuild(IsolatedModelKey<T> key) {
        return modelController.obtain(ownerScope, key, ownerScope.getBuildScope());
    }

    private <T> Map<IsolatedProject, Provider<T>> fromProjects(IsolatedModelKey<T> key, Collection<Project> projects) {
        Map<String, Provider<T>> byPath = getProjectModels(key, projects.stream().map(Project::getPath).collect(Collectors.toList()));
        return byPath.keySet().stream()
            .collect(Collectors.toMap(
                it -> projectRegistry.getProject(it).getIsolated(),
                byPath::get
            ));
    }

    private static IsolatedModelScope scope(ProjectInternal project) {
        return new IsolatedModelScope(project.getBuildPath(), project.getProjectPath());
    }

    private static <T> IsolatedModelKey<T> key(String name, Class<T> type) {
        return new DefaultIsolatedModelKey<>(name, type);
    }

    private static <T> IsolatedModelWork<T> work(Provider<T> provider) {
        return new DefaultIsolatedModelWork<>(provider);
    }
}
