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
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.*;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.DefaultResolvedLocalComponentsResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.Factory;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.ResolveContextToComponentResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
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

    private <T> List<T> allServices(Class<T> serviceType, T... additionals) {
        ArrayList<T> list = Lists.newArrayList(serviceRegistry.getAll(serviceType));
        if (additionals!=null) {
            Collections.addAll(list, additionals);
        }
        return list;
    }

    public void resolve(final ResolveContext resolveContext,
                        final List<? extends ResolutionAwareRepository> repositories,
                        final GlobalDependencyResolutionRules metadataHandler,
                        final DefaultResolverResults results) throws ResolveException {
        LOGGER.debug("Resolving {}", resolveContext);
        ivyContextManager.withIvy(new Action<Ivy>() {
            public void execute(Ivy ivy) {
                ResolutionStrategyInternal resolutionStrategy = (ResolutionStrategyInternal) resolveContext.getResolutionStrategy();

                List<LocalComponentFactory> localComponentFactories = allServices(LocalComponentFactory.class);
                List<ResolverProvider> resolvers = allServices(ResolverProvider.class, ivyFactory.create(resolutionStrategy, repositories, metadataHandler.getComponentMetadataProcessor()));
                ResolverProviderChain resolverProvider = new ResolverProviderChain(resolvers);
                WrappingResolverProvider wrappingProvider = new WrappingResolverProvider(
                    new DependencySubstitutionResolver(resolverProvider.getComponentIdResolver(), resolutionStrategy.getDependencySubstitutionRule()),
                    new ClientModuleResolver(resolverProvider.getComponentResolver(), dependencyDescriptorFactory),
                        createArtifactResolver(resolverProvider.getArtifactResolver())
                );
                ModuleConflictResolver conflictResolver;
                if (resolutionStrategy.getConflictResolution() instanceof StrictConflictResolution) {
                    conflictResolver = new StrictConflictResolver();
                } else {
                    conflictResolver = new LatestModuleConflictResolver(versionComparator);
                }
                conflictResolver = new VersionSelectionReasonResolver(conflictResolver);
                ConflictHandler conflictHandler = new DefaultConflictHandler(conflictResolver, metadataHandler.getModuleMetadataProcessor().getModuleReplacements());
                DefaultResolveContextToComponentResolver moduleResolver = new DefaultResolveContextToComponentResolver(new LocalComponentFactoryChain(localComponentFactories));
                DependencyGraphBuilder builder = new DependencyGraphBuilder(wrappingProvider, moduleResolver, conflictHandler, new DefaultDependencyToConfigurationResolver());

                StoreSet stores = storeFactory.createStoreSet();

                BinaryStore newModelStore = stores.nextBinaryStore();
                Store<ResolvedComponentResult> newModelCache = stores.oldModelStore();
                ResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache);

                BinaryStore oldModelStore = stores.nextBinaryStore();
                Store<TransientConfigurationResults> oldModelCache = stores.newModelStore();
                TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache);
                DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
                ResolvedLocalComponentsResultBuilder localComponentsResultBuilder = new DefaultResolvedLocalComponentsResultBuilder(buildProjectDependencies);

                // Resolve the dependency graph
                DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder();
                builder.resolve(resolveContext, newModelBuilder, oldModelBuilder, artifactsBuilder, localComponentsResultBuilder);
                results.resolved(newModelBuilder.complete(), localComponentsResultBuilder.complete());

                ResolvedGraphResults graphResults = oldModelBuilder.complete();
                results.retainState(graphResults, artifactsBuilder, oldTransientModelBuilder);
            }
        });
    }

    public void resolveArtifacts(final ResolveContext resolveContext,
                                 final List<? extends ResolutionAwareRepository> repositories,
                                 final GlobalDependencyResolutionRules metadataHandler,
                                 final DefaultResolverResults results) throws ResolveException {
        ResolvedGraphResults graphResults = results.getGraphResults();
        ResolvedArtifactResults artifactResults = results.getArtifactsBuilder().resolve();

        if (resolveContext instanceof Configuration) {
            // TODO:DAZ Should not be holding onto all of this state


            Factory<TransientConfigurationResults> transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(results.getTransientConfigurationResultsBuilder(), graphResults, artifactResults);

            DefaultLenientConfiguration result = new DefaultLenientConfiguration(
                (Configuration) resolveContext, cacheLockingManager, graphResults, artifactResults, transientConfigurationResultsFactory);
            results.withResolvedConfiguration(new DefaultResolvedConfiguration(result));
        } else {
            results.getResolutionResult().allComponents(new Action<ResolvedComponentResult>() {
                @Override
                public void execute(ResolvedComponentResult resolvedComponentResult) {

                }
            });
        }
    }

    private ArtifactResolver createArtifactResolver(ArtifactResolver origin) {
        ArtifactResolver artifactResolver = new ContextualArtifactResolver(cacheLockingManager, ivyContextManager, origin);
        artifactResolver = new ErrorHandlingArtifactResolver(artifactResolver);
        return artifactResolver;
    }

    private static class DefaultResolveContextToComponentResolver implements ResolveContextToComponentResolver {
        private final LocalComponentFactoryChain localComponentFactory;

        public DefaultResolveContextToComponentResolver(LocalComponentFactoryChain localComponentFactory) {
            this.localComponentFactory = localComponentFactory;
        }

        @Override
        public void resolve(ResolveContext resolveContext, BuildableComponentResolveResult result) {
            LocalComponentMetaData componentMetaData = localComponentFactory.convert(resolveContext);
            result.resolved(componentMetaData.toResolveMetaData());
        }
    }

    private static class ComponentMetaDataResolverChain implements ComponentMetaDataResolver {
        private final List<ComponentMetaDataResolver> resolvers;

        public ComponentMetaDataResolverChain(List<ComponentMetaDataResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
            for (ComponentMetaDataResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolve(identifier, componentOverrideMetadata, result);
            }
        }
    }

    private static class DependencyToComponentIdResolverChain implements DependencyToComponentIdResolver {
        private final List<DependencyToComponentIdResolver> resolvers;

        public DependencyToComponentIdResolverChain(List<DependencyToComponentIdResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
            for (DependencyToComponentIdResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolve(dependency, result);
            }
        }
    }

    private static class LocalComponentFactoryChain implements LocalComponentFactory {
        private final List<LocalComponentFactory> factories;

        public LocalComponentFactoryChain(List<LocalComponentFactory> factories) {
            this.factories = factories;
        }

        @Override
        public boolean canConvert(Object source) {
            for (LocalComponentFactory factory : factories) {
                if (factory.canConvert(source)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public LocalComponentMetaData convert(Object context) {
            for (LocalComponentFactory factory : factories) {
                if (factory.canConvert(context)) {
                    return factory.convert(context);
                }
            }
            throw new IllegalArgumentException("Unable to find a local converter factory for type "+context.getClass());
        }
    }

    private static class ArtifactResolverChain implements ArtifactResolver {
        private final List<ArtifactResolver> resolvers;

        private ArtifactResolverChain(List<ArtifactResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveArtifact(artifact, moduleSource, result);
            }
        }

        @Override
        public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveModuleArtifacts(component, usage, result);
            }
        }

        @Override
        public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            for (ArtifactResolver resolver : resolvers) {
                if (result.hasResult()) {
                    return;
                }
                resolver.resolveModuleArtifacts(component, artifactType, result);
            }
        }
    }

    private static class ResolverProviderChain implements ResolverProvider {
        private final DependencyToComponentIdResolverChain dependencyToComponentIdResolver;
        private final ComponentMetaDataResolverChain componentMetaDataResolver;
        private final ArtifactResolverChain artifactResolverChain;

        public ResolverProviderChain(List<ResolverProvider> providers) {
            List<DependencyToComponentIdResolver> depToComponentIdResolvers = new ArrayList<DependencyToComponentIdResolver>(providers.size());
            List<ComponentMetaDataResolver> componentMetaDataResolvers = new ArrayList<ComponentMetaDataResolver>(providers.size());
            List<ArtifactResolver> artifactResolvers = new ArrayList<ArtifactResolver>(providers.size());
            for (ResolverProvider provider : providers) {
                depToComponentIdResolvers.add(provider.getComponentIdResolver());
                componentMetaDataResolvers.add(provider.getComponentResolver());
                artifactResolvers.add(provider.getArtifactResolver());
            }
            dependencyToComponentIdResolver = new DependencyToComponentIdResolverChain(depToComponentIdResolvers);
            componentMetaDataResolver = new ComponentMetaDataResolverChain(componentMetaDataResolvers);
            artifactResolverChain = new ArtifactResolverChain(artifactResolvers);
        }

        @Override
        public DependencyToComponentIdResolverChain getComponentIdResolver() {
            return dependencyToComponentIdResolver;
        }

        @Override
        public ComponentMetaDataResolverChain getComponentResolver() {
            return componentMetaDataResolver;
        }

        @Override
        public ArtifactResolverChain getArtifactResolver() {
            return artifactResolverChain;
        }

    }

    private static class WrappingResolverProvider implements ResolverProvider {
        private final DependencyToComponentIdResolver dependencyToComponentIdResolver;
        private final ComponentMetaDataResolver componentMetaDataResolver;
        private final ArtifactResolver artifactResolver;

        private WrappingResolverProvider(
            DependencyToComponentIdResolver dependencyToComponentIdResolver,
            ComponentMetaDataResolver componentMetaDataResolver,
            ArtifactResolver artifactResolver) {
            this.dependencyToComponentIdResolver = dependencyToComponentIdResolver;
            this.componentMetaDataResolver = componentMetaDataResolver;
            this.artifactResolver= artifactResolver;
        }

        @Override
        public ArtifactResolver getArtifactResolver() {
            return artifactResolver;
        }

        @Override
        public DependencyToComponentIdResolver getComponentIdResolver() {
            return dependencyToComponentIdResolver;
        }

        @Override
        public ComponentMetaDataResolver getComponentResolver() {
            return componentMetaDataResolver;
        }
    }
}
