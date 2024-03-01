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

import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependencyConstraint;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyPublicationResolver;
import org.gradle.internal.Actions;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult.eachElement;

/**
 * A {@link ComponentDependencyResolver} backed by a resolution result.
 */
public class ResolutionBackedComponentDependencyResolver implements ComponentDependencyResolver {

    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ProjectDependencyPublicationResolver projectDependencyResolver;

    private final Map<ModuleIdentifier, String> resolvedComponentVersions;

    public ResolutionBackedComponentDependencyResolver(
        ResolvedComponentResult root,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        ProjectDependencyPublicationResolver projectDependencyResolver
    ) {
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.projectDependencyResolver = projectDependencyResolver;

        this.resolvedComponentVersions = getResolvedComponentVersions(root);
    }

    @Nullable
    @Override
    public ResolvedCoordinates resolveComponentCoordinates(ExternalDependency dependency) {
        return resolveModuleComponentCoordinates(dependency.getGroup(), dependency.getName());
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(ProjectDependency dependency) {
        // TODO: Eventually resolved project coordinates should be made available
        //       via the resolution result.
        Path identityPath = ((ProjectDependencyInternal) dependency).getIdentityPath();
        ModuleVersionIdentifier identifier = projectDependencyResolver.resolveComponent(ModuleVersionIdentifier.class, identityPath);
        return ResolvedCoordinates.create(identifier);
    }

    @Nullable
    @Override
    public ResolvedCoordinates resolveComponentCoordinates(DependencyConstraint dependency) {
        assert !(dependency instanceof DefaultProjectDependencyConstraint);
        return resolveModuleComponentCoordinates(dependency.getGroup(), dependency.getName());
    }

    @Override
    public ResolvedCoordinates resolveComponentCoordinates(DefaultProjectDependencyConstraint dependency) {
        return resolveComponentCoordinates(dependency.getProjectDependency());
    }

    @Nullable
    private ResolvedCoordinates resolveModuleComponentCoordinates(String group, String name) {
        String resolvedVersion = resolvedComponentVersions.get(
            moduleIdentifierFactory.module(group, name)
        );

        if (resolvedVersion == null) {
            return null;
        }

        return ResolvedCoordinates.create(group, name, resolvedVersion);
    }

    private static Map<ModuleIdentifier, String> getResolvedComponentVersions(ResolvedComponentResult root) {
        Map<ModuleIdentifier, String> resolvedComponentVersions = new HashMap<>();

        eachElement(root, (Action<? super ResolvedComponentResult>) component -> {

            ComponentIdentifier componentId = component.getId();
            if (componentId instanceof ModuleComponentIdentifier) {
                ModuleComponentIdentifier moduleId = (ModuleComponentIdentifier) componentId;
                resolvedComponentVersions.put(moduleId.getModuleIdentifier(), moduleId.getVersion());
            }

        }, Actions.doNothing(), new HashSet<>());

        return resolvedComponentVersions;
    }
}
