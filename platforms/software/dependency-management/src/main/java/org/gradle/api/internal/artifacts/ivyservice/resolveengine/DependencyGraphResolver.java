/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.dsl.ModuleReplacementsData;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.CachingDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory;
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
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator.NO_OP;

/**
 * Resolves a {@link ResolveContext} and visits the resulting graph. Essentially, this
 * class is a {@link DependencyGraphBuilder} factory and executor.
 */
public class DependencyGraphResolver {
    private final DependencyMetadataFactory dependencyMetadataFactory;
    private final VersionComparator versionComparator;
    private final ModuleExclusions moduleExclusions;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final VersionSelectorScheme versionSelectorScheme;
    private final VersionParser versionParser;
    private final Instantiator instantiator;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final AttributeDesugaring attributeDesugaring;
    private final ModuleComponentGraphResolveStateFactory moduleResolveStateFactory;
    private final ComponentIdGenerator idGenerator;
    private final GraphVariantSelector variantSelector;
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public DependencyGraphResolver(
        BuildOperationExecutor buildOperationExecutor,
        DependencyMetadataFactory dependencyMetadataFactory,
        VersionComparator versionComparator,
        ModuleExclusions moduleExclusions,
        ComponentSelectorConverter componentSelectorConverter,
        VersionSelectorScheme versionSelectorScheme,
        VersionParser versionParser,
        InstantiatorFactory instantiatorFactory,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        AttributeDesugaring attributeDesugaring,
        ModuleComponentGraphResolveStateFactory moduleResolveStateFactory,
        ComponentIdGenerator idGenerator,
        GraphVariantSelector variantSelector,
        ImmutableAttributesFactory attributesFactory
    ) {
        this.dependencyMetadataFactory = dependencyMetadataFactory;
        this.versionComparator = versionComparator;
        this.moduleExclusions = moduleExclusions;
        this.buildOperationExecutor = buildOperationExecutor;
        this.componentSelectorConverter = componentSelectorConverter;
        this.versionSelectorScheme = versionSelectorScheme;
        this.versionParser = versionParser;
        this.instantiator = instantiatorFactory.decorateScheme().instantiator();
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.attributeDesugaring = attributeDesugaring;
        this.moduleResolveStateFactory = moduleResolveStateFactory;
        this.idGenerator = idGenerator;
        this.variantSelector = variantSelector;
        this.attributesFactory = attributesFactory;
    }

    /**
     * Perform a graph resolution, visiting the resolved graph with the provided visitors.
     */
    public void resolveGraph(
        ResolveContext resolveContext,
        ComponentResolvers resolvers,
        AttributesSchemaInternal consumerSchema,
        GlobalDependencyResolutionRules metadataHandler,
        Spec<? super DependencyMetadata> edgeFilter,
        boolean includeSyntheticDependencies,
        List<DependencyGraphVisitor> visitors
    ) {
        ComponentMetaDataResolver componentMetaDataResolver = new ClientModuleResolver(
            resolvers.getComponentResolver(), dependencyMetadataFactory, moduleResolveStateFactory
        );

        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        ModuleConflictHandler conflictHandler = createModuleConflictHandler(
            resolutionStrategy.getConflictResolution(),
            metadataHandler.getModuleMetadataProcessor().getModuleReplacements()
        );

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

    private ModuleConflictHandler createModuleConflictHandler(ConflictResolution conflictResolution, ModuleReplacementsData moduleReplacements) {
        ModuleConflictResolver<ComponentState> conflictResolver =
            new ConflictResolverFactory(versionComparator, versionParser).createConflictResolver(conflictResolution);
        return new DefaultConflictHandler(conflictResolver, moduleReplacements);
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
