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
package org.gradle.api.internal.artifacts.resolution;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.ArtifactResolutionQuery;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.result.*;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DefaultDependencyMetaData;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.*;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

// TODO:DAZ Not sure where this should live
public class DefaultArtifactResolutionQuery implements ArtifactResolutionQuery {
    private final ConfigurationContainerInternal configurationContainer;
    private final RepositoryHandler repositoryHandler;
    private final ResolveIvyFactory ivyFactory;
    private final ModuleMetadataProcessor metadataProcessor;
    private final CacheLockingManager lockingManager;

    private Set<ComponentIdentifier> componentIds = Sets.newLinkedHashSet();
    private Class<? extends Component> componentType;
    private Set<Class<? extends Artifact>> artifactTypes = Sets.newLinkedHashSet();

    public DefaultArtifactResolutionQuery(ConfigurationContainerInternal configurationContainer, RepositoryHandler repositoryHandler,
                                          ResolveIvyFactory ivyFactory, ModuleMetadataProcessor metadataProcessor, CacheLockingManager lockingManager) {
        this.configurationContainer = configurationContainer;
        this.repositoryHandler = repositoryHandler;
        this.ivyFactory = ivyFactory;
        this.metadataProcessor = metadataProcessor;
        this.lockingManager = lockingManager;
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
        this.componentType = componentType;
        this.artifactTypes.addAll(Arrays.asList(artifactTypes));
        return this;
    }

    // TODO:DAZ This is ugly and needs a major cleanup and unit tests
    // TODO:DAZ Also need to add a 'result' layer to the api
    public ArtifactResolutionResult execute() {
        List<ResolutionAwareRepository> repositories = CollectionUtils.collect(repositoryHandler, Transformers.cast(ResolutionAwareRepository.class));
        ConfigurationInternal configuration = configurationContainer.detachedConfiguration();
        final RepositoryChain repositoryChain = ivyFactory.create(configuration, repositories, metadataProcessor);
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

    private ResolvedComponentArtifactsResult buildComponentResult(ModuleComponentIdentifier moduleComponentId, RepositoryChain repositoryChain, ArtifactResolver artifactResolver) {
        BuildableComponentResolveResult moduleResolveResult = new DefaultBuildableComponentResolveResult();
        repositoryChain.getDependencyResolver().resolve(new DefaultDependencyMetaData(moduleComponentId, true), moduleResolveResult);
        ComponentMetaData component = moduleResolveResult.getMetaData();
        DefaultResolvedComponentArtifactsResult componentResult = new DefaultResolvedComponentArtifactsResult(component.getComponentId());
        for (Class<? extends Artifact> artifactType : artifactTypes) {
            addArtifacts(componentResult, artifactType, component, artifactResolver);
        }
        return componentResult;
    }

    private <T extends Artifact> void addArtifacts(DefaultResolvedComponentArtifactsResult artifacts, Class<T> type, ComponentMetaData component, ArtifactResolver artifactResolver) {
        ArtifactResolveContext context = new ArtifactTypeResolveContext(type);
        BuildableArtifactSetResolveResult artifactSetResolveResult = new DefaultBuildableArtifactSetResolveResult();
        artifactResolver.resolveModuleArtifacts(component, context, artifactSetResolveResult);

        for (ComponentArtifactMetaData artifactMetaData : artifactSetResolveResult.getArtifacts()) {
            BuildableArtifactResolveResult resolveResult = new DefaultBuildableArtifactResolveResult();
            artifactResolver.resolveArtifact(artifactMetaData, component.getSource(), resolveResult);
            if (resolveResult.getFailure() != null) {
                artifacts.addArtifact(type, new DefaultUnresolvedArtifactResult(resolveResult.getFailure()));
            } else {
                artifacts.addArtifact(type, new DefaultResolvedArtifactResult(resolveResult.getFile()));
            }
        }
    }
}
