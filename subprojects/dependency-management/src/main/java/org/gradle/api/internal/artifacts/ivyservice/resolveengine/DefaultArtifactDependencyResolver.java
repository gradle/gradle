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
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ErrorHandlingArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.PreferProjectModulesConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictHandler;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.ResolveContextToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.internal.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DefaultArtifactDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultArtifactDependencyResolver.class);
    private final ServiceRegistry serviceRegistry;
    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final ResolveIvyFactory ivyFactory;
    private final CacheLockingManager cacheLockingManager;
    private final VersionComparator versionComparator;

    public DefaultArtifactDependencyResolver(ServiceRegistry serviceRegistry, ResolveIvyFactory ivyFactory, DependencyDescriptorFactory dependencyDescriptorFactory,
                                             CacheLockingManager cacheLockingManager, VersionComparator versionComparator) {
        this.serviceRegistry = serviceRegistry;
        this.ivyFactory = ivyFactory;
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.cacheLockingManager = cacheLockingManager;
        this.versionComparator = versionComparator;
    }

    @Override
    public void resolve(ResolveContext resolveContext, List<? extends ResolutionAwareRepository> repositories, GlobalDependencyResolutionRules metadataHandler, Spec<? super DependencyMetadata> edgeFilter, DependencyGraphVisitor graphVisitor, DependencyArtifactsVisitor artifactsVisitor, AttributesSchema attributesSchema) {
        LOGGER.debug("Resolving {}", resolveContext);
        ComponentResolvers resolvers = createResolvers(resolveContext, repositories, metadataHandler);
        DependencyGraphBuilder builder = createDependencyGraphBuilder(resolvers, resolveContext.getResolutionStrategy(), metadataHandler, edgeFilter, attributesSchema);

        ArtifactResolver artifactResolver = new ErrorHandlingArtifactResolver(new CacheLockingArtifactResolver(cacheLockingManager, resolvers.getArtifactResolver()));
        DependencyGraphVisitor artifactsGraphVisitor = new ResolvedArtifactsGraphVisitor(artifactsVisitor, artifactResolver);

        // Resolve the dependency graph
        builder.resolve(resolveContext, new CompositeDependencyGraphVisitor(graphVisitor, artifactsGraphVisitor));
    }

    private DependencyGraphBuilder createDependencyGraphBuilder(ComponentResolvers componentSource, ResolutionStrategyInternal resolutionStrategy, GlobalDependencyResolutionRules globalRules, Spec<? super DependencyMetadata> edgeFilter, AttributesSchema attributesSchema) {

        DependencyToComponentIdResolver componentIdResolver = new DependencySubstitutionResolver(componentSource.getComponentIdResolver(), resolutionStrategy.getDependencySubstitutionRule());
        ComponentMetaDataResolver componentMetaDataResolver = new ClientModuleResolver(componentSource.getComponentResolver(), dependencyDescriptorFactory);

        ResolveContextToComponentResolver requestResolver = createResolveContextConverter();
        ConflictHandler conflictHandler = createConflictHandler(resolutionStrategy, globalRules);

        return new DependencyGraphBuilder(componentIdResolver, componentMetaDataResolver, requestResolver, conflictHandler, edgeFilter, attributesSchema);
    }

    private ComponentResolversChain createResolvers(ResolveContext resolveContext, List<? extends ResolutionAwareRepository> repositories, GlobalDependencyResolutionRules metadataHandler) {
        List<ResolverProviderFactory> resolverFactories = allServices(ResolverProviderFactory.class);
        List<ComponentResolvers> resolvers = Lists.newArrayList();
        for (ResolverProviderFactory factory : resolverFactories) {
            if (factory.canCreate(resolveContext)) {
                resolvers.add(factory.create(resolveContext));
            }
        }
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        resolvers.add(ivyFactory.create(resolutionStrategy, repositories, metadataHandler.getComponentMetadataProcessor()));
        return new ComponentResolversChain(resolvers);
    }

    private ResolveContextToComponentResolver createResolveContextConverter() {
        return new DefaultResolveContextToComponentResolver();
    }

    private ConflictHandler createConflictHandler(ResolutionStrategyInternal resolutionStrategy, GlobalDependencyResolutionRules metadataHandler) {
        ModuleConflictResolver conflictResolver;
        ConflictResolution conflictResolution = resolutionStrategy.getConflictResolution();
        if (conflictResolution instanceof StrictConflictResolution) {
            conflictResolver = new StrictConflictResolver();
        } else {
            conflictResolver = new LatestModuleConflictResolver(versionComparator);
            if (conflictResolution instanceof PreferProjectModulesConflictResolution) {
                conflictResolver = new ProjectDependencyForcingResolver(conflictResolver);
            }
        }
        conflictResolver = new VersionSelectionReasonResolver(conflictResolver);
        return new DefaultConflictHandler(conflictResolver, metadataHandler.getModuleMetadataProcessor().getModuleReplacements());
    }

    private <T> List<T> allServices(Class<T> serviceType) {
        return Lists.newArrayList(serviceRegistry.getAll(serviceType));
    }

    private static class DefaultResolveContextToComponentResolver implements ResolveContextToComponentResolver {
        @Override
        public void resolve(ResolveContext resolveContext, BuildableComponentResolveResult result) {
            result.resolved(resolveContext.toRootComponentMetaData());
        }
    }

}
