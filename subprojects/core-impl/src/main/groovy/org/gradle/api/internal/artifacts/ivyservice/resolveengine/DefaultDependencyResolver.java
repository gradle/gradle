/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.apache.ivy.Ivy;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LazyDependencyToModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.DefaultResolvedConfigurationBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDependencyResolver.class);
    private final LocalComponentFactory localComponentFactory;
    private final ResolveIvyFactory ivyFactory;
    private final ProjectComponentRegistry projectComponentRegistry;
    private final CacheLockingManager cacheLockingManager;
    private final IvyContextManager ivyContextManager;
    private final ResolutionResultsStoreFactory storeFactory;
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;

    public DefaultDependencyResolver(ResolveIvyFactory ivyFactory, LocalComponentFactory localComponentFactory,
                                     ProjectComponentRegistry projectComponentRegistry, CacheLockingManager cacheLockingManager, IvyContextManager ivyContextManager,
                                     ResolutionResultsStoreFactory storeFactory, VersionMatcher versionMatcher, LatestStrategy latestStrategy) {
        this.ivyFactory = ivyFactory;
        this.localComponentFactory = localComponentFactory;
        this.projectComponentRegistry = projectComponentRegistry;
        this.cacheLockingManager = cacheLockingManager;
        this.ivyContextManager = ivyContextManager;
        this.storeFactory = storeFactory;
        this.versionMatcher = versionMatcher;
        this.latestStrategy = latestStrategy;
    }

    public void resolve(final ConfigurationInternal configuration,
                        final List<? extends ResolutionAwareRepository> repositories,
                        final ModuleMetadataProcessor metadataProcessor,
                        final ResolverResults results) throws ResolveException {
        LOGGER.debug("Resolving {}", configuration);
        ivyContextManager.withIvy(new Action<Ivy>() {
            public void execute(Ivy ivy) {
                RepositoryChain repositoryChain = ivyFactory.create(configuration, repositories, metadataProcessor);

                DependencyToModuleVersionResolver dependencyResolver = repositoryChain.getDependencyResolver();
                dependencyResolver = new ClientModuleResolver(dependencyResolver);
                ProjectDependencyResolver projectDependencyResolver = new ProjectDependencyResolver(projectComponentRegistry, localComponentFactory, dependencyResolver);
                dependencyResolver = projectDependencyResolver;
                DependencyToModuleVersionIdResolver idResolver = new LazyDependencyToModuleResolver(dependencyResolver, versionMatcher);
                idResolver = new VersionForcingDependencyToModuleResolver(idResolver, configuration.getResolutionStrategy().getDependencyResolveRule());

                ArtifactResolver artifactResolver = createArtifactResolver(repositoryChain);

                ModuleConflictResolver conflictResolver;
                if (configuration.getResolutionStrategy().getConflictResolution() instanceof StrictConflictResolution) {
                    conflictResolver = new StrictConflictResolver();
                } else {
                    conflictResolver = new LatestModuleConflictResolver(latestStrategy);
                }
                conflictResolver = new VersionSelectionReasonResolver(conflictResolver);

                DependencyGraphBuilder builder = new DependencyGraphBuilder(idResolver, projectDependencyResolver, artifactResolver, conflictResolver, new DefaultDependencyToConfigurationResolver());

                StoreSet stores = storeFactory.createStoreSet();

                BinaryStore newModelStore = stores.nextBinaryStore();
                Store<ResolvedComponentResult> newModelCache = stores.oldModelStore();
                ResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache);

                BinaryStore oldModelStore = stores.nextBinaryStore();
                Store<TransientConfigurationResults> oldModelCache = stores.newModelStore();
                TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache);
                DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);

                builder.resolve(configuration, newModelBuilder, oldModelBuilder);
                DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, oldModelBuilder, cacheLockingManager);
                results.resolved(new DefaultResolvedConfiguration(result), newModelBuilder.complete());
            }
        });
    }

    private ArtifactResolver createArtifactResolver(RepositoryChain repositoryChain) {
        ArtifactResolver artifactResolver = repositoryChain.getArtifactResolver();
        artifactResolver = new ProjectArtifactResolver(artifactResolver);
        artifactResolver = new ContextualArtifactResolver(cacheLockingManager, ivyContextManager, artifactResolver);
        artifactResolver = new ErrorHandlingArtifactResolver(artifactResolver);
        return artifactResolver;
    }
}
