/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.SelectedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultGraphBuilder;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.LocalComponentGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.model.CalculatedValue;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Detects empty resolutions and skips a lot of work in those cases.
 */
public class ShortCircuitingResolutionExecutor {
    private final ResolutionExecutor delegate;
    private final AttributeDesugaring attributeDesugaring;
    private final DependencyLockingProvider dependencyLockingProvider;

    public ShortCircuitingResolutionExecutor(
        ResolutionExecutor delegate,
        AttributeDesugaring attributeDesugaring,
        DependencyLockingProvider dependencyLockingProvider
    ) {
        this.delegate = delegate;
        this.attributeDesugaring = attributeDesugaring;
        this.dependencyLockingProvider = dependencyLockingProvider;
    }

    public ResolverResults resolveBuildDependencies(LegacyResolutionParameters legacyParams, ResolutionParameters params, CalculatedValue<ResolverResults> futureCompleteResults) {
        if (hasDependencies(params)) {
            return delegate.resolveBuildDependencies(legacyParams, params, futureCompleteResults);
        }

        VisitedGraphResults graphResults = emptyGraphResults(params);
        return DefaultResolverResults.buildDependenciesResolved(graphResults, EmptyResults.INSTANCE,
            DefaultResolverResults.DefaultLegacyResolverResults.buildDependenciesResolved(EmptyResults.INSTANCE)
        );
    }

    public ResolverResults resolveGraph(LegacyResolutionParameters legacyParams, ResolutionParameters params, List<ResolutionAwareRepository> repositories) throws ResolveException {
        if (hasDependencies(params)) {
            return delegate.resolveGraph(legacyParams, params, repositories);
        }

        if (params.isDependencyLockingEnabled()) {
            DependencyLockingState lockingState = dependencyLockingProvider.loadLockState(params.getDependencyLockingId(), params.getResolutionHost().displayName());
            if (lockingState.mustValidateLockState() && !lockingState.getLockedDependencies().isEmpty()) {
                // Invalid lock state, need to do a real resolution to gather locking failures
                return delegate.resolveGraph(legacyParams, params, repositories);
            }
            dependencyLockingProvider.persistResolvedDependencies(params.getDependencyLockingId(), params.getResolutionHost().displayName(), Collections.emptySet(), Collections.emptySet());
        }

        VisitedGraphResults graphResults = emptyGraphResults(params);
        ResolvedConfiguration resolvedConfiguration = new DefaultResolvedConfiguration(
            graphResults, params.getResolutionHost(), EmptyResults.INSTANCE, new EmptyLenientConfiguration()
        );
        return DefaultResolverResults.graphResolved(graphResults, EmptyResults.INSTANCE,
            DefaultResolverResults.DefaultLegacyResolverResults.graphResolved(
                EmptyResults.INSTANCE, resolvedConfiguration
            )
        );
    }

    private static boolean hasDependencies(ResolutionParameters params) {
        LocalVariantGraphResolveState rootVariant = params.getRootVariant();

        if (!rootVariant.getFiles().isEmpty()) {
            return true;
        }

        for (DependencyMetadata dependency : rootVariant.getDependencies()) {
            if (!dependency.isConstraint()) {
                return true;
            }
        }

        // All dependencies are constraints
        return false;
    }

    private VisitedGraphResults emptyGraphResults(ResolutionParameters params) {
        LocalComponentGraphResolveState rootComponent = params.getRootComponent();
        VariantGraphResolveState rootVariant = params.getRootVariant();

        MinimalResolutionResult emptyResult = ResolutionResultGraphBuilder.empty(
            rootComponent.getModuleVersionId(),
            rootComponent.getId(),
            rootVariant.getAttributes(),
            getCapabilities(rootComponent, rootVariant),
            rootVariant.getName(),
            attributeDesugaring
        );
        return new DefaultVisitedGraphResults(emptyResult, Collections.emptySet(), null);
    }

    private static ImmutableCapabilities getCapabilities(
        LocalComponentGraphResolveState rootComponent,
        VariantGraphResolveState rootVariant
    ) {
        ImmutableCapabilities capabilities = rootVariant.getMetadata().getCapabilities();
        if (capabilities.asSet().isEmpty()) {
            return ImmutableCapabilities.of(rootComponent.getDefaultCapability());
        } else {
            return capabilities;
        }
    }

    public static class EmptyResults implements VisitedArtifactSet, SelectedArtifactSet, ResolverResults.LegacyResolverResults.LegacyVisitedArtifactSet, SelectedArtifactResults {

        public static final EmptyResults INSTANCE = new EmptyResults();

        @Override
        public SelectedArtifactSet select(ArtifactSelectionSpec spec) {
            return this;
        }

        @Override
        public SelectedArtifactResults selectLegacy(ArtifactSelectionSpec spec, boolean lenient) {
            return this;
        }

        @Override
        public SelectedArtifactSet select(Spec<? super Dependency> dependencySpec) {
            return this;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }

        @Override
        public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
        }

        @Override
        public ResolvedArtifactSet getArtifacts() {
            return ResolvedArtifactSet.EMPTY;
        }

        @Override
        public ResolvedArtifactSet getArtifactsWithId(int id) {
            return ResolvedArtifactSet.EMPTY;
        }
    }

    @VisibleForTesting
    public static class EmptyLenientConfiguration implements LenientConfigurationInternal {

        @Override
        public ArtifactSelectionSpec getImplicitSelectionSpec() {
            return new ArtifactSelectionSpec(
                ImmutableAttributes.EMPTY, Specs.satisfyAll(), false, false, ResolutionStrategy.SortOrder.DEFAULT
            );
        }

        @Override
        public SelectedArtifactSet select(Spec<? super Dependency> dependencySpec) {
            return EmptyResults.INSTANCE;
        }

        @Override
        public Set<ResolvedDependency> getFirstLevelModuleDependencies() {
            return Collections.emptySet();
        }

        @Override
        @Deprecated
        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
            DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getFirstLevelModuleDependencies(Spec)")
                .withAdvice("Use getFirstLevelModuleDependencies() instead.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
                .nagUser();

            return Collections.emptySet();
        }

        @Override
        public Set<ResolvedDependency> getAllModuleDependencies() {
            return Collections.emptySet();
        }

        @Override
        public Set<UnresolvedDependency> getUnresolvedModuleDependencies() {
            return Collections.emptySet();
        }

        @Override
        @Deprecated
        public Set<File> getFiles() {
            DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getFiles()")
                .withAdvice("Use a lenient ArtifactView instead.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "deprecate_legacy_configuration_get_files")
                .nagUser();

            return Collections.emptySet();
        }

        @Override
        @Deprecated
        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) {
            DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getFiles(Spec)")
                .withAdvice("Use a lenient ArtifactView with a componentFilter instead.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
                .nagUser();

            return Collections.emptySet();
        }

        @Override
        public Set<ResolvedArtifact> getArtifacts() {
            return Collections.emptySet();
        }

        @Override
        @Deprecated
        public Set<ResolvedArtifact> getArtifacts(Spec<? super Dependency> dependencySpec) {
            DeprecationLogger.deprecateMethod(LenientConfiguration.class, "getArtifacts(Spec)")
                .withAdvice("Use a lenient ArtifactView with a componentFilter instead.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "deprecate_filtered_configuration_file_and_filecollection_methods")
                .nagUser();

            return Collections.emptySet();
        }
    }
}
