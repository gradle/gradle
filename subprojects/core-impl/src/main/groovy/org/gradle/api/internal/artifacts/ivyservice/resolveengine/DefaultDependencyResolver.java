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
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedModuleVersionResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyAdapter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LazyDependencyToModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.DefaultResolvedConfigurationBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientResultsStore;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedConfigurationListener;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDependencyResolver.class);
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final ResolveIvyFactory ivyFactory;
    private final ProjectModuleRegistry projectModuleRegistry;
    private final CacheLockingManager cacheLockingManager;
    private final IvyContextManager ivyContextManager;
    private final ResolutionResultsStoreFactory storeFactory;

    public DefaultDependencyResolver(ResolveIvyFactory ivyFactory, ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory,
                                     ProjectModuleRegistry projectModuleRegistry, CacheLockingManager cacheLockingManager, IvyContextManager ivyContextManager,
                                     ResolutionResultsStoreFactory storeFactory) {
        this.ivyFactory = ivyFactory;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
        this.projectModuleRegistry = projectModuleRegistry;
        this.cacheLockingManager = cacheLockingManager;
        this.ivyContextManager = ivyContextManager;
        this.storeFactory = storeFactory;
    }

    public ResolverResults resolve(final ConfigurationInternal configuration, final List<? extends ResolutionAwareRepository> repositories) throws ResolveException {
        LOGGER.debug("Resolving {}", configuration);
        return ivyContextManager.withIvy(new Transformer<ResolverResults, Ivy>() {
            public ResolverResults transform(Ivy ivy) {
                IvyAdapter ivyAdapter = ivyFactory.create(configuration, repositories);

                DependencyToModuleVersionResolver dependencyResolver = ivyAdapter.getDependencyToModuleResolver();
                dependencyResolver = new ClientModuleResolver(dependencyResolver);
                ProjectDependencyResolver projectDependencyResolver = new ProjectDependencyResolver(projectModuleRegistry, dependencyResolver, moduleDescriptorConverter);
                dependencyResolver = projectDependencyResolver;
                DependencyToModuleVersionIdResolver idResolver = new LazyDependencyToModuleResolver(dependencyResolver, ivyAdapter.getVersionMatcher());
                idResolver = new VersionForcingDependencyToModuleResolver(idResolver, configuration.getResolutionStrategy().getDependencyResolveRule());

                ModuleConflictResolver conflictResolver;
                if (configuration.getResolutionStrategy().getConflictResolution() instanceof StrictConflictResolution) {
                    conflictResolver = new StrictConflictResolver();
                } else {
                    conflictResolver = new LatestModuleConflictResolver(ivyAdapter.getLatestStrategy());
                }
                conflictResolver = new VersionSelectionReasonResolver(conflictResolver);

                DependencyGraphBuilder builder = new DependencyGraphBuilder(idResolver, projectDependencyResolver, conflictResolver, new DefaultDependencyToConfigurationResolver());

                BinaryStore newModelStore = storeFactory.createBinaryStore("new-model");
                Store<ResolvedModuleVersionResult> newModelCache = storeFactory.createNewModelCache(configuration);
                ResolvedConfigurationListener newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache);

                BinaryStore oldModelStore = storeFactory.createBinaryStore("old-model");
                Store<TransientConfigurationResults> oldModelCache = storeFactory.createOldModelCache(configuration);
                TransientResultsStore oldModelResults = new TransientResultsStore(oldModelStore, oldModelCache);
                DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(resolvedArtifactFactory, oldModelResults);

                builder.resolve(configuration, newModelBuilder, oldModelBuilder);
                DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, oldModelBuilder, cacheLockingManager);
                return new ResolverResults(new DefaultResolvedConfiguration(result), newModelBuilder.complete());
            }
        });
    }
}
