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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.CachingDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyMetadataFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.LastCandidateCapabilityResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.UserConfiguredCapabilityResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveStateFactory;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;

import javax.inject.Inject;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator.NO_OP;

/**
 * Resolves a dependency graph and visits it. Essentially, this class is a {@link DependencyGraphBuilder} executor.
 */
public class DependencyGraphResolver {
    private final DependencyMetadataFactory dependencyMetadataFactory;
    private final VersionComparator versionComparator;
    private final VersionParser versionParser;
    private final InstantiatorFactory instantiatorFactory;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final ModuleComponentGraphResolveStateFactory moduleResolveStateFactory;
    private final DependencyGraphBuilder dependencyGraphBuilder;

    @Inject
    public DependencyGraphResolver(
        DependencyMetadataFactory dependencyMetadataFactory,
        VersionComparator versionComparator,
        VersionParser versionParser,
        InstantiatorFactory instantiatorFactory,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        ModuleComponentGraphResolveStateFactory moduleResolveStateFactory,
        DependencyGraphBuilder dependencyGraphBuilder
    ) {
        this.dependencyMetadataFactory = dependencyMetadataFactory;
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.instantiatorFactory = instantiatorFactory;
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.moduleResolveStateFactory = moduleResolveStateFactory;
        this.dependencyGraphBuilder = dependencyGraphBuilder;
    }

    /**
     * Perform a graph resolution, visiting the resolved graph with the provided visitor.
     *
     * <p>We should keep this class independent of
     * {@link org.gradle.api.internal.artifacts.ResolveContext} and
     * {@link org.gradle.api.artifacts.ResolutionStrategy}</p>, as those are tightly
     * coupled to a Configuration, and this resolver should be able to resolve non-Configuration types.
     */
    public void resolve(
        LocalComponentGraphResolveState rootComponent,
        LocalVariantGraphResolveState rootVariant,
        List<? extends DependencyMetadata> syntheticDependencies,
        Spec<? super DependencyMetadata> edgeFilter,
        ComponentSelectorConverter componentSelectorConverter,
        DependencyToComponentIdResolver componentIdResolver,
        ComponentMetaDataResolver componentMetaDataResolver,
        ImmutableModuleReplacements moduleReplacements,
        ImmutableActionSet<DependencySubstitutionInternal> dependencySubstitutionRule,
        ConflictResolution conflictResolution,
        CapabilitiesResolutionInternal capabilitiesResolutionRules,
        boolean failingOnDynamicVersions,
        boolean failingOnChangingVersions,
        DependencyGraphVisitor modelVisitor
    ) {
        ComponentMetaDataResolver clientModuleResolver = new ClientModuleResolver(
            componentMetaDataResolver,
            dependencyMetadataFactory,
            moduleResolveStateFactory
        );

        DependencySubstitutionApplicator substitutionApplicator = createDependencySubstitutionApplicator(dependencySubstitutionRule);
        ModuleConflictResolver<ComponentState> moduleConflictResolver = createModuleConflictResolver(conflictResolution);
        List<CapabilitiesConflictHandler.Resolver> capabilityConflictResolvers = createCapabilityConflictResolvers(capabilitiesResolutionRules);

        dependencyGraphBuilder.resolve(
            rootComponent,
            rootVariant,
            syntheticDependencies,
            edgeFilter,
            componentSelectorConverter,
            componentIdResolver,
            clientModuleResolver,
            moduleReplacements,
            substitutionApplicator,
            moduleConflictResolver,
            capabilityConflictResolvers,
            conflictResolution,
            failingOnDynamicVersions,
            failingOnChangingVersions,
            modelVisitor
        );
    }

    private DependencySubstitutionApplicator createDependencySubstitutionApplicator(ImmutableActionSet<DependencySubstitutionInternal> dependencySubstitutionRule) {
        if (dependencySubstitutionRule.isEmpty()) {
            return NO_OP;
        }

        return new CachingDependencySubstitutionApplicator(new DefaultDependencySubstitutionApplicator(componentSelectionDescriptorFactory, dependencySubstitutionRule, instantiatorFactory));
    }

    private ModuleConflictResolver<ComponentState> createModuleConflictResolver(ConflictResolution conflictResolution) {
        ModuleConflictResolver<ComponentState> moduleConflictResolver = new LatestModuleConflictResolver<>(versionComparator, versionParser);
        if (conflictResolution != ConflictResolution.preferProjectModules) {
            return moduleConflictResolver;
        }
        return new ProjectDependencyForcingResolver<>(moduleConflictResolver);
    }

    private static List<CapabilitiesConflictHandler.Resolver> createCapabilityConflictResolvers(CapabilitiesResolutionInternal capabilitiesResolutionRules) {

        // The order of these resolvers is significant. They run in the declared order.
        return ImmutableList.of(
            // Candidates that are no longer selected are filtered out before these resolvers are executed.
            // If there is only one candidate at the beginning of conflict resolution, select that candidate.
            new LastCandidateCapabilityResolver(),

            // Otherwise, let the user resolvers reject candidates.
            new UserConfiguredCapabilityResolver(capabilitiesResolutionRules),

            // If there is one candidate left after the user resolvers are executed, select that candidate.
            new LastCandidateCapabilityResolver()
        );
    }
}
