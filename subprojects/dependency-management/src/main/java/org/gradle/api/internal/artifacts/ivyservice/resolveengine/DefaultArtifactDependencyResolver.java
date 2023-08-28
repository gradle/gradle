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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
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
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.VariantArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DefaultVariantArtifactResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator.NO_OP;

public class DefaultArtifactDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultArtifactDependencyResolver.class);
    private final DependencyMetadataFactory dependencyMetadataFactory;
    private final List<ResolverProviderFactory> resolverFactories;
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
    private final ResolvedVariantCache resolvedVariantCache;
    private final AttributeDesugaring attributeDesugaring;
    private final ModuleComponentGraphResolveStateFactory moduleResolveStateFactory;
    private final ComponentIdGenerator idGenerator;
    private final GraphVariantSelector configurationSelector;

    public DefaultArtifactDependencyResolver(
        BuildOperationExecutor buildOperationExecutor,
        List<ResolverProviderFactory> resolverFactories,
        ResolveIvyFactory ivyFactory,
        DependencyMetadataFactory dependencyMetadataFactory,
        VersionComparator versionComparator,
        ModuleExclusions moduleExclusions,
        ComponentSelectorConverter componentSelectorConverter,
        ImmutableAttributesFactory attributesFactory,
        VersionSelectorScheme versionSelectorScheme,
        VersionParser versionParser,
        ComponentMetadataSupplierRuleExecutor componentMetadataSupplierRuleExecutor,
        InstantiatorFactory instantiatorFactory,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        ResolvedVariantCache resolvedVariantCache,
        AttributeDesugaring attributeDesugaring,
        ModuleComponentGraphResolveStateFactory moduleResolveStateFactory,
        ComponentIdGenerator idGenerator,
        GraphVariantSelector configurationSelector
    ) {
        this.resolverFactories = resolverFactories;
        this.ivyFactory = ivyFactory;
        this.dependencyMetadataFactory = dependencyMetadataFactory;
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
        this.resolvedVariantCache = resolvedVariantCache;
        this.attributeDesugaring = attributeDesugaring;
        this.moduleResolveStateFactory = moduleResolveStateFactory;
        this.idGenerator = idGenerator;
        this.configurationSelector = configurationSelector;
    }

    @Override
    public void resolve(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        GlobalDependencyResolutionRules metadataHandler,
        Spec<? super DependencyMetadata> edgeFilter,
        DependencyGraphVisitor graphVisitor,
        DependencyArtifactsVisitor artifactsVisitor,
        AttributesSchemaInternal consumerSchema,
        ArtifactTypeRegistry artifactTypeRegistry,
        ProjectDependencyResolver projectDependencyResolver,
        boolean includeSyntheticDependencies
    ) {
        LOGGER.debug("Resolving {}", resolveContext);

        validateResolutionStrategy(resolveContext.getResolutionStrategy());

        ComponentResolvers userResolvers = createUserResolverChain(resolveContext, repositories, metadataHandler, consumerSchema);
        ComponentResolvers resolvers = createResolvers(resolveContext, projectDependencyResolver, userResolvers);
        DependencyGraphBuilder builder = createDependencyGraphBuilder(resolvers, resolveContext.getResolutionStrategy(), metadataHandler, edgeFilter, consumerSchema, moduleExclusions);

        VariantArtifactResolver variantResolver = new DefaultVariantArtifactResolver(resolvers.getArtifactResolver(), artifactTypeRegistry, resolvedVariantCache);
        DependencyGraphVisitor artifactsGraphVisitor = new ResolvedArtifactsGraphVisitor(artifactsVisitor, variantResolver, artifactTypeRegistry, calculatedValueContainerFactory);

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

    private DependencyGraphBuilder createDependencyGraphBuilder(
        ComponentResolvers componentSource,
        ResolutionStrategyInternal resolutionStrategy,
        GlobalDependencyResolutionRules globalRules,
        Spec<? super DependencyMetadata> edgeFilter,
        AttributesSchemaInternal attributesSchema,
        ModuleExclusions moduleExclusions
    ) {
        ComponentMetaDataResolver componentMetaDataResolver = new ClientModuleResolver(componentSource.getComponentResolver(), dependencyMetadataFactory, moduleResolveStateFactory);

        ModuleConflictHandler conflictHandler = createModuleConflictHandler(resolutionStrategy, globalRules);
        DefaultCapabilitiesConflictHandler capabilitiesConflictHandler = createCapabilitiesConflictHandler(resolutionStrategy.getCapabilitiesResolutionRules());
        DependencySubstitutionApplicator applicator = createDependencySubstitutionApplicator(resolutionStrategy);
        return new DependencyGraphBuilder(
            componentSource.getComponentIdResolver(),
            componentMetaDataResolver,
            conflictHandler,
            capabilitiesConflictHandler,
            edgeFilter,
            attributesSchema,
            moduleExclusions,
            buildOperationExecutor,
            applicator,
            componentSelectorConverter,
            attributesFactory,
            attributeDesugaring,
            versionSelectorScheme,
            versionComparator.asVersionComparator(),
            idGenerator,
            versionParser,
            configurationSelector
        );
    }

    private DependencySubstitutionApplicator createDependencySubstitutionApplicator(ResolutionStrategyInternal resolutionStrategy) {
        ImmutableActionSet<DependencySubstitutionInternal> rule = resolutionStrategy.getDependencySubstitutionRule();
        DependencySubstitutionApplicator applicator;
        if (rule.isEmpty()) {
            applicator = NO_OP;
        } else {
            applicator = new CachingDependencySubstitutionApplicator(new DefaultDependencySubstitutionApplicator(componentSelectionDescriptorFactory, rule, instantiator));
        }
        return applicator;
    }

    private ComponentResolvers createResolvers(ResolveContext resolveContext, ProjectDependencyResolver projectDependencyResolver, ComponentResolvers userResolvers) {
        List<ComponentResolvers> resolvers = Lists.newArrayList();
        for (ResolverProviderFactory factory : resolverFactories) {
            factory.create(resolveContext, resolvers);
        }
        resolvers.add(projectDependencyResolver);
        resolvers.add(userResolvers);
        return new ComponentResolversChain(resolvers);
    }

    private ComponentResolvers createUserResolverChain(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        GlobalDependencyResolutionRules metadataHandler,
        AttributesSchema consumerSchema
    ) {
        return ivyFactory.create(
            resolveContext.getName(),
            resolveContext.getResolutionStrategy(),
            repositories,
            metadataHandler.getComponentMetadataProcessorFactory(),
            resolveContext.getAttributes(),
            consumerSchema,
            attributesFactory,
            componentMetadataSupplierRuleExecutor
        );
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

}
