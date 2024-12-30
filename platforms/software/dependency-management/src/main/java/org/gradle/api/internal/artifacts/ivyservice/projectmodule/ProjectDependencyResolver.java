/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;

@ServiceScope(Scope.Project.class)
public class ProjectDependencyResolver implements ComponentMetaDataResolver, DependencyToComponentIdResolver, ArtifactResolver, ComponentResolvers {
    private final LocalComponentRegistry localComponentRegistry;
    private final ProjectArtifactResolver artifactResolver;

    public ProjectDependencyResolver(LocalComponentRegistry localComponentRegistry, ProjectArtifactResolver artifactResolver) {
        this.localComponentRegistry = localComponentRegistry;
        this.artifactResolver = artifactResolver;
    }

    @Override
    public ArtifactResolver getArtifactResolver() {
        return this;
    }

    @Override
    public DependencyToComponentIdResolver getComponentIdResolver() {
        return this;
    }

    @Override
    public ComponentMetaDataResolver getComponentResolver() {
        return this;
    }

    @Override
    public void resolve(ComponentSelector selector, ComponentOverrideMetadata overrideMetadata, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result) {
        if (selector instanceof DefaultProjectComponentSelector) {
            DefaultProjectComponentSelector projectSelector = (DefaultProjectComponentSelector) selector;
            ProjectComponentIdentifier projectId = projectSelector.toIdentifier();
            LocalComponentGraphResolveState component = localComponentRegistry.getComponent(projectId);
            if (rejector != null && rejector.accept(component.getModuleVersionId().getVersion())) {
                result.rejected(projectId, component.getModuleVersionId());
            } else {
                result.resolved(component, ComponentGraphSpecificResolveState.EMPTY_STATE);
            }
        }
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, final BuildableComponentResolveResult result) {
        if (isProjectModule(identifier)) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) identifier;
            LocalComponentGraphResolveState component = localComponentRegistry.getComponent(projectId);
            result.resolved(component, ComponentGraphSpecificResolveState.EMPTY_STATE);
        }
    }

    @Override
    public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
        return true;
    }

    @Override
    public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        if (isProjectModule(component.getId())) {
            throw new UnsupportedOperationException("Resolving artifacts by type is not yet supported for project modules");
        }
    }

    @Override
    public void resolveArtifact(ComponentArtifactResolveMetadata component, ComponentArtifactMetadata artifact, BuildableArtifactResolveResult result) {
        if (isProjectModule(artifact.getComponentId())) {
            artifactResolver.resolveArtifact(component, artifact, result);
        }
    }

    private static boolean isProjectModule(ComponentIdentifier componentId) {
        return componentId instanceof ProjectComponentIdentifier;
    }
}
