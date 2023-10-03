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

package org.gradle.internal.shareddata;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.shareddata.ProjectSharedData;
import org.gradle.api.specs.Spec;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@NonNullApi
public class DefaultProjectSharedData implements ProjectSharedData {
    private final ProjectInternal usedInProject;
    private final SharedDataRegistry globalSharedData;
    private final ProjectStateRegistry projectStateRegistry;
    private final ProviderFactory providerFactory;

    @Inject
    public DefaultProjectSharedData(ProjectInternal usedInProject, SharedDataRegistry globalSharedData, ProjectStateRegistry projectStateRegistry, ProviderFactory providerFactory) {
        this.usedInProject = usedInProject;
        this.globalSharedData = globalSharedData;
        this.projectStateRegistry = projectStateRegistry;
        this.providerFactory = providerFactory;
    }

    @Override
    public <T> void register(Class<T> dataType, @Nullable String dataIdentifier, Provider<T> dataProvider) {
        globalSharedData.registerSharedDataProducer(usedInProject, dataType, dataIdentifier, dataProvider);
    }

    @Override
    public <T> Provider<T> obtain(Class<T> dataType, @Nullable String dataIdentifier, SingleSourceIdentifier dataSourceIdentifier) {
        // TODO: check composite builds
        // TODO: should not wait for the other project(s) to be configured, unless `.get()` is called
        projectStateRegistry.stateFor(dataSourceIdentifier.getSourceProjectIdentitiyPath()).ensureConfigured();

        return globalSharedData.obtainData(usedInProject, dataType, dataIdentifier, dataSourceIdentifier);
    }

    @Override
    public <T> Provider<T> obtain(Class<T> dataType, SingleSourceIdentifier dataSourceIdentifier) {
        return obtain(dataType, null, dataSourceIdentifier);
    }

    @Override
    public <T> Provider<Map<String, ? extends T>> obtain(Class<T> dataType, @Nullable String dataIdentifier, MultipleSourcesIdentifier dataSourceIdentifier) {
        return providerFactory.provider(() -> {
            Collection<Path> paths = dataSourceIdentifier.getSourceProjectIdentityPaths();
            return paths.stream().collect(LinkedHashMap::new,
                (map, path) -> {
                    Provider<T> dataFromProject = obtain(dataType, dataIdentifier, new DefaultSingleSourceIdentifier(path));
                    if (dataFromProject.isPresent()) {
                        map.put(path.getPath(), dataFromProject.get());
                    }
                },
                LinkedHashMap::putAll
            );
        });
    }

    @Override
    public SingleSourceIdentifier fromProject(Project project) {
        return new DefaultSingleSourceIdentifier(projectStateRegistry.stateFor(project).getIdentityPath());
    }

    @Override
    public SingleSourceIdentifier fromProject(ProjectComponentIdentifier projectComponentIdentifier) {
        return new DefaultSingleSourceIdentifier(projectStateRegistry.stateFor(projectComponentIdentifier).getIdentityPath());
    }

    @Override
    public MultipleSourcesIdentifier fromProjects(Collection<Project> projects) {
        return () -> projects.stream().map(project -> projectStateRegistry.stateFor(project).getIdentityPath()).collect(Collectors.toList());
    }

    @Override
    public MultipleSourcesIdentifier fromAllProjects(Spec<? super Project> filterProjects) {
        return () -> projectStateRegistry.getAllProjects().stream().filter(it -> filterProjects.isSatisfiedBy(it.getMutableModel())).map(ProjectState::getIdentityPath).collect(Collectors.toList());
    }

    @Override
    public MultipleSourcesIdentifier fromResolutionResults(Configuration configuration) {
        return () -> {
            Set<Path> result = new LinkedHashSet<>();
            class Visitor {
                void visitDependency(DependencyResult dependency) {
                    if (!(dependency instanceof ResolvedDependencyResult)) {
                        return;
                    }
                    ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
                    ResolvedComponentResult selected = resolvedDependency.getSelected();
                    ComponentIdentifier selectedId = selected.getId();
                    if (selectedId instanceof ProjectComponentIdentifier) {
                        result.add(projectStateRegistry.stateFor((ProjectComponentIdentifier) selectedId).getIdentityPath());
                    }
                    selected.getDependencies().forEach(this::visitDependency);
                }
            }
            // todo better way to link the providers?
            Provider<ResolvedComponentResult> root = configuration.getIncoming().getResolutionResult().getRootComponent();
            root.get().getDependencies().forEach(new Visitor()::visitDependency);
            return result;
        };
    }
}
