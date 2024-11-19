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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.VariantTransformRegistry;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyLockingProvider;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ExternalModuleComponentResolverFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactories;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolverProviderFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.RootComponentMetadataBuilder;
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
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
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
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.resolution.failure.ResolutionFailureHandler;
import org.gradle.internal.locking.DependencyLockingGraphVisitor;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resolve.resolver.ResolvedVariantCache;
import org.gradle.operations.dependencies.configurations.ConfigurationIdentity;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Performs a graph resolution. This interface acts as the entry-point to the dependency resolution process.
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
    private final GlobalDependencyResolutionRules metadataHandler;
    private final ResolutionResultsStoreFactory storeFactory;
    private final boolean buildProjectDependencies;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ArtifactTypeRegistry artifactTypeRegistry;
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

    @Inject
    public ResolutionExecutor(
        DependencyGraphResolver dependencyGraphResolver,
        GlobalDependencyResolutionRules metadataHandler,
        ResolutionResultsStoreFactory storeFactory,
        StartParameter startParameter,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        BuildOperationExecutor buildOperationExecutor,
        ArtifactTypeRegistry artifactTypeRegistry,
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
        VariantTransformRegistry transformRegistry
    ) {
        this.dependencyGraphResolver = dependencyGraphResolver;
        this.metadataHandler = metadataHandler;
        this.storeFactory = storeFactory;
        this.buildProjectDependencies = startParameter.isBuildProjectDependencies();
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
        this.artifactTypeRegistry = artifactTypeRegistry;
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
    }

    /**
     * Traverses enough of the graph to calculate the build dependencies of the graph.
     *
     * @param resolveContext Describes what and how to resolve
     * @param futureCompleteResults The future value of the output of {@link #resolveGraph(ResolveContext, List)}. See
     * {@link DefaultTransformUpstreamDependenciesResolver} for why this is needed.
     *
     * @return An immutable result set, containing a subset of the graph that is sufficient to calculate the build dependencies.
     */
    public ResolverResults resolveBuildDependencies(ResolveContext resolveContext, CalculatedValue<ResolverResults> futureCompleteResults) {

        ResolutionHost resolutionHost = resolveContext.getResolutionHost();
        RootComponentMetadataBuilder.RootComponentState rootComponent = resolveContext.toRootComponent();
        ImmutableAttributes requestAttributes = rootComponent.getRootVariant().getAttributes();
        ResolutionStrategy.SortOrder defaultSortOrder = resolveContext.getResolutionStrategy().getSortOrder();
        ImmutableAttributesSchema consumerSchema = rootComponent.getRootComponent().getMetadata().getAttributesSchema();
        ConfigurationIdentity configurationIdentity = resolveContext.getConfigurationIdentity();

        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        InMemoryResolutionResultBuilder resolutionResultBuilder = new InMemoryResolutionResultBuilder(resolutionStrategy.getIncludeAllSelectableVariantResults());
        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild, projectStateRegistry);
        DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(buildProjectDependencies);

        ComponentResolvers resolvers = getResolvers(resolveContext, Collections.emptyList(), consumerSchema, requestAttributes);
        ImmutableArtifactTypeRegistry immutableArtifactTypeRegistry = attributeSchemaServices.getArtifactTypeRegistryFactory().create(artifactTypeRegistry);
        DependencyGraphVisitor artifactsGraphVisitor = artifactVisitorFor(artifactsBuilder, immutableArtifactTypeRegistry);

        ImmutableList<DependencyGraphVisitor> visitors = ImmutableList.of(failureCollector, resolutionResultBuilder, localComponentsVisitor, artifactsGraphVisitor);
        doResolve(resolveContext, rootComponent, resolutionStrategy, resolvers, false, IS_LOCAL_EDGE, visitors);
        localComponentsVisitor.complete(ConfigurationInternal.InternalState.BUILD_DEPENDENCIES_RESOLVED);

        Set<UnresolvedDependency> unresolvedDependencies = failureCollector.complete(Collections.emptySet());
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResultBuilder.getResolutionResult(), unresolvedDependencies, null);
        VisitedArtifactResults artifactsResults = artifactsBuilder.complete();

        TransformUpstreamDependenciesResolver.Factory dependenciesResolverFactory = visitedArtifacts -> new DefaultTransformUpstreamDependenciesResolver(
            resolutionHost,
            configurationIdentity,
            requestAttributes,
            defaultSortOrder,
            graphResults,
            visitedArtifacts,
            futureCompleteResults,
            domainObjectContext,
            calculatedValueContainerFactory,
            attributesFactory,
            taskDependencyFactory
        );

        VisitedArtifactSet visitedArtifacts = getVisitedArtifactSet(
            graphResults,
            resolutionHost,
            consumerSchema,
            artifactsResults,
            resolvers,
            dependenciesResolverFactory,
            immutableArtifactTypeRegistry
        );

        ResolverResults.LegacyResolverResults legacyResolverResults = DefaultResolverResults.DefaultLegacyResolverResults.buildDependenciesResolved(
            // When resolving build dependencies, we ignore the dependencySpec, potentially capturing a greater
            // set of build dependencies than actually required. This is because it takes a lot of extra information
            // from the visited graph to properly filter artifacts by dependencySpec, and we don't want capture that when
            // calculating build dependencies.
            dependencySpec -> visitedArtifacts.select(getImplicitSelectionSpec(requestAttributes, defaultSortOrder))
        );

        return DefaultResolverResults.buildDependenciesResolved(graphResults, visitedArtifacts, legacyResolverResults);
    }

    /**
     * Traverses the full dependency graph.
     *
     * @param resolveContext Describes what and how to resolve
     * @param repositories The repositories used to resolve external dependencies
     *
     * @return An immutable result set, containing the full graph of resolved components.
     */
    public ResolverResults resolveGraph(ResolveContext resolveContext, List<ResolutionAwareRepository> repositories) {

        ResolutionHost resolutionHost = resolveContext.getResolutionHost();
        RootComponentMetadataBuilder.RootComponentState rootComponent = resolveContext.toRootComponent();
        ImmutableAttributes requestAttributes = rootComponent.getRootVariant().getAttributes();
        ResolutionStrategy.SortOrder defaultSortOrder = resolveContext.getResolutionStrategy().getSortOrder();
        ImmutableAttributesSchema consumerSchema = rootComponent.getRootComponent().getMetadata().getAttributesSchema();
        ConfigurationIdentity configurationIdentity = resolveContext.getConfigurationIdentity();

        StoreSet stores = storeFactory.createStoreSet();

        BinaryStore oldModelStore = stores.nextBinaryStore();
        Store<TransientConfigurationResults> oldModelCache = stores.oldModelCache();
        TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache, moduleIdentifierFactory, buildOperationExecutor, resolutionHost);
        DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
        ResolvedConfigurationDependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder);

        BinaryStore newModelStore = stores.nextBinaryStore();
        Store<ResolvedComponentResultInternal> newModelCache = stores.newModelCache();
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        StreamingResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache, attributeContainerSerializer, capabilitySelectorSerializer, componentResultSerializer, componentSelectionDescriptorFactory, resolutionStrategy.getIncludeAllSelectableVariantResults());

        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild, projectStateRegistry);

        DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(buildProjectDependencies);
        FileDependencyCollectingGraphVisitor fileDependencyVisitor = new FileDependencyCollectingGraphVisitor();
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);

        ImmutableList.Builder<DependencyGraphVisitor> graphVisitors = ImmutableList.builder();
        graphVisitors.add(newModelBuilder);
        graphVisitors.add(localComponentsVisitor);
        graphVisitors.add(failureCollector);

        FailOnVersionConflictGraphVisitor versionConflictVisitor = null;
        if (resolutionStrategy.getConflictResolution() == ConflictResolution.strict) {
            versionConflictVisitor = new FailOnVersionConflictGraphVisitor();
            graphVisitors.add(versionConflictVisitor);
        }

        DependencyLockingGraphVisitor lockingVisitor = null;
        if (resolutionStrategy.isDependencyLockingEnabled()) {
            lockingVisitor = new DependencyLockingGraphVisitor(resolveContext.getDependencyLockingId(), resolutionHost.displayName(), dependencyLockingProvider);
            graphVisitors.add(lockingVisitor);
        } else {
            dependencyLockingProvider.confirmNotLocked(resolveContext.getDependencyLockingId());
        }


        ComponentResolvers resolvers = getResolvers(resolveContext, repositories, consumerSchema, requestAttributes);
        CompositeDependencyArtifactsVisitor artifactVisitors = new CompositeDependencyArtifactsVisitor(ImmutableList.of(
            oldModelVisitor, fileDependencyVisitor, artifactsBuilder
        ));
        ImmutableArtifactTypeRegistry immutableArtifactTypeRegistry = attributeSchemaServices.getArtifactTypeRegistryFactory().create(artifactTypeRegistry);
        graphVisitors.add(artifactVisitorFor(artifactVisitors, immutableArtifactTypeRegistry));

        doResolve(resolveContext, rootComponent, resolutionStrategy, resolvers, true, Specs.satisfyAll(), graphVisitors.build());
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
                List<String> resolutions = resolveContext.getFailureResolutions().forVersionConflict(versionConflicts);
                nonFatalFailuresBuilder.add(new VersionConflictException(versionConflicts, resolutions));
            }
        }

        Set<Throwable> nonFatalFailures = nonFatalFailuresBuilder.build();
        Set<UnresolvedDependency> resolutionFailures = failureCollector.complete(lockingFailures);

        MinimalResolutionResult resolutionResult = newModelBuilder.getResolutionResult(lockingFailures);
        Optional<? extends ResolveException> failure = resolutionHost.consolidateFailures("dependencies", nonFatalFailures);
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResult, resolutionFailures, failure.orElse(null));

        // Only write dependency locks if resolution completed without failure.
        if (lockingVisitor != null && !graphResults.hasAnyFailure()) {
            lockingVisitor.writeLocks();
        }

        TransformUpstreamDependenciesResolver.Factory dependenciesResolverFactory = visitedArtifacts -> new DefaultTransformUpstreamDependenciesResolver(
            resolutionHost,
            configurationIdentity,
            requestAttributes,
            defaultSortOrder,
            graphResults,
            visitedArtifacts,
            domainObjectContext,
            calculatedValueContainerFactory,
            attributesFactory,
            taskDependencyFactory
        );

        VisitedArtifactSet visitedArtifacts = getVisitedArtifactSet(
            graphResults,
            resolutionHost,
            consumerSchema,
            artifactsResults,
            resolvers,
            dependenciesResolverFactory,
            immutableArtifactTypeRegistry
        );

        // Legacy results
        TransientConfigurationResultsLoader transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(oldTransientModelBuilder, legacyGraphResults);
        DefaultLenientConfiguration lenientConfiguration = new DefaultLenientConfiguration(
            resolutionHost,
            graphResults,
            visitedArtifacts,
            fileDependencyResults,
            transientConfigurationResultsFactory,
            artifactSetResolver,
            getImplicitSelectionSpec(requestAttributes, defaultSortOrder)
        );
        ResolverResults.LegacyResolverResults legacyResolverResults = DefaultResolverResults.DefaultLegacyResolverResults.graphResolved(
            lenientConfiguration,
            new DefaultResolvedConfiguration(graphResults, resolutionHost, visitedArtifacts, lenientConfiguration)
        );

        return DefaultResolverResults.graphResolved(graphResults, visitedArtifacts, legacyResolverResults);
    }

    private static ArtifactSelectionSpec getImplicitSelectionSpec(ImmutableAttributes requestAttributes, ResolutionStrategy.SortOrder sortOrder) {
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
        VisitedGraphResults graphResults,
        ResolutionHost resolutionHost,
        ImmutableAttributesSchema consumerSchema,
        VisitedArtifactResults artifactsResults,
        ComponentResolvers resolvers,
        TransformUpstreamDependenciesResolver.Factory dependenciesResolverFactory,
        ImmutableArtifactTypeRegistry immutableArtifactTypeRegistry
    ) {
        return new DefaultVisitedArtifactSet(
            graphResults,
            resolutionHost,
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
            immutableArtifactTypeRegistry,
            resolvedVariantCache,
            graphVariantSelector,
            transformRegistry
        );
    }

    /**
     * Perform dependency resolution and visit the results.
     */
    private void doResolve(
        ResolveContext resolveContext,
        RootComponentMetadataBuilder.RootComponentState rootComponent,
        ResolutionStrategyInternal resolutionStrategy,
        ComponentResolvers resolvers,
        boolean includeSyntheticDependencies,
        Spec<DependencyMetadata> edgeFilter,
        ImmutableList<DependencyGraphVisitor> visitors
    ) {
        if (resolutionStrategy.isDependencyLockingEnabled()) {
            if (resolutionStrategy.isFailingOnDynamicVersions()) {
                throw new InvalidUserCodeException(
                    "Both dependency locking and fail on dynamic versions are enabled. You must choose between the two modes."
                );
            } else if (resolutionStrategy.isFailingOnChangingVersions()) {
                throw new InvalidUserCodeException(
                    "Both dependency locking and fail on changing versions are enabled. You must choose between the two modes."
                );
            }
        }

        // TODO: These dependencies should not be provided separately, but should be part of the root variant.
        List<? extends DependencyMetadata> syntheticDependencies = includeSyntheticDependencies ?
            resolveContext.getSyntheticDependencies() : Collections.emptyList();

        dependencyGraphResolver.resolve(
            rootComponent,
            syntheticDependencies,
            edgeFilter,
            componentSelectorConverter,
            resolvers.getComponentIdResolver(),
            resolvers.getComponentResolver(),
            metadataHandler.getModuleMetadataProcessor().getModuleReplacements(),
            resolutionStrategy.getDependencySubstitutionRule(),
            resolutionStrategy.getConflictResolution(),
            resolutionStrategy.getCapabilitiesResolutionRules(),
            resolutionStrategy.isFailingOnDynamicVersions(),
            resolutionStrategy.isFailingOnChangingVersions(),
            new CompositeDependencyGraphVisitor(visitors)
        );
    }

    /**
     * Get component resolvers that resolve local and external components.
     */
    private ComponentResolvers getResolvers(
        ResolveContext resolveContext,
        List<ResolutionAwareRepository> repositories,
        ImmutableAttributesSchema consumerSchema,
        AttributeContainerInternal requestAttributes
    ) {
        List<ComponentResolvers> resolvers = new ArrayList<>(3);
        for (ResolverProviderFactory factory : resolverFactories) {
            factory.create(resolvers, localComponentRegistry);
        }
        resolvers.add(projectDependencyResolver);

        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();
        resolvers.add(externalResolverFactory.createResolvers(
            repositories,
            metadataHandler.getComponentMetadataProcessorFactory(),
            resolutionStrategy.getComponentSelection(),
            resolutionStrategy.isDependencyVerificationEnabled(),
            resolutionStrategy.getCachePolicy(),
            // We should not need to know _what_ we're resolving in order to construct a resolver for a set of repositories.
            // The request attributes and schema are used to support filtering components by attributes when using dynamic versions.
            // We should consider just removing that feature and making dynamic version selection dumber.
            requestAttributes,
            consumerSchema
        ));

        return new ComponentResolversChain(resolvers);
    }
}
