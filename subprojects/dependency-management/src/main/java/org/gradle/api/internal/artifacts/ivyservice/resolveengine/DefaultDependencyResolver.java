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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolveContext;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContextInternal;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChain;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.DefaultResolutionStrategy;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.*;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.DefaultResolvedProjectConfigurationResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedProjectConfigurationResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.internal.Factory;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDependencyResolver.class);
    private final LocalComponentFactory localComponentFactory;
    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final ResolveIvyFactory ivyFactory;
    private final ProjectComponentRegistry projectComponentRegistry;
    private final CacheLockingManager cacheLockingManager;
    private final IvyContextManager ivyContextManager;
    private final ResolutionResultsStoreFactory storeFactory;
    private final VersionComparator versionComparator;
    private final boolean buildProjectDependencies;

    public DefaultDependencyResolver(ResolveIvyFactory ivyFactory, LocalComponentFactory localComponentFactory, DependencyDescriptorFactory dependencyDescriptorFactory,
                                     ProjectComponentRegistry projectComponentRegistry, CacheLockingManager cacheLockingManager, IvyContextManager ivyContextManager,
                                     ResolutionResultsStoreFactory storeFactory, VersionComparator versionComparator, boolean buildProjectDependencies) {
        this.ivyFactory = ivyFactory;
        this.localComponentFactory = localComponentFactory;
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.projectComponentRegistry = projectComponentRegistry;
        this.cacheLockingManager = cacheLockingManager;
        this.ivyContextManager = ivyContextManager;
        this.storeFactory = storeFactory;
        this.versionComparator = versionComparator;
        this.buildProjectDependencies = buildProjectDependencies;
    }

    public void resolve(final ResolveContext resolveContext,
                        final List<? extends ResolutionAwareRepository> repositories,
                        final GlobalDependencyResolutionRules metadataHandler,
                        final ResolverResults results) throws ResolveException {
        LOGGER.debug("Resolving {}", resolveContext);
        ivyContextManager.withIvy(new Action<Ivy>() {
            public void execute(Ivy ivy) {
                ResolutionStrategyInternal resolutionStrategy;
                if (resolveContext instanceof ConfigurationInternal) {
                    resolutionStrategy =((ConfigurationInternal) resolveContext).getResolutionStrategy();
                } else {
                    resolutionStrategy = new DefaultResolutionStrategy();
                }
                RepositoryChain repositoryChain = ivyFactory.create(resolutionStrategy, repositories, metadataHandler.getComponentMetadataProcessor());

                ComponentMetaDataResolver metaDataResolver = new ClientModuleResolver(repositoryChain.getComponentResolver(), dependencyDescriptorFactory);
                ProjectDependencyResolver projectDependencyResolver;
                if (resolveContext instanceof ResolveContextInternal) {
                    projectDependencyResolver = ((ResolveContextInternal) resolveContext).newProjectDependencyResolver(projectComponentRegistry, localComponentFactory, repositoryChain.getComponentIdResolver(), metaDataResolver);
                } else {
                    projectDependencyResolver = new ProjectDependencyResolver(projectComponentRegistry, localComponentFactory, repositoryChain.getComponentIdResolver(), metaDataResolver);
                }
                DependencyToComponentIdResolver idResolver = new DependencySubstitutionResolver(projectDependencyResolver, resolutionStrategy.getDependencySubstitutionRule());

                ArtifactResolver artifactResolver = createArtifactResolver(repositoryChain);

                ModuleConflictResolver conflictResolver;
                if (resolutionStrategy.getConflictResolution() instanceof StrictConflictResolution) {
                    conflictResolver = new StrictConflictResolver();
                } else {
                    conflictResolver = new LatestModuleConflictResolver(versionComparator);
                }
                conflictResolver = new VersionSelectionReasonResolver(conflictResolver);
                ConflictHandler conflictHandler = new DefaultConflictHandler(conflictResolver, metadataHandler.getModuleMetadataProcessor().getModuleReplacements());

                DependencyGraphBuilder builder = new DependencyGraphBuilder(idResolver, projectDependencyResolver, projectDependencyResolver, artifactResolver, conflictHandler, new DefaultDependencyToConfigurationResolver());

                StoreSet stores = storeFactory.createStoreSet();

                BinaryStore newModelStore = stores.nextBinaryStore();
                Store<ResolvedComponentResult> newModelCache = stores.oldModelStore();
                ResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache);

                BinaryStore oldModelStore = stores.nextBinaryStore();
                Store<TransientConfigurationResults> oldModelCache = stores.newModelStore();
                TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache);
                DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
                DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder();
                ResolvedProjectConfigurationResultBuilder projectModelBuilder = new DefaultResolvedProjectConfigurationResultBuilder(buildProjectDependencies);

                // Resolve the dependency graph
                builder.resolve(resolveContext, newModelBuilder, oldModelBuilder, artifactsBuilder, projectModelBuilder);
                results.resolved(newModelBuilder.complete(), projectModelBuilder.complete());

                ResolvedGraphResults graphResults = oldModelBuilder.complete();
                results.retainState(graphResults, artifactsBuilder, oldTransientModelBuilder);
            }
        });
    }

    public void resolveArtifacts(final ResolveContext resolveContext,
                                 final List<? extends ResolutionAwareRepository> repositories,
                                 final GlobalDependencyResolutionRules metadataHandler,
                                 final ResolverResults results) throws ResolveException {
        // TODO:DAZ Should not be holding onto all of this state
        ResolvedGraphResults graphResults = results.getGraphResults();

        ResolvedArtifactResults artifactResults = results.getArtifactsBuilder().resolve();

        Factory<TransientConfigurationResults> transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(results.getTransientConfigurationResultsBuilder(), graphResults, artifactResults);

        DefaultLenientConfiguration result = new DefaultLenientConfiguration(
            (Configuration) resolveContext, cacheLockingManager, graphResults, artifactResults, transientConfigurationResultsFactory);
        results.withResolvedConfiguration(new DefaultResolvedConfiguration(result));
    }

    private ArtifactResolver createArtifactResolver(RepositoryChain repositoryChain) {
        ArtifactResolver artifactResolver = repositoryChain.getArtifactResolver();
        artifactResolver = new ProjectArtifactResolver(artifactResolver);
        artifactResolver = new ContextualArtifactResolver(cacheLockingManager, ivyContextManager, artifactResolver);
        artifactResolver = new ErrorHandlingArtifactResolver(artifactResolver);
        return artifactResolver;
    }

}
