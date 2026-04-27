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

import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.internal.artifacts.ProjectComponentIdentifierInternal;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link VariantDependencyResolver} that performs version mapping.
 *
 * @see org.gradle.api.publish.VersionMappingStrategy
 */
public class VersionMappingComponentDependencyResolver implements ComponentDependencyResolver {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ResolvedComponentResult root;

    private boolean indexed;
    private Map<ModuleKey, ModuleVersionIdentifier> selectedByCoordinates;
    private Map<ModuleKey, ModuleVersionIdentifier> selectedByRequestedModule;
    private Map<Path, ModuleVersionIdentifier> selectedByRequestedProjectIdentity;

    public VersionMappingComponentDependencyResolver(
        ProjectDependencyPublicationResolver projectDependencyResolver,
        ResolvedComponentResult root
    ) {
        this.projectDependencyResolver = projectDependencyResolver;
        this.root = root;
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
        ensureIndexed();

        ModuleKey key = new ModuleKey(group, module);
        ModuleVersionIdentifier resolved = selectedByCoordinates.get(key);
        if (resolved != null) {
            return resolved;
        }

        // If we reach this point it means we have a dependency which doesn't belong to the resolution result.
        // Which can mean two things:
        // 1. the graph used to get the resolved version has nothing to do with the dependencies we're trying to get versions for (likely user error)
        // 2. the graph contains first-level dependencies which have been substituted (likely) so we fall back to looking up the requested coordinates
        resolved = selectedByRequestedModule.get(key);
        if (resolved != null) {
            return resolved;
        }

        if (identityPath != null) {
            return selectedByRequestedProjectIdentity.get(identityPath);
        }

        return null;
    }

    /**
     * Lazily indexes the resolution graph the first time it is queried.
     *
     * <p>Without this, every call to {@link #maybeResolveVersion} would walk the entire graph
     * twice, leading to {@code O(graphSize × dependencies)} work when generating module/POM
     * metadata for components with large dependency graphs.
     */
    private void ensureIndexed() {
        if (indexed) {
            return;
        }
        Map<ModuleKey, ModuleVersionIdentifier> byCoordinates = new HashMap<>();
        Map<ModuleKey, ModuleVersionIdentifier> byRequestedModule = new HashMap<>();
        Map<Path, ModuleVersionIdentifier> byRequestedProjectIdentity = new HashMap<>();
        indexGraph(root, byCoordinates, byRequestedModule, byRequestedProjectIdentity, new HashSet<>());

        this.selectedByCoordinates = byCoordinates;
        this.selectedByRequestedModule = byRequestedModule;
        this.selectedByRequestedProjectIdentity = byRequestedProjectIdentity;
        this.indexed = true;
    }

    private void indexGraph(
        ResolvedComponentResult node,
        Map<ModuleKey, ModuleVersionIdentifier> byCoordinates,
        Map<ModuleKey, ModuleVersionIdentifier> byRequestedModule,
        Map<Path, ModuleVersionIdentifier> byRequestedProjectIdentity,
        Set<ResolvedComponentResult> visited
    ) {
        if (!visited.add(node)) {
            return;
        }

        ModuleVersionIdentifier moduleVersion = node.getModuleVersion();
        if (moduleVersion != null) {
            byCoordinates.putIfAbsent(new ModuleKey(moduleVersion.getGroup(), moduleVersion.getName()), moduleVersion);
        }

        for (DependencyResult dependency : node.getDependencies()) {
            if (!(dependency instanceof ResolvedDependencyResult)) {
                continue;
            }
            ResolvedDependencyResult resolved = (ResolvedDependencyResult) dependency;
            ResolvedComponentResult selected = resolved.getSelected();
            ComponentSelector requested = resolved.getRequested();

            if (requested instanceof ModuleComponentSelector) {
                ModuleComponentSelector mcs = (ModuleComponentSelector) requested;
                ModuleKey key = new ModuleKey(mcs.getGroup(), mcs.getModule());
                if (!byRequestedModule.containsKey(key)) {
                    ModuleVersionIdentifier id = getModuleVersionId(selected);
                    if (id != null) {
                        byRequestedModule.put(key, id);
                    }
                }
            } else if (requested instanceof ProjectComponentSelector) {
                Path requestedIdentityPath = ((ProjectComponentSelectorInternal) requested).getIdentityPath();
                if (!byRequestedProjectIdentity.containsKey(requestedIdentityPath)) {
                    ModuleVersionIdentifier id = getModuleVersionId(selected);
                    if (id != null) {
                        byRequestedProjectIdentity.put(requestedIdentityPath, id);
                    }
                }
            }

            indexGraph(selected, byCoordinates, byRequestedModule, byRequestedProjectIdentity, visited);
        }
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

    private static final class ModuleKey {
        private final String group;
        private final String module;

        ModuleKey(String group, String module) {
            this.group = group;
            this.module = module;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ModuleKey)) {
                return false;
            }
            ModuleKey other = (ModuleKey) o;
            return group.equals(other.group) && module.equals(other.module);
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, module);
        }
    }
}
