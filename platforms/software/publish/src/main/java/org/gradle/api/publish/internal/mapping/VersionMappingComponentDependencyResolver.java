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

package org.gradle.api.publish.internal.mapping;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal;
import org.gradle.internal.lazy.Lazy;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * A {@link VariantDependencyResolver} that performs version mapping.
 *
 * @see org.gradle.api.publish.VersionMappingStrategy
 */
public class VersionMappingComponentDependencyResolver implements ComponentDependencyResolver {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final Lazy<ResolvedGraph> lazyGraph;

    public VersionMappingComponentDependencyResolver(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ResolvedComponentResult root
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.lazyGraph = Lazy.unsafe().of(() -> buildResolvedGraph(root));
    }

    @Nullable
    @Override
    public ResolvedCoordinates resolveComponentCoordinates(ExternalDependency dependency) {
        return resolveModule(dependency.getGroup(), dependency.getName());
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(ProjectDependency dependency) {
        Path identityPath = ((ProjectDependencyInternal) dependency).getTargetProjectIdentity().getBuildTreePath();
        ModuleVersionIdentifier coordinates = projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath);
        ModuleVersionIdentifier resolved = maybeResolveVersion(coordinates.getGroup(), coordinates.getName(), identityPath);
        return ResolvedCoordinates.create(resolved != null ? resolved : coordinates);
    }

    @Nullable
    @Override
    public ResolvedCoordinates resolveComponentCoordinates(DependencyConstraint dependency) {
        return resolveModule(dependency.getGroup(), dependency.getName());
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(DefaultProjectDependencyConstraint dependency) {
        return resolveComponentCoordinates(dependency.getProjectDependency());
    }

    @Nullable
    public ResolvedCoordinates resolveModule(String group, String name) {
        ModuleVersionIdentifier resolved = maybeResolveVersion(group, name, null);
        if (resolved != null) {
            return ResolvedCoordinates.create(resolved);
        }
        return null;
    }

    @Nullable
    public ModuleVersionIdentifier maybeResolveVersion(String group, String module, @Nullable Path identityPath) {
        ModuleIdentifier moduleId = DefaultModuleIdentifier.newId(group, module);
        ResolvedGraph graph = lazyGraph.get();

        ModuleVersionIdentifier moduleVersion = graph.resolvedModuleVersions().get(moduleId);
        if (moduleVersion != null) {
            return moduleVersion;
        }

        // If we reach this point it means we have a dependency which doesn't belong to the resolution result
        // Which can mean two things:
        // 1. the graph used to get the resolved version has nothing to do with the dependencies we're trying to get versions for (likely user error)
        // 2. the graph contains first-level dependencies which have been substituted (likely) so we're going to look at what the dependencies requested instead
        if (identityPath != null) {
            ResolvedComponentResult selected = graph.byRequestedProject().get(identityPath);
            if (selected != null) {
                return getModuleVersionId(selected);
            }
        }

        ResolvedComponentResult selected = graph.byRequestedModule().get(moduleId);
        if (selected != null) {
            return getModuleVersionId(selected);
        }

        return null;
    }

    @Nullable
    private ModuleVersionIdentifier getModuleVersionId(ResolvedComponentResult selected) {
        // Match found - need to make sure that if the selection is a project, we use its publication identity
        if (selected.getId() instanceof ProjectComponentIdentifier) {
            Path identityPath = ((ProjectComponentIdentifierInternal) selected.getId()).getIdentityPath();
            return projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath);
        }
        return selected.getModuleVersion();
    }

    private static ResolvedGraph buildResolvedGraph(ResolvedComponentResult root) {
        ImmutableMap.Builder<ModuleIdentifier, ModuleVersionIdentifier> resolvedModuleVersions = ImmutableMap.builder();
        ImmutableMap.Builder<ModuleIdentifier, ResolvedComponentResult> byRequestedModule = ImmutableMap.builder();
        ImmutableMap.Builder<Path, ResolvedComponentResult> byRequestedProject = ImmutableMap.builder();

        Set<ResolvedComponentResult> visited = new HashSet<>();
        Deque<ResolvedComponentResult> queue = new ArrayDeque<>();
        visited.add(root);
        queue.add(root);

        while (!queue.isEmpty()) {
            ResolvedComponentResult component = queue.remove();
            ModuleVersionIdentifier mvId = component.getModuleVersion();
            if (mvId != null) {
                resolvedModuleVersions.put(mvId.getModule(), mvId);
            }

            for (DependencyResult dependencyResult : component.getDependencies()) {
                if (dependencyResult instanceof ResolvedDependencyResult rdr) {
                    ComponentSelector requested = rdr.getRequested();
                    ResolvedComponentResult selected = rdr.getSelected();

                    if (requested instanceof ModuleComponentSelector mcs) {
                        byRequestedModule.put(mcs.getModuleIdentifier(), selected);
                    } else if (requested instanceof ProjectComponentSelectorInternal pcs) {
                        byRequestedProject.put(pcs.getIdentityPath(), selected);
                    }
                    if (visited.add(selected)) {
                        queue.add(selected);
                    }
                }
            }
        }

        return new ResolvedGraph(
            resolvedModuleVersions.buildKeepingLast(),
            byRequestedModule.buildKeepingLast(),
            byRequestedProject.buildKeepingLast()
        );
    }

    private record ResolvedGraph(
        ImmutableMap<ModuleIdentifier, ModuleVersionIdentifier> resolvedModuleVersions,
        ImmutableMap<ModuleIdentifier, ResolvedComponentResult> byRequestedModule,
        ImmutableMap<Path, ResolvedComponentResult> byRequestedProject
    ) { }

}
