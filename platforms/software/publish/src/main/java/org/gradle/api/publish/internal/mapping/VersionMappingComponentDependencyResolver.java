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
import org.gradle.internal.Actions;
import org.gradle.internal.component.local.model.ProjectComponentSelectorInternal;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult.eachElement;

/**
 * A {@link VariantDependencyResolver} that performs version mapping.
 *
 * @see org.gradle.api.publish.VersionMappingStrategy
 */
public class VersionMappingComponentDependencyResolver implements ComponentDependencyResolver {

    private final ProjectDependencyPublicationResolver projectDependencyResolver;
    private final ResolvedComponentResult root;

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
        Path identityPath = ((ProjectDependencyInternal) dependency).getIdentityPath();
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
        final Set<ResolvedComponentResult> resolvedComponentResults = new LinkedHashSet<>();
        eachElement(root, Actions.doNothing(), Actions.doNothing(), resolvedComponentResults);

        for (ResolvedComponentResult selected : resolvedComponentResults) {
            ModuleVersionIdentifier moduleVersion = selected.getModuleVersion();
            if (moduleVersion != null && group.equals(moduleVersion.getGroup()) && module.equals(moduleVersion.getName())) {
                return moduleVersion;
            }
        }

        final Set<DependencyResult> allDependencies = new LinkedHashSet<>();
        eachElement(root, Actions.doNothing(), allDependencies::add, new HashSet<>());

        // If we reach this point it means we have a dependency which doesn't belong to the resolution result
        // Which can mean two things:
        // 1. the graph used to get the resolved version has nothing to do with the dependencies we're trying to get versions for (likely user error)
        // 2. the graph contains first-level dependencies which have been substituted (likely) so we're going to iterate on dependencies instead
        for (DependencyResult dependencyResult : allDependencies) {
            if (dependencyResult instanceof ResolvedDependencyResult) {
                ComponentSelector rcs = dependencyResult.getRequested();
                ResolvedComponentResult selected = ((ResolvedDependencyResult) dependencyResult).getSelected();
                if (rcs instanceof ModuleComponentSelector) {
                    ModuleComponentSelector requested = (ModuleComponentSelector) rcs;
                    if (requested.getGroup().equals(group) && requested.getModule().equals(module)) {
                        return getModuleVersionId(selected);
                    }
                } else if (rcs instanceof ProjectComponentSelector) {
                    ProjectComponentSelectorInternal pcs = (ProjectComponentSelectorInternal) rcs;
                    if (pcs.getIdentityPath().equals(identityPath)) {
                        return getModuleVersionId(selected);
                    }
                }
            }
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
}
