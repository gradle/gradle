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
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.CachingDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultCapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.DefaultConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.LastCandidateCapabilityResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ModuleConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.RejectRemainingCandidates;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.UserConfiguredCapabilityResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.ResolveContextToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator.NO_OP;

public class DefaultArtifactDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultArtifactDependencyResolver.class);
    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final List<ResolverProviderFactory> resolverFactories;
    private final ProjectDependencyResolver projectDependencyResolver;
    private final ResolveIvyFactory ivyFactory;
    private final VersionComparator versionComparator;
    private final ModuleExclusions moduleExclusions;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final ImmutableAttributesFactory attributesFactory;
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionParser versionParser;
    private final ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor;
    private final Instantiator instantiator;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public DefaultArtifactDependencyResolver(BuildOperationExecutor buildOperationExecutor,
                                             List<ResolverProviderFactory> resolverFactories,
                                             ProjectDependencyResolver projectDependencyResolver,
                                             ResolveIvyFactory ivyFactory,
                                             DependencyDescriptorFactory dependencyDescriptorFactory,
                                             VersionComparator versionComparator,
                                             ModuleExclusions moduleExclusions,
                                             ComponentSelectorConverter componentSelectorConverter,
                                             ImmutableAttributesFactory attributesFactory,
                                             VersionSelectorScheme versionSelectorScheme,
                                             VersionParser versionParser,
                                             ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
                                             InstantiatorFactory instantiatorFactory,
                                             ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
                                             CalculatedValueContainerFactory calculatedValueContainerFactory) {
        this.resolverFactories = resolverFactories;
        this.projectDependencyResolver = projectDependencyResolver;
        this.ivyFactory = ivyFactory;
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
        this.versionComparator = versionComparator;
        this.moduleExclusions = moduleExclusions;
        this.buildOperationExecutor = buildOperationExecutor;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributesFactory = attributesFactory;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionParser = versionParser;
        this.componentMetadataSupplierRuleExecutor = componentMetadataSupplierRuleExecutor;
        this.instantiator = instantiatorFactory.decorateScheme().instantiator();
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    @Override
    public void resolve(ResolveContext resolveContext, List<? extends ResolutionAwareRepository> repositories, GlobalDependencyResolutionRules metadataHandler, Spec<? super DependencyMetadata> edgeFilter, DependencyGraphVisitor graphVisitor, DependencyArtifactsVisitor artifactsVisitor, AttributesSchemaInternal consumerSchema, ArtifactTypeRegistry artifactTypeRegistry, boolean includeSyntheticDependencies) {
        LOGGER.debug("Resolving {}", resolveContext);

        validateResolutionStrategy(resolveContext.getResolutionStrategy());

        ComponentResolversChain resolvers = createResolvers(resolveContext, repositories, metadataHandler, artifactTypeRegistry, consumerSchema);
        DependencyGraphBuilder builder = createDependencyGraphBuilder(resolvers, resolveContext.getResolutionStrategy(), metadataHandler, edgeFilter, consumerSchema, moduleExclusions, buildOperationExecutor);

        DependencyGraphVisitor artifactsGraphVisitor = new ResolvedArtifactsGraphVisitor(artifactsVisitor, resolvers.getArtifactSelector());

        // Resolve the dependency graph
        builder.resolve(resolveContext, new CompositeDependencyGraphVisitor(graphVisitor, artifactsGraphVisitor), includeSyntheticDependencies);
    }

    private static void validateResolutionStrategy(ResolutionStrategyInternal resolutionStrategy) {
        if (resolutionStrategy.isDependencyLockingEnabled()) {
            if (resolutionStrategy.isFailingOnDynamicVersions()) {
                failOnDependencyLockingConflictingWith("fail on dynamic versions");
            } else if (resolutionStrategy.isFailingOnChangingVersions()) {
                failOnDependencyLockingConflictingWith("fail on changing versions");
            }
        }
    }

    private static void failOnDependencyLockingConflictingWith(String conflicting) {
        throw new InvalidUserCodeException("Resolution strategy has both dependency locking and " + conflicting + " enabled. You must choose between the two modes.");

    }

    private DependencyGraphBuilder createDependencyGraphBuilder(ComponentResolversChain componentSource,
                                                                ResolutionStrategyInternal resolutionStrategy,
                                                                GlobalDependencyResolutionRules globalRules,
                                                                Spec<? super DependencyMetadata> edgeFilter,
                                                                AttributesSchemaInternal attributesSchema,
                                                                ModuleExclusions moduleExclusions,
                                                                BuildOperationExecutor buildOperationExecutor) {

        DependencyToComponentIdResolver componentIdResolver = componentSource.getComponentIdResolver();
        ComponentMetaDataResolver componentMetaDataResolver = new ClientModuleResolver(componentSource.getComponentResolver(), dependencyDescriptorFactory);

        ResolveContextToComponentResolver requestResolver = createResolveContextConverter();
        ModuleConflictHandler conflictHandler = createModuleConflictHandler(resolutionStrategy, globalRules);
        DefaultCapabilitiesConflictHandler capabilitiesConflictHandler = createCapabilitiesConflictHandler(resolutionStrategy.getCapabilitiesResolutionRules());

        DependencySubstitutionApplicator applicator = createDependencySubstitutionApplicator(resolutionStrategy);
        return new DependencyGraphBuilder(componentIdResolver, componentMetaDataResolver, requestResolver, conflictHandler, capabilitiesConflictHandler, edgeFilter, attributesSchema, moduleExclusions, buildOperationExecutor, applicator, componentSelectorConverter, attributesFactory, versionSelectorScheme, versionComparator.asVersionComparator(), versionParser);
    }

    private DependencySubstitutionApplicator createDependencySubstitutionApplicator(ResolutionStrategyInternal resolutionStrategy) {
        Action<DependencySubstitution> rule = resolutionStrategy.getDependencySubstitutionRule();
        DependencySubstitutionApplicator applicator;
        if (Actions.<DependencySubstitution>doNothing() == rule) {
            applicator = NO_OP;
        } else {
            applicator = new CachingDependencySubstitutionApplicator(new DefaultDependencySubstitutionApplicator(componentSelectionDescriptorFactory, rule, instantiator));
        }
        return applicator;
    }

    private ComponentResolversChain createResolvers(ResolveContext resolveContext, List<? extends ResolutionAwareRepository> repositories, GlobalDependencyResolutionRules metadataHandler, ArtifactTypeRegistry artifactTypeRegistry, AttributesSchema consumerSchema) {
        List<ComponentResolvers> resolvers = Lists.newArrayList();
        for (ResolverProviderFactory factory : resolverFactories) {
            factory.create(resolveContext, resolvers);
        }
        resolvers.add(projectDependencyResolver);
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        resolvers.add(ivyFactory.create(resolveContext.getName(), resolutionStrategy, repositories, metadataHandler.getComponentMetadataProcessorFactory(), resolveContext.getAttributes(), consumerSchema, attributesFactory, componentMetadataSupplierRuleExecutor));
        return new ComponentResolversChain(resolvers, artifactTypeRegistry, calculatedValueContainerFactory);
    }

    private ResolveContextToComponentResolver createResolveContextConverter() {
        return new DefaultResolveContextToComponentResolver();
    }

    private ModuleConflictHandler createModuleConflictHandler(ResolutionStrategyInternal resolutionStrategy, GlobalDependencyResolutionRules metadataHandler) {
        ConflictResolution conflictResolution = resolutionStrategy.getConflictResolution();
        ModuleConflictResolver<ComponentState> conflictResolver =
            new ConflictResolverFactory(versionComparator, versionParser).createConflictResolver(conflictResolution);
        return new DefaultConflictHandler(conflictResolver, metadataHandler.getModuleMetadataProcessor().getModuleReplacements());
    }

    private DefaultCapabilitiesConflictHandler createCapabilitiesConflictHandler(CapabilitiesResolutionInternal capabilitiesResolutionRules) {
        DefaultCapabilitiesConflictHandler handler = new DefaultCapabilitiesConflictHandler();
        handler.registerResolver(new UserConfiguredCapabilityResolver(capabilitiesResolutionRules));
        //handler.registerResolver(new UpgradeCapabilityResolver());
        handler.registerResolver(new LastCandidateCapabilityResolver());
        handler.registerResolver(new RejectRemainingCandidates());
        return handler;
    }

    private static class DefaultResolveContextToComponentResolver implements ResolveContextToComponentResolver {
        @Override
        public void resolve(ResolveContext resolveContext, BuildableComponentResolveResult result) {
            result.resolved(resolveContext.toRootComponentMetaData());
        }
    }

}
