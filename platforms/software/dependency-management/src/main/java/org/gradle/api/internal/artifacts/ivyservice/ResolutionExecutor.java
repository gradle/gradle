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
import org.gradle.StartParameter;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessorFactory;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.LegacyResolutionParameters;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingState;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactories;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolversChain;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DependencyGraphResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultVisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VariantArtifactSetCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.Conflict;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.FailOnVersionConflictGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictException;
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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AdhocHandlingComponentResultSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.FileDependencyCollectingGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.internal.artifacts.transform.ConsumerProvidedVariantFinder;
import org.gradle.api.internal.artifacts.transform.DefaultTransformUpstreamDependenciesResolver;
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver;
import org.gradle.api.internal.artifacts.transform.TransformedVariantFactory;
import org.gradle.api.internal.attributes.AttributeSchemaServices;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.api.internal.attributes.immutable.artifact.ImmutableArtifactTypeRegistry;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.locking.DependencyLockingGraphVisitor;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resolve.resolver.ResolvedVariantCache;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Performs a graph resolution. This class acts as the entry-point to the dependency resolution process.
 *
 * <p>Resolution can either be executed partially or completely:</p>
 * <ul>
 *     <li>
 *         During partial resolution, only project dependencies are resolved and all external dependencies are ignored.
 *         These results are faster to calculate and are sufficient for task dependency resolution.
 *     </li>
 *     <li>
 *         During full resolution, the entire graph is traversed and no dependencies are ignored.
 *     </li>
 * </ul>
 */
public class ResolutionExecutor {

    private static final Spec<DependencyMetadata> IS_LOCAL_EDGE = element -> element.getSelector() instanceof ProjectComponentSelector;

    private final DependencyGraphResolver dependencyGraphResolver;
    private final ResolutionResultsStoreFactory storeFactory;
    private final boolean buildProjectDependencies;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final CapabilitySelectorSerializer capabilitySelectorSerializer;
    private final BuildIdentifier currentBuild;
    private final ResolvedArtifactSetResolver artifactSetResolver;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final AdhocHandlingComponentResultSerializer componentResultSerializer;
    private final ResolvedVariantCache resolvedVariantCache;
    private final GraphVariantSelector graphVariantSelector;
    private final ProjectStateRegistry projectStateRegistry;
    private final LocalComponentRegistry localComponentRegistry;
    private final List<ResolverProviderFactory> resolverFactories;
    private final ExternalModuleComponentResolverFactory externalResolverFactory;
    private final ProjectDependencyResolver projectDependencyResolver;
    private final DependencyLockingProvider dependencyLockingProvider;
    private final TransformedVariantFactory transformedVariantFactory;
    private final AttributesFactory attributesFactory;
    private final DomainObjectContext domainObjectContext;
    private final TaskDependencyFactory taskDependencyFactory;
    private final ConsumerProvidedVariantFinder consumerProvidedVariantFinder;
    private final AttributeSchemaServices attributeSchemaServices;
    private final ResolutionFailureHandler resolutionFailureHandler;
    private final VariantArtifactSetCache variantArtifactSetCache;
    private final VariantTransformRegistry transformRegistry;
    private final ComponentMetadataProcessorFactory componentMetadataProcessorFactory;

    @Inject
    public ResolutionExecutor(
        DependencyGraphResolver dependencyGraphResolver,
        ResolutionResultsStoreFactory storeFactory,
        StartParameter startParameter,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        BuildOperationExecutor buildOperationExecutor,
        CalculatedValueContainerFactory calculatedValueContainerFactory,
        ComponentSelectorConverter componentSelectorConverter,
        AttributeContainerSerializer attributeContainerSerializer,
        CapabilitySelectorSerializer capabilitySelectorSerializer,
        BuildState currentBuild,
        ResolvedArtifactSetResolver artifactSetResolver,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        AdhocHandlingComponentResultSerializer componentResultSerializer,
        ResolvedVariantCache resolvedVariantCache,
        GraphVariantSelector graphVariantSelector,
        ProjectStateRegistry projectStateRegistry,
        LocalComponentRegistry localComponentRegistry,
        ResolverProviderFactories resolverFactories,
        ExternalModuleComponentResolverFactory externalResolverFactory,
        ProjectDependencyResolver projectDependencyResolver,
        DependencyLockingProvider dependencyLockingProvider,
        TransformedVariantFactory transformedVariantFactory,
        AttributesFactory attributesFactory,
        DomainObjectContext domainObjectContext,
        TaskDependencyFactory taskDependencyFactory,
        ConsumerProvidedVariantFinder consumerProvidedVariantFinder,
        AttributeSchemaServices attributeSchemaServices,
        ResolutionFailureHandler resolutionFailureHandler,
        VariantArtifactSetCache variantArtifactSetCache,
        VariantTransformRegistry transformRegistry,
        ComponentMetadataProcessorFactory componentMetadataProcessorFactory
    ) {
        this.dependencyGraphResolver = dependencyGraphResolver;
        this.storeFactory = storeFactory;
        this.buildProjectDependencies = startParameter.isBuildProjectDependencies();
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.capabilitySelectorSerializer = capabilitySelectorSerializer;
        this.currentBuild = currentBuild.getBuildIdentifier();
        this.artifactSetResolver = artifactSetResolver;
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.componentResultSerializer = componentResultSerializer;
        this.resolvedVariantCache = resolvedVariantCache;
        this.graphVariantSelector = graphVariantSelector;
        this.projectStateRegistry = projectStateRegistry;
        this.localComponentRegistry = localComponentRegistry;
        this.resolverFactories = resolverFactories.getFactories();
        this.externalResolverFactory = externalResolverFactory;
        this.projectDependencyResolver = projectDependencyResolver;
        this.dependencyLockingProvider = dependencyLockingProvider;
        this.transformedVariantFactory = transformedVariantFactory;
        this.attributesFactory = attributesFactory;
        this.domainObjectContext = domainObjectContext;
        this.taskDependencyFactory = taskDependencyFactory;
        this.consumerProvidedVariantFinder = consumerProvidedVariantFinder;
        this.attributeSchemaServices = attributeSchemaServices;
        this.resolutionFailureHandler = resolutionFailureHandler;
        this.variantArtifactSetCache = variantArtifactSetCache;
        this.transformRegistry = transformRegistry;
        this.componentMetadataProcessorFactory = componentMetadataProcessorFactory;
    }

    /**
     * Traverses enough of the graph to calculate the build dependencies of the graph.
     *
     * @param legacyParams Legacy parameters describing what and how to resolve
     * @param params Immutable thread-safe parameters describing what and how to resolve
     * @param futureCompleteResults The future value of the output of {@link #resolveGraph(LegacyResolutionParameters, ResolutionParameters, List)}. See
     * {@link DefaultTransformUpstreamDependenciesResolver} for why this is needed.
     *
     * @return An immutable result set, containing a subset of the graph that is sufficient to calculate the build dependencies.
     */
    public ResolverResults resolveBuildDependencies(
        LegacyResolutionParameters legacyParams,
        ResolutionParameters params,
        CalculatedValue<ResolverResults> futureCompleteResults
    ) {
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
        InMemoryResolutionResultBuilder resolutionResultBuilder = new InMemoryResolutionResultBuilder(params.getIncludeAllSelectableVariantResults());
        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild, projectStateRegistry);
        DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(buildProjectDependencies);

        ComponentResolvers resolvers = getResolvers(params, legacyParams, Collections.emptyList());
        DependencyGraphVisitor artifactsGraphVisitor = artifactVisitorFor(artifactsBuilder, params.getArtifactTypeRegistry());

        ImmutableList<DependencyGraphVisitor> visitors = ImmutableList.of(failureCollector, resolutionResultBuilder, localComponentsVisitor, artifactsGraphVisitor);
        doResolve(params, legacyParams, ImmutableList.of(), resolvers, IS_LOCAL_EDGE, visitors);
        localComponentsVisitor.complete(ConfigurationInternal.InternalState.BUILD_DEPENDENCIES_RESOLVED);

        Set<UnresolvedDependency> unresolvedDependencies = failureCollector.complete(Collections.emptySet());
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResultBuilder.getResolutionResult(), unresolvedDependencies, null);
        VisitedArtifactResults artifactsResults = artifactsBuilder.complete();

        TransformUpstreamDependenciesResolver.Factory dependenciesResolverFactory = visitedArtifacts -> new DefaultTransformUpstreamDependenciesResolver(
            params.getResolutionHost(),
            params.getConfigurationIdentity(),
            params.getRootVariant().getAttributes(),
            params.getDefaultSortOrder(),
            graphResults,
            visitedArtifacts,
            futureCompleteResults,
            domainObjectContext,
            calculatedValueContainerFactory,
            attributesFactory,
            taskDependencyFactory
        );

        VisitedArtifactSet visitedArtifacts = getVisitedArtifactSet(params, resolvers, graphResults, artifactsResults, dependenciesResolverFactory);

        ResolverResults.LegacyResolverResults legacyResolverResults = DefaultResolverResults.DefaultLegacyResolverResults.buildDependenciesResolved(
            // When resolving build dependencies, we ignore the dependencySpec, potentially capturing a greater
            // set of build dependencies than actually required. This is because it takes a lot of extra information
            // from the visited graph to properly filter artifacts by dependencySpec, and we don't want capture that when
            // calculating build dependencies.
            dependencySpec -> visitedArtifacts.select(getImplicitSelectionSpec(params))
        );

        return DefaultResolverResults.buildDependenciesResolved(graphResults, visitedArtifacts, legacyResolverResults);
    }

    /**
     * Traverses the full dependency graph.
     *
     * @param legacyParams Legacy parameters describing what and how to resolve
     * @param params Immutable thread-safe parameters describing what and how to resolve
     * @param repositories The repositories used to resolve external dependencies
     *
     * @return An immutable result set, containing the full graph of resolved components.
     */
    public ResolverResults resolveGraph(
        LegacyResolutionParameters legacyParams,
        ResolutionParameters params,
        List<ResolutionAwareRepository> repositories
    ) {
        StoreSet stores = storeFactory.createStoreSet();

        BinaryStore oldModelStore = stores.nextBinaryStore();
        Store<TransientConfigurationResults> oldModelCache = stores.oldModelCache();
        TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache, moduleIdentifierFactory, buildOperationExecutor, params.getResolutionHost());
        DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
        ResolvedConfigurationDependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder);

        BinaryStore newModelStore = stores.nextBinaryStore();
        Store<ResolvedComponentResultInternal> newModelCache = stores.newModelCache();
        StreamingResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache, attributeContainerSerializer, capabilitySelectorSerializer, componentResultSerializer, componentSelectionDescriptorFactory, params.getIncludeAllSelectableVariantResults());

        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild, projectStateRegistry);

        DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(buildProjectDependencies);
        FileDependencyCollectingGraphVisitor fileDependencyVisitor = new FileDependencyCollectingGraphVisitor();
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);

        ImmutableList.Builder<DependencyGraphVisitor> graphVisitors = ImmutableList.builder();
        graphVisitors.add(newModelBuilder);
        graphVisitors.add(localComponentsVisitor);
        graphVisitors.add(failureCollector);

        FailOnVersionConflictGraphVisitor versionConflictVisitor = null;
        if (params.getModuleConflictResolutionStrategy() == ConflictResolution.strict) {
            versionConflictVisitor = new FailOnVersionConflictGraphVisitor();
            graphVisitors.add(versionConflictVisitor);
        }

        DependencyLockingGraphVisitor lockingVisitor = null;
        if (params.isDependencyLockingEnabled()) {
            lockingVisitor = new DependencyLockingGraphVisitor(params.getDependencyLockingId(), params.getResolutionHost().displayName(), dependencyLockingProvider);
            graphVisitors.add(lockingVisitor);
        } else {
            dependencyLockingProvider.confirmNotLocked(params.getDependencyLockingId());
        }

        ComponentResolvers resolvers = getResolvers(params, legacyParams, repositories);
        CompositeDependencyArtifactsVisitor artifactVisitors = new CompositeDependencyArtifactsVisitor(ImmutableList.of(
            oldModelVisitor, fileDependencyVisitor, artifactsBuilder
        ));
        graphVisitors.add(artifactVisitorFor(artifactVisitors, params.getArtifactTypeRegistry()));

        doResolve(params, legacyParams, getAllVersionLocks(params), resolvers, Specs.satisfyAll(), graphVisitors.build());
        localComponentsVisitor.complete(ConfigurationInternal.InternalState.GRAPH_RESOLVED);

        VisitedArtifactResults artifactsResults = artifactsBuilder.complete();
        VisitedFileDependencyResults fileDependencyResults = fileDependencyVisitor.complete();
        ResolvedGraphResults legacyGraphResults = oldModelBuilder.complete();

        // TODO: Failures from dependency locking should be included in the nonFatalFailuresBuilder.
        Set<UnresolvedDependency> lockingFailures = Collections.emptySet();
        ImmutableSet.Builder<Throwable> nonFatalFailuresBuilder = ImmutableSet.builder();
        if (lockingVisitor != null) {
            lockingFailures = lockingVisitor.collectLockingFailures();
        }

        if (versionConflictVisitor != null) {
            Set<Conflict> versionConflicts = versionConflictVisitor.getAllConflicts();
            if (!versionConflicts.isEmpty()) {
                List<String> resolutions = params.getFailureResolutions().forVersionConflict(versionConflicts);
                nonFatalFailuresBuilder.add(new VersionConflictException(versionConflicts, resolutions));
            }
        }

        Set<Throwable> nonFatalFailures = nonFatalFailuresBuilder.build();
        Set<UnresolvedDependency> resolutionFailures = failureCollector.complete(lockingFailures);

        MinimalResolutionResult resolutionResult = newModelBuilder.getResolutionResult(lockingFailures);
        Optional<? extends ResolveException> failure = params.getResolutionHost().consolidateFailures("dependencies", nonFatalFailures);
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResult, resolutionFailures, failure.orElse(null));

        // Only write dependency locks if resolution completed without failure.
        if (lockingVisitor != null && !graphResults.hasAnyFailure()) {
            lockingVisitor.writeLocks();
        }

        TransformUpstreamDependenciesResolver.Factory dependenciesResolverFactory = visitedArtifacts -> new DefaultTransformUpstreamDependenciesResolver(
            params.getResolutionHost(),
            params.getConfigurationIdentity(),
            params.getRootVariant().getAttributes(),
            params.getDefaultSortOrder(),
            graphResults,
            visitedArtifacts,
            domainObjectContext,
            calculatedValueContainerFactory,
            attributesFactory,
            taskDependencyFactory
        );

        VisitedArtifactSet visitedArtifacts = getVisitedArtifactSet(params, resolvers, graphResults, artifactsResults, dependenciesResolverFactory);

        // Legacy results
        TransientConfigurationResultsLoader transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(oldTransientModelBuilder, legacyGraphResults);
        DefaultLenientConfiguration lenientConfiguration = new DefaultLenientConfiguration(
            params.getResolutionHost(),
            graphResults,
            visitedArtifacts,
            fileDependencyResults,
            transientConfigurationResultsFactory,
            artifactSetResolver,
            getImplicitSelectionSpec(params)
        );
        ResolverResults.LegacyResolverResults legacyResolverResults = DefaultResolverResults.DefaultLegacyResolverResults.graphResolved(
            lenientConfiguration,
            new DefaultResolvedConfiguration(graphResults, params.getResolutionHost(), visitedArtifacts, lenientConfiguration)
        );

        return DefaultResolverResults.graphResolved(graphResults, visitedArtifacts, legacyResolverResults);
    }

    private static ArtifactSelectionSpec getImplicitSelectionSpec(ResolutionParameters params) {
        ImmutableAttributes requestAttributes = params.getRootVariant().getAttributes();
        ResolutionStrategy.SortOrder sortOrder = params.getDefaultSortOrder();
        return new ArtifactSelectionSpec(requestAttributes, Specs.satisfyAll(), false, false, sortOrder);
    }

    private ResolvedArtifactsGraphVisitor artifactVisitorFor(DependencyArtifactsVisitor artifactsVisitor, ImmutableArtifactTypeRegistry immutableArtifactTypeRegistry) {
        return new ResolvedArtifactsGraphVisitor(
            artifactsVisitor,
            immutableArtifactTypeRegistry,
            variantArtifactSetCache,
            calculatedValueContainerFactory
        );
    }

    private VisitedArtifactSet getVisitedArtifactSet(
        ResolutionParameters params,
        ComponentResolvers resolvers,
        VisitedGraphResults graphResults,
        VisitedArtifactResults artifactsResults,
        TransformUpstreamDependenciesResolver.Factory dependenciesResolverFactory
    ) {
        ImmutableAttributesSchema consumerSchema = params.getRootComponent().getMetadata().getAttributesSchema();
        return new DefaultVisitedArtifactSet(
            graphResults,
            params.getResolutionHost(),
            artifactsResults,
            artifactSetResolver,
            transformedVariantFactory,
            dependenciesResolverFactory,
            consumerSchema,
            consumerProvidedVariantFinder,
            attributesFactory,
            attributeSchemaServices,
            resolutionFailureHandler,
            resolvers.getArtifactResolver(),
            params.getArtifactTypeRegistry(),
            resolvedVariantCache,
            graphVariantSelector,
            transformRegistry
        );
    }

    /**
     * Perform dependency resolution and visit the results.
     */
    private void doResolve(
        ResolutionParameters params,
        LegacyResolutionParameters legacyParams,
        ImmutableList<ResolutionParameters.ModuleVersionLock> moduleVersionLocks,
        ComponentResolvers resolvers,
        Spec<DependencyMetadata> edgeFilter,
        ImmutableList<DependencyGraphVisitor> visitors
    ) {
        ImmutableList.Builder<DependencyMetadata> syntheticDependencies = ImmutableList.builderWithExpectedSize(moduleVersionLocks.size());
        for (ResolutionParameters.ModuleVersionLock lock : moduleVersionLocks) {
            syntheticDependencies.add(asDependencyConstraintMetadata(lock));
        }

        dependencyGraphResolver.resolve(
            params.getRootComponent(),
            params.getRootVariant(),
            syntheticDependencies.build(),
            edgeFilter,
            componentSelectorConverter,
            resolvers.getComponentIdResolver(),
            resolvers.getComponentResolver(),
            params.getModuleReplacements(),
            legacyParams.getDependencySubstitutionRules(),
            params.getModuleConflictResolutionStrategy(),
            legacyParams.getCapabilityConflictResolutionRules(),
            params.isFailingOnDynamicVersions(),
            params.isFailingOnChangingVersions(),
            new CompositeDependencyGraphVisitor(visitors)
        );
    }

    /**
     * Get component resolvers that resolve local and external components.
     */
    private ComponentResolvers getResolvers(
        ResolutionParameters params,
        LegacyResolutionParameters legacyParams,
        List<ResolutionAwareRepository> repositories
    ) {
        List<ComponentResolvers> resolvers = new ArrayList<>(3);
        for (ResolverProviderFactory factory : resolverFactories) {
            factory.create(resolvers, localComponentRegistry);
        }
        resolvers.add(projectDependencyResolver);

        resolvers.add(externalResolverFactory.createResolvers(
            repositories,
            componentMetadataProcessorFactory,
            legacyParams.getComponentSelectionRules(),
            params.isDependencyVerificationEnabled(),
            params.getCacheExpirationControl(),
            // We should not need to know _what_ we're resolving in order to construct a resolver for a set of repositories.
            // The request attributes and schema are used to support filtering components by attributes when using dynamic versions.
            // We should consider just removing that feature and making dynamic version selection dumber.
            params.getRootVariant().getAttributes(),
            params.getRootComponent().getMetadata().getAttributesSchema()
        ));

        return new ComponentResolversChain(resolvers);
    }

    private ImmutableList<ResolutionParameters.ModuleVersionLock> getAllVersionLocks(ResolutionParameters params) {
        if (!params.isDependencyLockingEnabled()) {
            return params.getModuleVersionLocks();
        }

        if (params.isFailingOnDynamicVersions()) {
            throw new InvalidUserCodeException(
                "Both dependency locking and fail on dynamic versions are enabled. You must choose between the two modes."
            );
        } else if (params.isFailingOnChangingVersions()) {
            throw new InvalidUserCodeException(
                "Both dependency locking and fail on changing versions are enabled. You must choose between the two modes."
            );
        }

        return ImmutableList.<ResolutionParameters.ModuleVersionLock>builder()
            .addAll(getLockfileLocks(params))
            .addAll(params.getModuleVersionLocks())
            .build();
    }

    private ImmutableList<ResolutionParameters.ModuleVersionLock> getLockfileLocks(ResolutionParameters params) {
        DependencyLockingState dependencyLockingState = dependencyLockingProvider.loadLockState(
            params.getDependencyLockingId(),
            params.getResolutionHost().displayName()
        );

        boolean strict = dependencyLockingState.mustValidateLockState();

        Set<ModuleComponentIdentifier> lockedDependencies = dependencyLockingState.getLockedDependencies();
        ImmutableList.Builder<ResolutionParameters.ModuleVersionLock> locks = ImmutableList.builderWithExpectedSize(lockedDependencies.size());
        for (ModuleComponentIdentifier lockedDependency : lockedDependencies) {
            locks.add(new ResolutionParameters.ModuleVersionLock(
                lockedDependency.getModuleIdentifier(),
                lockedDependency.getVersion(),
                "Dependency version enforced by Dependency Locking",
                strict
            ));
        }
        return locks.build();
    }

    private static LocalComponentDependencyMetadata asDependencyConstraintMetadata(ResolutionParameters.ModuleVersionLock lock) {
        VersionConstraint versionConstraint = lock.isStrict()
            ? DefaultImmutableVersionConstraint.strictly(lock.getVersion())
            : DefaultImmutableVersionConstraint.of(lock.getVersion());

        ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(
            lock.getModuleId(),
            versionConstraint
        );

        return new LocalComponentDependencyMetadata(
            selector, null, Collections.emptyList(), Collections.emptyList(),
            false, false, false, true, false, true, lock.getReason()
        );
    }

}
