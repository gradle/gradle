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
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ComponentResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.internal.component.model.DefaultDependencyMetaData;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.*;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.*;
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

    // TODO:DAZ This is ugly and needs a major cleanup and unit tests
    public ArtifactResolutionResult execute() {
        if (componentType == null) {
            throw new IllegalStateException("Must specify component type and artifacts to query.");
        }
        List<ResolutionAwareRepository> repositories = CollectionUtils.collect(repositoryHandler, Transformers.cast(ResolutionAwareRepository.class));
        ConfigurationInternal configuration = configurationContainer.detachedConfiguration();
        final RepositoryChain repositoryChain = ivyFactory.create(configuration, repositories, metadataHandler.getComponentMetadataProcessor());
        final ArtifactResolver artifactResolver = new ErrorHandlingArtifactResolver(repositoryChain.getArtifactResolver());

        return lockingManager.useCache("resolve artifacts", new Factory<ArtifactResolutionResult>() {
            public ArtifactResolutionResult create() {
                Set<ComponentResult> componentResults = Sets.newHashSet();

                for (ComponentIdentifier componentId : componentIds) {
                    if (!(componentId instanceof ModuleComponentIdentifier)) {
                        throw new IllegalArgumentException(String.format("Cannot resolve the artifacts for component %s with unsupported type %s.", componentId.getDisplayName(), componentId.getClass().getName()));
                    }
                    ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) componentId;
                    try {
                        componentResults.add(buildComponentResult(moduleComponentId, repositoryChain, artifactResolver));
                    } catch (Throwable t) {
                        componentResults.add(new DefaultUnresolvedComponentResult(moduleComponentId, t));
                    }
                }

                return new DefaultArtifactResolutionResult(componentResults);
            }
        });
    }

    private ComponentArtifactsResult buildComponentResult(ModuleComponentIdentifier moduleComponentId, RepositoryChain repositoryChain, ArtifactResolver artifactResolver) {
        BuildableComponentResolveResult moduleResolveResult = new DefaultBuildableComponentResolveResult();
        repositoryChain.getDependencyResolver().resolve(new DefaultDependencyMetaData(moduleComponentId), moduleResolveResult);
        ComponentResolveMetaData component = moduleResolveResult.getMetaData();
        DefaultComponentArtifactsResult componentResult = new DefaultComponentArtifactsResult(component.getComponentId());
        for (Class<? extends Artifact> artifactType : artifactTypes) {
            addArtifacts(componentResult, artifactType, component, artifactResolver);
        }
        return componentResult;
    }

    private <T extends Artifact> void addArtifacts(DefaultComponentArtifactsResult artifacts, Class<T> type, ComponentResolveMetaData component, ArtifactResolver artifactResolver) {
        BuildableArtifactSetResolveResult artifactSetResolveResult = new DefaultBuildableArtifactSetResolveResult();
        artifactResolver.resolveModuleArtifacts(component, convertType(type), artifactSetResolveResult);

        for (ComponentArtifactMetaData artifactMetaData : artifactSetResolveResult.getArtifacts()) {
            BuildableArtifactResolveResult resolveResult = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifactMetaData, component.getSource(), resolveResult);
            if (resolveResult.getFailure() != null) {
                artifacts.addArtifact(new DefaultUnresolvedArtifactResult(type, resolveResult.getFailure()));
            } else {
                artifacts.addArtifact(new DefaultResolvedArtifactResult(type, resolveResult.getFile()));
            }
        }
    }

    private <T extends Artifact> ArtifactType convertType(Class<T> requestedType) {
        return componentTypeRegistry.getComponentRegistration(componentType).getArtifactType(requestedType);
    }
}
