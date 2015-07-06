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

import com.google.common.collect.Lists;
import org.apache.ivy.Ivy;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.*;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.*;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.DefaultResolvedLocalComponentsResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.internal.Factory;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.ResolveContextToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDependencyResolver.class);
    private final ServiceRegistry serviceRegistry;
    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final ResolveIvyFactory ivyFactory;
    private final CacheLockingManager cacheLockingManager;
    private final IvyContextManager ivyContextManager;
    private final ResolutionResultsStoreFactory storeFactory;
    private final VersionComparator versionComparator;
    private final boolean buildProjectDependencies;

    public DefaultDependencyResolver(ServiceRegistry serviceRegistry, ResolveIvyFactory ivyFactory, DependencyDescriptorFactory dependencyDescriptorFactory,
                                     CacheLockingManager cacheLockingManager, IvyContextManager ivyContextManager,
                                     ResolutionResultsStoreFactory storeFactory, VersionComparator versionComparator,
                                     boolean buildProjectDependencies) {
        this.serviceRegistry = serviceRegistry;
        this.ivyFactory = ivyFactory;
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.cacheLockingManager = cacheLockingManager;
        this.ivyContextManager = ivyContextManager;
        this.storeFactory = storeFactory;
        this.versionComparator = versionComparator;
        this.buildProjectDependencies = buildProjectDependencies;
    }

    private <T> List<T> allServices(Class<T> serviceType) {
        return Lists.newArrayList(serviceRegistry.getAll(serviceType));
    }

    public void resolve(final ResolveContext resolveContext,
                        final List<? extends ResolutionAwareRepository> repositories,
                        final GlobalDependencyResolutionRules metadataHandler,
                        final BuildableResolverResults results) throws ResolveException {
        LOGGER.debug("Resolving {}", resolveContext);
        ivyContextManager.withIvy(new Action<Ivy>() {
            public void execute(Ivy ivy) {
                ResolutionStrategyInternal resolutionStrategy = (ResolutionStrategyInternal) resolveContext.getResolutionStrategy();
                ResolverProvider componentSource = createComponentSource(resolutionStrategy, resolveContext, repositories, metadataHandler);
                ArtifactResolver artifactResolver = new ErrorHandlingArtifactResolver(new ContextualArtifactResolver(cacheLockingManager, ivyContextManager, componentSource.getArtifactResolver()));

                StoreSet stores = storeFactory.createStoreSet();

                BinaryStore newModelStore = stores.nextBinaryStore();
                Store<ResolvedComponentResult> newModelCache = stores.oldModelStore();
                ResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache);
                DependencyGraphVisitor newModelVisitor = new ResolutionResultDependencyGraphVisitor(newModelBuilder);

                BinaryStore oldModelStore = stores.nextBinaryStore();
                Store<TransientConfigurationResults> oldModelCache = stores.newModelStore();
                TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache);
                DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
                DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder();
                DependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder, artifactsBuilder, artifactResolver);

                ResolvedLocalComponentsResultBuilder localComponentsResultBuilder = new DefaultResolvedLocalComponentsResultBuilder(buildProjectDependencies);
                DependencyGraphVisitor projectModelVisitor = new ResolvedLocalComponentsResultGraphVisitor(localComponentsResultBuilder);

                // Resolve the dependency graph
                DependencyGraphBuilder builder = createDependencyGraphBuilder(componentSource, resolutionStrategy, metadataHandler);
                builder.resolve(resolveContext, oldModelVisitor, newModelVisitor, projectModelVisitor);

                DefaultResolverResults defaultResolverResults = (DefaultResolverResults) results;
                defaultResolverResults.resolved(newModelBuilder.complete(), localComponentsResultBuilder.complete());

                ResolvedGraphResults graphResults = oldModelBuilder.complete();
                defaultResolverResults.retainState(graphResults, artifactsBuilder, oldTransientModelBuilder);
            }
        });
    }

    private DependencyGraphBuilder createDependencyGraphBuilder(ResolverProvider componentSource, ResolutionStrategyInternal resolutionStrategy, GlobalDependencyResolutionRules metadataHandler) {

        DependencyToComponentIdResolver componentIdResolver = new DependencySubstitutionResolver(componentSource.getComponentIdResolver(), resolutionStrategy.getDependencySubstitutionRule());
        ComponentMetaDataResolver componentMetaDataResolver = new ClientModuleResolver(componentSource.getComponentResolver(), dependencyDescriptorFactory);

        DependencyToConfigurationResolver dependencyToConfigurationResolver = new DefaultDependencyToConfigurationResolver();
        ResolveContextToComponentResolver requestResolver = createResolveContextConverter();
        ConflictHandler conflictHandler = createConflictHandler(resolutionStrategy, metadataHandler);

        return new DependencyGraphBuilder(componentIdResolver, componentMetaDataResolver, requestResolver, dependencyToConfigurationResolver, conflictHandler);
    }

    private ResolverProviderChain createComponentSource(ResolutionStrategyInternal resolutionStrategy, ResolveContext resolveContext, List<? extends ResolutionAwareRepository> repositories, GlobalDependencyResolutionRules metadataHandler) {
        List<ResolverProvider> resolvers = allServices(ResolverProvider.class);
        List<ResolverProviderFactory> providerFactories = allServices(ResolverProviderFactory.class);
        for (ResolverProviderFactory factory : providerFactories) {
            if (factory.canCreate(resolveContext)) {
                resolvers.add(factory.create(resolveContext));
            }
        }
        resolvers.add(ivyFactory.create(resolutionStrategy, repositories, metadataHandler.getComponentMetadataProcessor()));
        return new ResolverProviderChain(resolvers);
    }

    private ResolveContextToComponentResolver createResolveContextConverter() {
        List<LocalComponentConverter> localComponentFactories = allServices(LocalComponentConverter.class);
        return new DefaultResolveContextToComponentResolver(new ChainedLocalComponentConverter(localComponentFactories));
    }

    private ConflictHandler createConflictHandler(ResolutionStrategyInternal resolutionStrategy, GlobalDependencyResolutionRules metadataHandler) {
        ModuleConflictResolver conflictResolver;
        if (resolutionStrategy.getConflictResolution() instanceof StrictConflictResolution) {
            conflictResolver = new StrictConflictResolver();
        } else {
            conflictResolver = new LatestModuleConflictResolver(versionComparator);
        }
        conflictResolver = new VersionSelectionReasonResolver(conflictResolver);
        return new DefaultConflictHandler(conflictResolver, metadataHandler.getModuleMetadataProcessor().getModuleReplacements());
    }

    public void resolveArtifacts(final ResolveContext resolveContext,
                                 final List<? extends ResolutionAwareRepository> repositories,
                                 final GlobalDependencyResolutionRules metadataHandler,
                                 final BuildableResolverResults results) throws ResolveException {

        if (resolveContext instanceof Configuration) {
            DefaultResolverResults defaultResolverResults = (DefaultResolverResults) results;
            ResolvedGraphResults graphResults = defaultResolverResults.getGraphResults();
            ResolvedArtifactResults artifactResults = defaultResolverResults.getArtifactsBuilder().resolve();
            TransientConfigurationResultsBuilder transientConfigurationResultsBuilder = defaultResolverResults.getTransientConfigurationResultsBuilder();

            Factory<TransientConfigurationResults> transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(transientConfigurationResultsBuilder, graphResults, artifactResults);

            DefaultLenientConfiguration result = new DefaultLenientConfiguration(
                (Configuration) resolveContext, cacheLockingManager, graphResults, artifactResults, transientConfigurationResultsFactory);
            results.withResolvedConfiguration(new DefaultResolvedConfiguration(result));
        } else {
            throw new UnsupportedOperationException("Artifact resolution only supported for Configuration");
        }
    }

    private static class ChainedLocalComponentConverter implements LocalComponentConverter {
        private final List<LocalComponentConverter> factories;

        public ChainedLocalComponentConverter(List<LocalComponentConverter> factories) {
            this.factories = factories;
        }

        @Override
        public boolean canConvert(Object source) {
            for (LocalComponentConverter factory : factories) {
                if (factory.canConvert(source)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public LocalComponentMetaData convert(Object context) {
            for (LocalComponentConverter factory : factories) {
                if (factory.canConvert(context)) {
                    return factory.convert(context);
                }
            }
            throw new IllegalArgumentException("Unable to find a local converter factory for type "+context.getClass());
        }
    }

    private static class DefaultResolveContextToComponentResolver implements ResolveContextToComponentResolver {
        private final LocalComponentConverter localComponentFactory;

        public DefaultResolveContextToComponentResolver(ChainedLocalComponentConverter localComponentFactory) {
            this.localComponentFactory = localComponentFactory;
        }

        @Override
        public void resolve(ResolveContext resolveContext, BuildableComponentResolveResult result) {
            LocalComponentMetaData componentMetaData = localComponentFactory.convert(resolveContext);
            result.resolved(componentMetaData);
        }
    }

}
