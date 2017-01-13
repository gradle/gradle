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

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ComponentResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.DefaultArtifactResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultComponentArtifactsResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedArtifactResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedComponentResult;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DefaultArtifactResolutionQuery implements ArtifactResolutionQuery {
    private final ConfigurationContainerInternal configurationContainer;
    private final RepositoryHandler repositoryHandler;
    private final ResolveIvyFactory ivyFactory;
    private final GlobalDependencyResolutionRules metadataHandler;
    private final CacheLockingManager lockingManager;
    private final ComponentTypeRegistry componentTypeRegistry;

    private Set<ComponentIdentifier> componentIds = Sets.newLinkedHashSet();
    private Class<? extends Component> componentType;
    private Set<Class<? extends Artifact>> artifactTypes = Sets.newLinkedHashSet();

    public DefaultArtifactResolutionQuery(ConfigurationContainerInternal configurationContainer, RepositoryHandler repositoryHandler,
                                          ResolveIvyFactory ivyFactory, GlobalDependencyResolutionRules metadataHandler, CacheLockingManager lockingManager,
                                          ComponentTypeRegistry componentTypeRegistry) {
        this.configurationContainer = configurationContainer;
        this.repositoryHandler = repositoryHandler;
        this.ivyFactory = ivyFactory;
        this.metadataHandler = metadataHandler;
        this.lockingManager = lockingManager;
        this.componentTypeRegistry = componentTypeRegistry;
    }

    public ArtifactResolutionQuery forComponents(Iterable<? extends ComponentIdentifier> componentIds) {
        CollectionUtils.addAll(this.componentIds, componentIds);
        return this;
    }

    public ArtifactResolutionQuery forComponents(ComponentIdentifier... componentIds) {
        CollectionUtils.addAll(this.componentIds, componentIds);
        return this;
    }

    public ArtifactResolutionQuery withArtifacts(Class<? extends Component> componentType, Class<? extends Artifact>... artifactTypes) {
        if (this.componentType != null) {
            throw new IllegalStateException("Cannot specify component type multiple times.");
        }
        this.componentType = componentType;
        this.artifactTypes.addAll(Arrays.asList(artifactTypes));
        return this;
    }

    public ArtifactResolutionResult execute() {
        if (componentType == null) {
            throw new IllegalStateException("Must specify component type and artifacts to query.");
        }
        List<ResolutionAwareRepository> repositories = CollectionUtils.collect(repositoryHandler, Transformers.cast(ResolutionAwareRepository.class));
        ResolutionStrategyInternal resolutionStrategy = configurationContainer.detachedConfiguration().getResolutionStrategy();
        final ComponentResolvers componentResolvers = ivyFactory.create(resolutionStrategy, repositories, metadataHandler.getComponentMetadataProcessor());
        final ComponentMetaDataResolver componentMetaDataResolver = componentResolvers.getComponentResolver();
        final ArtifactResolver artifactResolver = new ErrorHandlingArtifactResolver(componentResolvers.getArtifactResolver());

        return lockingManager.useCache(new Factory<ArtifactResolutionResult>() {
            public ArtifactResolutionResult create() {
                Set<ComponentResult> componentResults = Sets.newHashSet();

                for (ComponentIdentifier componentId : componentIds) {
                    try {
                        ComponentIdentifier validId = validateComponentIdentifier(componentId);
                        componentResults.add(buildComponentResult(validId, componentMetaDataResolver, artifactResolver));
                    } catch (Throwable t) {
                        componentResults.add(new DefaultUnresolvedComponentResult(componentId, t));
                    }
                }

                return new DefaultArtifactResolutionResult(componentResults);
            }

            private ComponentIdentifier validateComponentIdentifier(ComponentIdentifier componentId) {
                if (componentId instanceof ModuleComponentIdentifier) {
                    return componentId;
                }
                if(componentId instanceof ProjectComponentIdentifier) {
                    throw new IllegalArgumentException(String.format("Cannot query artifacts for a project component (%s).", componentId.getDisplayName()));
                }

                throw new IllegalArgumentException(String.format("Cannot resolve the artifacts for component %s with unsupported type %s.", componentId.getDisplayName(), componentId.getClass().getName()));
            }
        });
    }

    private ComponentArtifactsResult buildComponentResult(ComponentIdentifier componentId, ComponentMetaDataResolver componentMetaDataResolver, ArtifactResolver artifactResolver) {
        BuildableComponentResolveResult moduleResolveResult = new DefaultBuildableComponentResolveResult();
        componentMetaDataResolver.resolve(componentId, new DefaultComponentOverrideMetadata(), moduleResolveResult);
        ComponentResolveMetadata component = moduleResolveResult.getMetaData();
        DefaultComponentArtifactsResult componentResult = new DefaultComponentArtifactsResult(component.getComponentId());
        for (Class<? extends Artifact> artifactType : artifactTypes) {
            addArtifacts(componentResult, artifactType, component, artifactResolver);
        }
        return componentResult;
    }

    private <T extends Artifact> void addArtifacts(DefaultComponentArtifactsResult artifacts, Class<T> type, ComponentResolveMetadata component, ArtifactResolver artifactResolver) {
        BuildableArtifactSetResolveResult artifactSetResolveResult = new DefaultBuildableArtifactSetResolveResult();
        artifactResolver.resolveArtifactsWithType(component, convertType(type), artifactSetResolveResult);

        for (ComponentArtifactMetadata artifactMetaData : artifactSetResolveResult.getResult()) {
            BuildableArtifactResolveResult resolveResult = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifactMetaData, component.getSource(), resolveResult);
            if (resolveResult.getFailure() != null) {
                artifacts.addArtifact(new DefaultUnresolvedArtifactResult(artifactMetaData.getId(), type, resolveResult.getFailure()));
            } else {
                artifacts.addArtifact(new DefaultResolvedArtifactResult(artifactMetaData.getId(), type, resolveResult.getResult()));
            }
        }
    }

    private <T extends Artifact> ArtifactType convertType(Class<T> requestedType) {
        return componentTypeRegistry.getComponentRegistration(componentType).getArtifactType(requestedType);
    }
}
