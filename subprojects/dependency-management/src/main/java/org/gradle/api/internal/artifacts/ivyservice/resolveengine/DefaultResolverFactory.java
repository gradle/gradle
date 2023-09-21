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

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolverFactory;
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
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.caching.ComponentMetadataSupplierRuleExecutor;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator.NO_OP;

public class DefaultResolverFactory implements ResolverFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultResolverFactory.class);
    private final DependencyMetadataFactory dependencyMetadataFactory;
    private final List<ResolverProviderFactory> resolverFactories;
    private final ResolveIvyFactory moduleDependencyResolverFactory;
    private final ProjectDependencyResolver projectDependencyResolver;
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
    private final AttributeDesugaring attributeDesugaring;
    private final ModuleComponentGraphResolveStateFactory moduleResolveStateFactory;
    private final ComponentIdGenerator idGenerator;
    private final GraphVariantSelector variantSelector;

    public DefaultResolverFactory(
        BuildOperationExecutor buildOperationExecutor,
        List<ResolverProviderFactory> resolverFactories,
        ResolveIvyFactory moduleDependencyResolverFactory,
        ProjectDependencyResolver projectDependencyResolver,
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
        AttributeDesugaring attributeDesugaring,
        ModuleComponentGraphResolveStateFactory moduleResolveStateFactory,
        ComponentIdGenerator idGenerator,
        GraphVariantSelector variantSelector
    ) {
        this.resolverFactories = resolverFactories;
        this.moduleDependencyResolverFactory = moduleDependencyResolverFactory;
        this.projectDependencyResolver = projectDependencyResolver;
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
        this.attributeDesugaring = attributeDesugaring;
        this.moduleResolveStateFactory = moduleResolveStateFactory;
        this.idGenerator = idGenerator;
        this.variantSelector = variantSelector;
    }

    @Override
    public Resolver create(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema,
        GlobalDependencyResolutionRules metadataHandler
    ) {
        validateResolutionStrategy(resolveContext.getResolutionStrategy());
        ComponentResolvers allResolvers = createResolverChain(resolveContext, repositories, consumerSchema, metadataHandler);
        return new ResolveContextResolver(resolveContext, allResolvers, consumerSchema, metadataHandler);
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

    /**
     * Creates a resolver that can resolve project dependencies, module dependencies, and VCS dependencies.
     */
    private ComponentResolvers createResolverChain(
        ResolveContext resolveContext,
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema,
        GlobalDependencyResolutionRules metadataHandler
    ) {
        List<ComponentResolvers> resolvers = new ArrayList<>(3);
        for (ResolverProviderFactory factory : resolverFactories) {
            factory.create(resolvers);
        }
        resolvers.add(projectDependencyResolver);
        resolvers.add(createModuleRepositoryResolvers(
            repositories,
            consumerSchema,
            // We should avoid using `resolveContext` if possible here.
            // We should not need to know _what_ we're resolving in order to construct a resolver for a set of repositories.
            // These parameters are used to support various features in `RepositoryContentDescriptor`.
            resolveContext.getName(),
            resolveContext.getResolutionStrategy(),
            resolveContext.getAttributes(),
            metadataHandler
        ));

        return new ComponentResolversChain(resolvers);
    }

    /**
     * Creates a resolver that resolves module components from the given repositories.
     */
    private ComponentResolvers createModuleRepositoryResolvers(
        List<? extends ResolutionAwareRepository> repositories,
        AttributesSchemaInternal consumerSchema,
        String resolveContextName,
        ResolutionStrategyInternal resolutionStrategy,
        AttributeContainerInternal requestedAttributes,
        GlobalDependencyResolutionRules metadataHandler
    ) {
        return moduleDependencyResolverFactory.create(
            resolveContextName,
            resolutionStrategy,
            repositories,
            metadataHandler.getComponentMetadataProcessorFactory(),
            requestedAttributes,
            consumerSchema,
            attributesFactory,
            componentMetadataSupplierRuleExecutor
        );
    }

    /**
     * A resolver that resolves a {@link ResolveContext} and visits the resulting graph.
     *
     * <p>It would be nice to make this static, but since this requires so many
     * services, construction would be very verbose.
     * Ideally, we would use dependency-injection here, but the current
     * service hierarchy makes that difficult.</p>
     */
    private class ResolveContextResolver implements Resolver {
        private final ResolveContext resolveContext;
        private final ComponentResolvers resolvers;
        private final AttributesSchemaInternal consumerSchema;
        private final GlobalDependencyResolutionRules metadataHandler;

        public ResolveContextResolver(
            ResolveContext resolveContext,
            ComponentResolvers resolvers,
            AttributesSchemaInternal consumerSchema,
            GlobalDependencyResolutionRules metadataHandler
        ) {
            this.resolveContext = resolveContext;
            this.resolvers = resolvers;
            this.consumerSchema = consumerSchema;
            this.metadataHandler = metadataHandler;
        }

        @Override
        public ArtifactResolver getArtifactResolver() {
            return resolvers.getArtifactResolver();
        }

        @Override
        public void resolveGraph(
            Spec<? super DependencyMetadata> edgeFilter,
            boolean includeSyntheticDependencies,
            List<DependencyGraphVisitor> visitors
        ) {
            LOGGER.debug("Resolving {}", resolveContext);

            ComponentMetaDataResolver componentMetaDataResolver = new ClientModuleResolver(
                resolvers.getComponentResolver(), dependencyMetadataFactory, moduleResolveStateFactory
            );

            ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
            ModuleConflictHandler conflictHandler = createModuleConflictHandler(resolutionStrategy.getConflictResolution());
            DefaultCapabilitiesConflictHandler capabilitiesConflictHandler = createCapabilitiesConflictHandler(resolutionStrategy.getCapabilitiesResolutionRules());
            DependencySubstitutionApplicator applicator = createDependencySubstitutionApplicator(resolutionStrategy.getDependencySubstitutionRule());

            DependencyGraphBuilder builder = new DependencyGraphBuilder(
                resolvers.getComponentIdResolver(),
                componentMetaDataResolver,
                conflictHandler,
                capabilitiesConflictHandler,
                edgeFilter,
                consumerSchema,
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
                variantSelector
            );

            List<? extends DependencyMetadata> syntheticDependencies = includeSyntheticDependencies
                ? resolveContext.getSyntheticDependencies()
                : Collections.emptyList();

            builder.resolve(resolveContext.toRootComponent(), resolutionStrategy, syntheticDependencies, new CompositeDependencyGraphVisitor(visitors));
        }

        private DependencySubstitutionApplicator createDependencySubstitutionApplicator(ImmutableActionSet<DependencySubstitutionInternal> substitutionRules) {
            DependencySubstitutionApplicator applicator;
            if (substitutionRules.isEmpty()) {
                applicator = NO_OP;
            } else {
                applicator = new CachingDependencySubstitutionApplicator(new DefaultDependencySubstitutionApplicator(componentSelectionDescriptorFactory, substitutionRules, instantiator));
            }
            return applicator;
        }

        private ModuleConflictHandler createModuleConflictHandler(ConflictResolution conflictResolution) {
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

}
