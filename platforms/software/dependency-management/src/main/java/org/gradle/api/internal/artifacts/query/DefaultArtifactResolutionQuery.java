/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.api.internal.artifacts.query;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ComponentResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyFactory;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory;
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.DefaultArtifactResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultComponentArtifactsResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedArtifactResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedComponentResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveState;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultArtifactResolutionQuery implements ArtifactResolutionQuery {
    private final ResolutionStrategyFactory resolutionStrategyFactory;
    private final RepositoriesSupplier repositoriesSupplier;
    private final ExternalModuleComponentResolverFactory externalResolverFactory;
    private final ComponentMetadataProcessorFactory componentMetadataProcessorFactory;
    private final ComponentTypeRegistry componentTypeRegistry;

    private final Set<ComponentIdentifier> componentIds = new LinkedHashSet<>();
    private Class<? extends Component> componentType;
    private final Set<Class<? extends Artifact>> artifactTypes = new LinkedHashSet<>();

    public DefaultArtifactResolutionQuery(
        ResolutionStrategyFactory resolutionStrategyFactory,
        RepositoriesSupplier repositoriesSupplier,
        ExternalModuleComponentResolverFactory externalResolverFactory,
        ComponentMetadataProcessorFactory componentMetadataProcessorFactory,
        ComponentTypeRegistry componentTypeRegistry
    ) {
        this.resolutionStrategyFactory = resolutionStrategyFactory;
        this.repositoriesSupplier = repositoriesSupplier;
        this.externalResolverFactory = externalResolverFactory;
        this.componentMetadataProcessorFactory = componentMetadataProcessorFactory;
        this.componentTypeRegistry = componentTypeRegistry;
    }

    @Override
    public ArtifactResolutionQuery forComponents(Iterable<? extends ComponentIdentifier> componentIds) {
        CollectionUtils.addAll(this.componentIds, componentIds);
        return this;
    }

    @Override
    public ArtifactResolutionQuery forComponents(ComponentIdentifier... componentIds) {
        CollectionUtils.addAll(this.componentIds, componentIds);
        return this;
    }

    @Override
    public ArtifactResolutionQuery forModule(@Nonnull String group, @Nonnull String name, @Nonnull String version) {
        componentIds.add(DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, name), version));
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ArtifactResolutionQuery withArtifacts(Class<? extends Component> componentType, Class<? extends Artifact>... artifactTypes) {
        return withArtifacts(componentType, Arrays.asList(artifactTypes));
    }

    @Override
    public ArtifactResolutionQuery withArtifacts(Class<? extends Component> componentType, Collection<Class<? extends Artifact>> artifactTypes) {
        if (this.componentType != null) {
            throw new IllegalStateException("Cannot specify component type multiple times.");
        }
        this.componentType = componentType;
        this.artifactTypes.addAll(artifactTypes);
        return this;
    }

    @Override
    public ArtifactResolutionResult execute() {
        if (componentType == null) {
            throw new IllegalStateException("Must specify component type and artifacts to query.");
        }

        List<? extends ResolutionAwareRepository> repositories = repositoriesSupplier.get();
        List<ResolutionAwareRepository> filteredRepositories = repositories.stream()
            .filter(repository -> {
                if (repository instanceof ContentFilteringRepository) {
                    ContentFilteringRepository cfr = (ContentFilteringRepository) repository;
                    // If the repository requires certain request attributes or requires certain configurations,
                    // it should not be used for ARQs.
                    return cfr.getRequiredAttributes() == null && cfr.getIncludedConfigurations() == null;
                }
                return true;
            })
            .collect(Collectors.toList());

        // We use a resolution strategy here in order to use the same defaults for dependency verification,
        // caching, etc. that a normal dependency resolution would use.
        ResolutionStrategyInternal resolutionStrategy = resolutionStrategyFactory.create();

        ComponentResolvers componentResolvers = externalResolverFactory.createResolvers(
            filteredRepositories,
            componentMetadataProcessorFactory,
            resolutionStrategy.getComponentSelection(),
            resolutionStrategy.isDependencyVerificationEnabled(),
            resolutionStrategy.getCachePolicy(),
            ImmutableAttributes.EMPTY,
            null
        );

        ComponentMetaDataResolver componentMetaDataResolver = componentResolvers.getComponentResolver();
        ArtifactResolver artifactResolver = new ErrorHandlingArtifactResolver(componentResolvers.getArtifactResolver());
        return createResult(componentMetaDataResolver, artifactResolver);
    }

    private ArtifactResolutionResult createResult(ComponentMetaDataResolver componentMetaDataResolver, ArtifactResolver artifactResolver) {
        Set<ComponentResult> componentResults = new HashSet<>();

        for (ComponentIdentifier componentId : componentIds) {
            try {
                ComponentIdentifier validId = validateComponentIdentifier(componentId);
                componentResults.add(buildComponentResult(validId, componentMetaDataResolver, artifactResolver));
            } catch (Exception t) {
                componentResults.add(new DefaultUnresolvedComponentResult(componentId, t));
            }
        }

        return new DefaultArtifactResolutionResult(componentResults);
    }

    private ComponentIdentifier validateComponentIdentifier(ComponentIdentifier componentId) {
        if (componentId instanceof ModuleComponentIdentifier) {
            return componentId;
        }
        if (componentId instanceof ProjectComponentIdentifier) {
            throw new IllegalArgumentException(String.format("Cannot query artifacts for a project component (%s).", componentId.getDisplayName()));
        }

        throw new IllegalArgumentException(String.format("Cannot resolve the artifacts for component %s with unsupported type %s.", componentId.getDisplayName(), componentId.getClass().getName()));
    }

    private ComponentArtifactsResult buildComponentResult(ComponentIdentifier componentId, ComponentMetaDataResolver componentMetaDataResolver, ArtifactResolver artifactResolver) {
        BuildableComponentResolveResult moduleResolveResult = new DefaultBuildableComponentResolveResult();
        componentMetaDataResolver.resolve(componentId, DefaultComponentOverrideMetadata.EMPTY, moduleResolveResult);
        ComponentArtifactResolveState componentState = moduleResolveResult.getState().prepareForArtifactResolution();
        DefaultComponentArtifactsResult componentResult = new DefaultComponentArtifactsResult(componentState.getId());
        for (Class<? extends Artifact> artifactType : artifactTypes) {
            moduleResolveResult.getModuleVersionId();
            addArtifacts(componentResult, artifactType, componentState, artifactResolver);
        }
        return componentResult;
    }

    private <T extends Artifact> void addArtifacts(DefaultComponentArtifactsResult artifacts, Class<T> type, ComponentArtifactResolveState componentState, ArtifactResolver artifactResolver) {
        BuildableArtifactSetResolveResult artifactSetResolveResult = new DefaultBuildableArtifactSetResolveResult();
        componentState.resolveArtifactsWithType(artifactResolver, convertType(type), artifactSetResolveResult);

        for (ComponentArtifactMetadata artifactMetaData : artifactSetResolveResult.getResult()) {
            BuildableArtifactResolveResult resolveResult = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(componentState.getArtifactMetadata(), artifactMetaData, resolveResult);
            try {
                artifacts.addArtifact(externalResolverFactory.verifiedArtifact(new DefaultResolvedArtifactResult(artifactMetaData.getId(), ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, Describables.of(componentState.getId().getDisplayName()), type, resolveResult.getResult().getFile())));
            } catch (Exception e) {
                artifacts.addArtifact(new DefaultUnresolvedArtifactResult(artifactMetaData.getId(), type, e));
            }
        }
    }

    private <T extends Artifact> ArtifactType convertType(Class<T> requestedType) {
        return componentTypeRegistry.getComponentRegistration(componentType).getArtifactType(requestedType);
    }
}
