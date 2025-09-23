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
import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dsl.ImmutableModuleReplacements;
import org.gradle.api.internal.artifacts.ivyservice.ResolutionParameters;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DefaultDependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy.CapabilitiesResolutionInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.ComponentState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder.DependencyGraphBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.inject.Inject;
import java.util.List;

import static org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator.NO_OP;

/**
 * Resolves a dependency graph and visits it. Essentially, this class is a {@link DependencyGraphBuilder} executor.
 */
@ServiceScope(Scope.Project.class)
public class DependencyGraphResolver {

    private final VersionComparator versionComparator;
    private final VersionParser versionParser;
    private final InstantiatorFactory instantiatorFactory;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final DependencyGraphBuilder dependencyGraphBuilder;
    private final InMemoryCacheFactory cacheFactory;

    @Inject
    public DependencyGraphResolver(
        VersionComparator versionComparator,
        VersionParser versionParser,
        InstantiatorFactory instantiatorFactory,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        DependencyGraphBuilder dependencyGraphBuilder,
        InMemoryCacheFactory cacheFactory
    ) {
        this.versionComparator = versionComparator;
        this.versionParser = versionParser;
        this.instantiatorFactory = instantiatorFactory;
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.dependencyGraphBuilder = dependencyGraphBuilder;
        this.cacheFactory = cacheFactory;
    }

    /**
     * Perform a graph resolution, visiting the resolved graph with the provided visitor.
     *
     * <p>We should keep this class independent of
     * {@link LegacyResolutionParameters} and
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
        ImmutableList<CapabilitiesResolutionInternal.CapabilityResolutionRule> capabilityResolutionRules,
        boolean failingOnDynamicVersions,
        boolean failingOnChangingVersions,
        ResolutionParameters.FailureResolutions failureResolutions,
        DependencyGraphVisitor modelVisitor
    ) {
        DependencySubstitutionApplicator substitutionApplicator = createDependencySubstitutionApplicator(dependencySubstitutionRule);
        ModuleConflictResolver<ComponentState> moduleConflictResolver = createModuleConflictResolver(conflictResolution);

        dependencyGraphBuilder.resolve(
            rootComponent,
            rootVariant,
            syntheticDependencies,
            edgeFilter,
            componentSelectorConverter,
            componentIdResolver,
            componentMetaDataResolver,
            moduleReplacements,
            substitutionApplicator,
            moduleConflictResolver,
            capabilityResolutionRules,
            conflictResolution,
            failingOnDynamicVersions,
            failingOnChangingVersions,
            failureResolutions,
            modelVisitor
        );
    }

    private DependencySubstitutionApplicator createDependencySubstitutionApplicator(ImmutableActionSet<DependencySubstitutionInternal> dependencySubstitutionRule) {
        if (dependencySubstitutionRule.isEmpty()) {
            return NO_OP;
        }

        return new DefaultDependencySubstitutionApplicator(
            componentSelectionDescriptorFactory,
            dependencySubstitutionRule,
            instantiatorFactory,
            cacheFactory
        );
    }

    private ModuleConflictResolver<ComponentState> createModuleConflictResolver(ConflictResolution conflictResolution) {
        ModuleConflictResolver<ComponentState> moduleConflictResolver = new LatestModuleConflictResolver<>(versionComparator, versionParser);
        if (conflictResolution != ConflictResolution.preferProjectModules) {
            return moduleConflictResolver;
        }
        return new ProjectDependencyForcingResolver<>(moduleConflictResolver);
    }

}
