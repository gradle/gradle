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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSetFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveVariantState;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.OriginArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.Set;

@ServiceScope(Scopes.Project.class)
public class ProjectDependencyResolver implements ComponentMetaDataResolver, DependencyToComponentIdResolver, ArtifactResolver, OriginArtifactSelector, ComponentResolvers {
    private final LocalComponentRegistry localComponentRegistry;
    private final ComponentIdentifierFactory componentIdentifierFactory;
    private final ProjectArtifactResolver artifactResolver;

    public ProjectDependencyResolver(LocalComponentRegistry localComponentRegistry,
                                     ComponentIdentifierFactory componentIdentifierFactory,
                                     ProjectArtifactResolver artifactResolver
    ) {
        this.localComponentRegistry = localComponentRegistry;
        this.componentIdentifierFactory = componentIdentifierFactory;
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
    public OriginArtifactSelector getArtifactSelector() {
        return this;
    }

    @Override
    public void resolve(DependencyMetadata dependency, VersionSelector acceptor, @Nullable VersionSelector rejector, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof ProjectComponentSelector) {
            ProjectComponentSelector selector = (ProjectComponentSelector) dependency.getSelector();
            ProjectComponentIdentifier projectId = componentIdentifierFactory.createProjectComponentIdentifier(selector);
            LocalComponentGraphResolveState component = localComponentRegistry.getComponent(projectId);
            if (component == null) {
                result.failed(new ModuleVersionResolveException(selector, () -> projectId + " not found."));
            } else {
                if (rejector != null && rejector.accept(component.getModuleVersionId().getVersion())) {
                    result.rejected(projectId, component.getModuleVersionId());
                } else {
                    result.resolved(component);
                }
            }
        }
    }

    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, final BuildableComponentResolveResult result) {
        if (isProjectModule(identifier)) {
            ProjectComponentIdentifier projectId = (ProjectComponentIdentifier) identifier;
            LocalComponentGraphResolveState component = localComponentRegistry.getComponent(projectId);
            if (component == null) {
                result.failed(new ModuleVersionResolveException(DefaultProjectComponentSelector.newSelector(projectId), () -> projectId + " not found."));
            } else {
                result.resolved(component);
            }
        }
    }

    @Override
    public boolean isFetchingMetadataCheap(ComponentIdentifier identifier) {
        return true;
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        if (isProjectModule(component.getId())) {
            throw new UnsupportedOperationException("Resolving artifacts by type is not yet supported for project modules");
        }
    }

    @Nullable
    @Override
    public ArtifactSet resolveArtifacts(final ComponentResolveMetadata component, ComponentArtifactResolveVariantState allVariants, Set<ResolvedVariant> legacyVariants, final ExcludeSpec exclusions, final ImmutableAttributes overriddenAttributes) {
        if (isProjectModule(component.getId())) {
            return ArtifactSetFactory.createFromVariantMetadata(component.getId(), allVariants, legacyVariants, component.getAttributesSchema(), overriddenAttributes);
        } else {
            return null;
        }
    }

    @Override
    public void resolveArtifact(ModuleVersionIdentifier ownerId, ComponentArtifactMetadata artifact, ModuleSources moduleSources, final BuildableArtifactResolveResult result) {
        if (isProjectModule(artifact.getComponentId())) {
            artifactResolver.resolveArtifact(ownerId, artifact, moduleSources, result);
        }
    }

    private boolean isProjectModule(ComponentIdentifier componentId) {
        return componentId instanceof ProjectComponentIdentifier;
    }
}
