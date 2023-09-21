/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer;
import org.gradle.api.internal.artifacts.ResolverFactory;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesOnlyVisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactsResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.FailOnVersionConflictGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.DefaultResolvedConfigurationBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolutionFailureCollector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedConfigurationDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentDetailsSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.FileDependencyCollectingGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.SelectedVariantSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.VariantSelectorFactory;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.locking.DependencyLockingGraphVisitor;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultConfigurationResolver implements ConfigurationResolver {
    private static final Spec<DependencyMetadata> IS_LOCAL_EDGE = element -> element.getSelector() instanceof ProjectComponentSelector;
    private final ResolverFactory resolverFactory;
    private final RepositoriesSupplier repositoriesSupplier;
    private final GlobalDependencyResolutionRules metadataHandler;
    private final ResolutionResultsStoreFactory storeFactory;
    private final boolean buildProjectDependencies;
    private final AttributesSchemaInternal consumerSchema;
    private final VariantSelectorFactory variantSelectorFactory;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final BuildIdentifier currentBuild;
    private final AttributeDesugaring attributeDesugaring;
    private final DependencyVerificationOverride dependencyVerificationOverride;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final ResolveExceptionContextualizer exceptionContextualizer;
    private final ComponentDetailsSerializer componentDetailsSerializer;
    private final SelectedVariantSerializer selectedVariantSerializer;
    private final ResolvedVariantCache resolvedVariantCache;

    public DefaultConfigurationResolver(
        ResolverFactory resolverFactory,
        RepositoriesSupplier repositoriesSupplier,
        GlobalDependencyResolutionRules metadataHandler,
        ResolutionResultsStoreFactory storeFactory,
        boolean buildProjectDependencies,
        AttributesSchemaInternal consumerSchema,
        VariantSelectorFactory variantSelectorFactory,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        BuildOperationExecutor buildOperationExecutor,
        ArtifactTypeRegistry artifactTypeRegistry,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        ComponentSelectorConverter componentSelectorConverter,
        AttributeContainerSerializer attributeContainerSerializer,
        BuildIdentifier currentBuild, AttributeDesugaring attributeDesugaring,
        DependencyVerificationOverride dependencyVerificationOverride,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        WorkerLeaseService workerLeaseService,
        ResolveExceptionContextualizer exceptionContextualizer,
        ComponentDetailsSerializer componentDetailsSerializer,
        SelectedVariantSerializer selectedVariantSerializer,
        ResolvedVariantCache resolvedVariantCache
    ) {
        this.resolverFactory = resolverFactory;
        this.repositoriesSupplier = repositoriesSupplier;
        this.metadataHandler = metadataHandler;
        this.storeFactory = storeFactory;
        this.buildProjectDependencies = buildProjectDependencies;
        this.consumerSchema = consumerSchema;
        this.variantSelectorFactory = variantSelectorFactory;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.currentBuild = currentBuild;
        this.attributeDesugaring = attributeDesugaring;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.workerLeaseService = workerLeaseService;
        this.exceptionContextualizer = exceptionContextualizer;
        this.componentDetailsSerializer = componentDetailsSerializer;
        this.selectedVariantSerializer = selectedVariantSerializer;
        this.resolvedVariantCache = resolvedVariantCache;
    }

    @Override
    public ResolverResults resolveBuildDependencies(ResolveContext resolveContext) {
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
        InMemoryResolutionResultBuilder resolutionResultBuilder = new InMemoryResolutionResultBuilder();
        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild);
        DefaultResolvedArtifactsBuilder artifactsVisitor = new DefaultResolvedArtifactsBuilder(buildProjectDependencies, resolutionStrategy.getSortOrder());

        ResolverFactory.Resolver resolver = resolverFactory.create(resolveContext, ImmutableList.of(), consumerSchema, metadataHandler);

        DependencyGraphVisitor artifactsGraphVisitor = new ResolvedArtifactsGraphVisitor(
            artifactsVisitor,
            artifactTypeRegistry,
            calculatedValueContainerFactory,
            resolver.getArtifactResolver(),
            resolvedVariantCache
        );

        resolver.resolveGraph(IS_LOCAL_EDGE, false, ImmutableList.of(
            failureCollector, resolutionResultBuilder, localComponentsVisitor, artifactsGraphVisitor
        ));

        Set<UnresolvedDependency> unresolvedDependencies = failureCollector.complete(Collections.emptySet());
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResultBuilder.getResolutionResult(), unresolvedDependencies, null);

        ArtifactVariantSelector artifactVariantSelector = variantSelectorFactory.create(resolveContext.getDependenciesResolverFactory());
        VisitedArtifactSet artifacts = new BuildDependenciesOnlyVisitedArtifactSet(graphResults, artifactsVisitor.complete(), artifactVariantSelector);
        return DefaultResolverResults.buildDependenciesResolved(graphResults, localComponentsVisitor, artifacts);
    }

    @Override
    public ResolverResults resolveGraph(ResolveContext resolveContext) {
        StoreSet stores = storeFactory.createStoreSet();

        BinaryStore oldModelStore = stores.nextBinaryStore();
        Store<TransientConfigurationResults> oldModelCache = stores.oldModelCache();
        TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache, moduleIdentifierFactory, buildOperationExecutor);
        DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
        ResolvedConfigurationDependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder);

        BinaryStore newModelStore = stores.nextBinaryStore();
        Store<ResolvedComponentResult> newModelCache = stores.newModelCache();
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        StreamingResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache, attributeContainerSerializer, componentDetailsSerializer, selectedVariantSerializer, attributeDesugaring, componentSelectionDescriptorFactory, resolutionStrategy.getReturnAllVariants());

        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild);

        DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(buildProjectDependencies, resolutionStrategy.getSortOrder());
        FileDependencyCollectingGraphVisitor fileDependencyVisitor = new FileDependencyCollectingGraphVisitor();
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);

        ImmutableList.Builder<DependencyGraphVisitor> graphVisitors = ImmutableList.builder();
        graphVisitors.add(newModelBuilder);
        graphVisitors.add(localComponentsVisitor);
        graphVisitors.add(failureCollector);

        FailOnVersionConflictGraphVisitor versionConflictVisitor = null;
        if (resolutionStrategy.getConflictResolution() == ConflictResolution.strict) {
            Path projectPath = resolveContext.getDomainObjectContext().getProjectPath();
            // projectPath is null for settings execution
            String path = projectPath != null ? projectPath.getPath() : "";
            versionConflictVisitor = new FailOnVersionConflictGraphVisitor(path, resolveContext.getName());
            graphVisitors.add(versionConflictVisitor);
        }

        DependencyLockingGraphVisitor lockingVisitor = null;
        if (resolutionStrategy.isDependencyLockingEnabled()) {
            lockingVisitor = new DependencyLockingGraphVisitor(resolveContext.getName(), resolutionStrategy.getDependencyLockingProvider());
            graphVisitors.add(lockingVisitor);
        } else {
            resolutionStrategy.confirmUnlockedConfigurationResolved(resolveContext.getName());
        }

        ResolverFactory.Resolver resolver = resolverFactory.create(resolveContext, getRepositories(), consumerSchema, metadataHandler);

        List<DependencyArtifactsVisitor> artifactVisitors = ImmutableList.of(oldModelVisitor, fileDependencyVisitor, artifactsBuilder);
        graphVisitors.add(new ResolvedArtifactsGraphVisitor(
            new CompositeDependencyArtifactsVisitor(artifactVisitors),
            artifactTypeRegistry,
            calculatedValueContainerFactory,
            resolver.getArtifactResolver(),
            resolvedVariantCache
        ));

        resolver.resolveGraph(Specs.satisfyAll(), true, graphVisitors.build());

        VisitedArtifactsResults artifactsResults = artifactsBuilder.complete();
        VisitedFileDependencyResults fileDependencyResults = fileDependencyVisitor.complete();
        ResolvedGraphResults legacyGraphResults = oldModelBuilder.complete();

        // TODO: Failures from dependency locking should be included in the nonFatalFailuresBuilder.
        Set<UnresolvedDependency> lockingFailures = Collections.emptySet();
        ImmutableSet.Builder<Throwable> nonFatalFailuresBuilder = ImmutableSet.builder();
        if (lockingVisitor != null) {
            lockingFailures = lockingVisitor.collectLockingFailures();
        }
        if (versionConflictVisitor != null) {
            for (Throwable failure : versionConflictVisitor.collectConflictFailures()) {
                nonFatalFailuresBuilder.add(failure);
            }
        }

        Set<Throwable> nonFatalFailures = nonFatalFailuresBuilder.build();
        Set<UnresolvedDependency> resolutionFailures = failureCollector.complete(lockingFailures);

        MinimalResolutionResult resolutionResult = newModelBuilder.complete(lockingFailures);
        ResolveException failure = exceptionContextualizer.mapFailures(nonFatalFailures, resolveContext.getDisplayName(), "dependencies");
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResult, resolutionFailures, failure);

        ArtifactResolveState artifactResolveState = new ArtifactResolveState(graphResults, legacyGraphResults, artifactsResults, fileDependencyResults, oldTransientModelBuilder);
        ArtifactVariantSelector selector = variantSelectorFactory.create(resolveContext.getDependenciesResolverFactory());
        VisitedArtifactSet visitedArtifactSet = new BuildDependenciesOnlyVisitedArtifactSet(graphResults, artifactsResults, selector);
        ResolverResults results = DefaultResolverResults.graphResolved(graphResults, localComponentsVisitor, visitedArtifactSet, artifactResolveState);

        // Only write dependency locks if resolution completed without failure.
        if (lockingVisitor != null && !graphResults.hasAnyFailure()) {
            lockingVisitor.writeLocks();
        }

        return results;
    }

    @Override
    public List<ResolutionAwareRepository> getRepositories() {
        return Cast.uncheckedCast(repositoriesSupplier.get());
    }

    @Override
    public ResolverResults resolveArtifacts(ResolveContext resolveContext, ResolverResults graphResults) {
        ArtifactResolveState resolveState = graphResults.getArtifactResolveState();
        VisitedArtifactsResults artifactResults = resolveState.artifactsResults;
        TransientConfigurationResultsBuilder transientConfigurationResultsBuilder = resolveState.transientConfigurationResultsBuilder;

        TransientConfigurationResultsLoader transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(transientConfigurationResultsBuilder, resolveState.legacyGraphResults);

        ArtifactVariantSelector selector = variantSelectorFactory.create(resolveContext.getDependenciesResolverFactory());
        DefaultLenientConfiguration result = new DefaultLenientConfiguration(
            resolveContext,
            resolveState.graphResults,
            artifactResults,
            resolveState.fileDependencyResults,
            transientConfigurationResultsFactory,
            buildOperationExecutor,
            dependencyVerificationOverride,
            workerLeaseService,
            selector
        );

        return DefaultResolverResults.artifactsResolved(graphResults.getVisitedGraph(), graphResults.getResolvedLocalComponents(), new DefaultResolvedConfiguration(result), result);
    }
}
