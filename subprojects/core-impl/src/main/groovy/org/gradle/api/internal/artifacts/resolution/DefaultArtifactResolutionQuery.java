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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.resolution.*;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.configurations.ConfigurationContainerInternal;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.DefaultDependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.util.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class DefaultArtifactResolutionQuery implements ArtifactResolutionQuery {
    private final ConfigurationContainerInternal configurationContainer;
    private final RepositoryHandler repositoryHandler;
    private final ResolveIvyFactory ivyFactory;
    private final ModuleMetadataProcessor metadataProcessor;
    private final CacheLockingManager lockingManager;

    private Set<ComponentIdentifier> componentIds = Sets.newHashSet();
    private Class<? extends SoftwareComponent<?>> componentType;
    private Set<Class<? extends SoftwareArtifact>> artifactTypes = Sets.newHashSet();

    public DefaultArtifactResolutionQuery(ConfigurationContainerInternal configurationContainer, RepositoryHandler repositoryHandler,
                                          ResolveIvyFactory ivyFactory, ModuleMetadataProcessor metadataProcessor, CacheLockingManager lockingManager) {
        this.configurationContainer = configurationContainer;
        this.repositoryHandler = repositoryHandler;
        this.ivyFactory = ivyFactory;
        this.metadataProcessor = metadataProcessor;
        this.lockingManager = lockingManager;
    }

    public ArtifactResolutionQuery forComponents(Set<? extends ComponentIdentifier> componentIds) {
        this.componentIds.addAll(componentIds);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends SoftwareArtifact, U extends SoftwareComponent<T>> ArtifactResolutionQuery withArtifacts(Class<U> componentType, Class<T>... artifactTypes) {
        this.componentType = componentType;
        if (artifactTypes.length == 0) {
            this.artifactTypes = (Set) Sets.newHashSet(JvmLibrarySourcesArtifact.class, JvmLibraryJavadocArtifact.class);
        } else {
            this.artifactTypes.addAll(Arrays.asList(artifactTypes));
        }
        return this;
    }

    public ArtifactResolutionQueryResult execute() {
        List<ResolutionAwareRepository> repositories = CollectionUtils.collect(repositoryHandler, Transformers.cast(ResolutionAwareRepository.class));
        ConfigurationInternal configuration = configurationContainer.detachedConfiguration();
        final RepositoryChain repositoryChain = ivyFactory.create(configuration, repositories, metadataProcessor);

        return lockingManager.useCache("resolve artifacts", new Factory<ArtifactResolutionQueryResult>() {
            public ArtifactResolutionQueryResult create() {
                Set<JvmLibrary> jvmLibraries = Sets.newHashSet();
                Set<UnresolvedSoftwareComponent> unresolvedComponents = Sets.newHashSet();

                for (ComponentIdentifier componentId : componentIds) {
                    if (!(componentId instanceof ModuleComponentIdentifier)) {
                        throw new AssertionError("unknown component identifier type: " + componentId.getClass().getName());
                    }
                    ModuleComponentIdentifier moduleComponentId = (ModuleComponentIdentifier) componentId;
                    BuildableModuleVersionResolveResult moduleResolveResult = new DefaultBuildableModuleVersionResolveResult();
                    repositoryChain.getDependencyResolver().resolve(new DefaultDependencyMetaData(new DefaultDependencyDescriptor(toModuleRevisionId(moduleComponentId), true)), moduleResolveResult);
                    ArtifactResolver artifactResolver = repositoryChain.getArtifactResolver();

                    if (moduleResolveResult.getFailure() != null) {
                        unresolvedComponents.add(new DefaultUnresolvedSoftwareComponent(moduleComponentId, moduleResolveResult.getFailure()));
                    } else {
                        ModuleVersionMetaData moduleMetaData = moduleResolveResult.getMetaData();
                        List<JvmLibraryArtifact> jvmLibraryArtifacts = Lists.newArrayList();
                        for (Class<? extends SoftwareArtifact> artifactType : artifactTypes) {
                            ArtifactResolveContext context = new ArtifactTypeResolveContext(artifactType);
                            BuildableArtifactSetResolveResult multiResolveResult = new DefaultBuildableArtifactSetResolveResult();
                            artifactResolver.resolveModuleArtifacts(moduleMetaData, context, multiResolveResult);
                            for (ComponentArtifactMetaData artifactMetaData : multiResolveResult.getArtifacts()) {
                                BuildableArtifactResolveResult resolveResult = new DefaultBuildableArtifactResolveResult();
                                artifactResolver.resolveArtifact(artifactMetaData, moduleMetaData.getSource(), resolveResult);
                                if (artifactType == JvmLibraryJavadocArtifact.class) {
                                    if (resolveResult.getFailure() != null) {
                                        jvmLibraryArtifacts.add(new DefaultJvmLibraryJavadocArtifact(resolveResult.getFailure()));
                                    } else {
                                        jvmLibraryArtifacts.add(new DefaultJvmLibraryJavadocArtifact(resolveResult.getFile()));
                                    }
                                } else if (artifactType == JvmLibrarySourcesArtifact.class) {
                                    if (resolveResult.getFailure() != null) {
                                        jvmLibraryArtifacts.add(new DefaultJvmLibrarySourcesArtifact(resolveResult.getFailure()));
                                    } else {
                                        jvmLibraryArtifacts.add(new DefaultJvmLibrarySourcesArtifact(resolveResult.getFile()));
                                    }
                                } else {
                                    throw new AssertionError("unknown artifact type: " + artifactType.getName());
                                }
                            }
                        }

                        jvmLibraries.add(new DefaultJvmLibrary(moduleComponentId, jvmLibraryArtifacts));
                    }
                }

                return new DefaultArtifactResolutionQueryResult(jvmLibraries, unresolvedComponents);
            }
        });
    }

    private ModuleRevisionId toModuleRevisionId(ModuleComponentIdentifier componentId) {
        return ModuleRevisionId.newInstance(componentId.getGroup(), componentId.getModule(), componentId.getVersion());
    }
}
