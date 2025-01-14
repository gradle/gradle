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
import org.gradle.api.isolated.models.ProjectModelScope;
import org.gradle.api.isolated.models.ProjectScopeModelRequest;
import org.gradle.api.provider.Provider;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultProjectModelScope implements ProjectModelScope {

    private final ProjectModelScopeIdentifier ownerScope;
    private final ProjectModelController projectModelController;

    public DefaultProjectModelScope(
        ProjectInternal owner,
        ProjectModelController projectModelController
    ) {
        this.ownerScope = projectScopeFrom(owner);
        this.projectModelController = projectModelController;
    }

    @Override
    public <T> void register(Class<T> modelType, Provider<T> modelProducer) {
        register(keyFor(modelType), modelProducerFrom(modelType, modelProducer));
    }

    @Override
    public <T> ProjectScopeModelRequest<T> request(Class<T> modelType, Collection<Project> projects) {
        return request(keyFor(modelType), targetProjects(projects));
    }

    private <T> ProjectScopeModelRequest<T> request(IsolatedModelKey<T> key, List<ProjectModelScopeIdentifier> projects) {
        return new DefaultProjectScopeModelRequest<>(projectModelController, ownerScope, key, projects);
    }

    private static List<ProjectModelScopeIdentifier> targetProjects(Collection<Project> projects) {
        // TODO: avoid eagerly processing all projects

        return projects.stream()
            .sorted()
            .distinct()
            .map(p -> projectScopeFrom((ProjectInternal) p))
            .collect(Collectors.toList());
    }

    private <T> void register(IsolatedModelKey<T> key, IsolatedModelProducer<T> work) {
        projectModelController.register(ownerScope, key, work);
    }

    private static <T> IsolatedModelKey<T> keyFor(Class<T> type) {
        // TODO: constrain possible types
        String name = type.getName();
        return new DefaultIsolatedModelKey<>(name, type);
    }

    private static <T> IsolatedModelProducer<T> modelProducerFrom(Class<T> modelType, Provider<T> provider) {
        return new DefaultIsolatedModelProducer<>(modelType, provider);
    }

    private static ProjectModelScopeIdentifier projectScopeFrom(ProjectInternal project) {
        return new ProjectModelScopeIdentifier(project.getBuildPath(), project.getProjectPath());
    }
}
