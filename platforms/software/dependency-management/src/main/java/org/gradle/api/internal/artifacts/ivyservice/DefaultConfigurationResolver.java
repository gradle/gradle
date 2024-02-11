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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.DefaultResolverResults;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.RepositoriesSupplier;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolveExceptionContextualizer;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolversFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.DependencyGraphResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultVisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariantCache;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
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
import org.gradle.api.internal.artifacts.repositories.ContentFilteringRepository;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.artifacts.transform.ArtifactVariantSelector;
import org.gradle.api.internal.artifacts.transform.VariantSelectorFactory;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.locking.DependencyLockingGraphVisitor;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultConfigurationResolver implements ConfigurationResolver {
    private static final Spec<DependencyMetadata> IS_LOCAL_EDGE = element -> element.getSelector() instanceof ProjectComponentSelector;
    private final ComponentResolversFactory componentResolversFactory;
    private final DependencyGraphResolver dependencyGraphResolver;
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
    private final ResolvedArtifactSetResolver artifactSetResolver;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;
    private final ResolveExceptionContextualizer exceptionContextualizer;
    private final ComponentDetailsSerializer componentDetailsSerializer;
    private final SelectedVariantSerializer selectedVariantSerializer;
    private final ResolvedVariantCache resolvedVariantCache;
    private final GraphVariantSelector graphVariantSelector;
    private final ProjectStateRegistry projectStateRegistry;

    public DefaultConfigurationResolver(
        ComponentResolversFactory componentResolversFactory,
        DependencyGraphResolver dependencyGraphResolver,
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
        ResolvedArtifactSetResolver artifactSetResolver,
        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory,
        ResolveExceptionContextualizer exceptionContextualizer,
        ComponentDetailsSerializer componentDetailsSerializer,
        SelectedVariantSerializer selectedVariantSerializer,
        ResolvedVariantCache resolvedVariantCache,
        GraphVariantSelector graphVariantSelector,
        ProjectStateRegistry projectStateRegistry
    ) {
        this.componentResolversFactory = componentResolversFactory;
        this.dependencyGraphResolver = dependencyGraphResolver;
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
        this.artifactSetResolver = artifactSetResolver;
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
        this.exceptionContextualizer = exceptionContextualizer;
        this.componentDetailsSerializer = componentDetailsSerializer;
        this.selectedVariantSerializer = selectedVariantSerializer;
        this.resolvedVariantCache = resolvedVariantCache;
        this.graphVariantSelector = graphVariantSelector;
        this.projectStateRegistry = projectStateRegistry;
    }

    @Override
    public ResolverResults resolveBuildDependencies(ResolveContext resolveContext) {
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
        InMemoryResolutionResultBuilder resolutionResultBuilder = new InMemoryResolutionResultBuilder();
        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild, projectStateRegistry);
        DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(buildProjectDependencies);

        ComponentResolvers resolvers = componentResolversFactory.create(resolveContext, ImmutableList.of(), consumerSchema);
        DependencyGraphVisitor artifactsGraphVisitor = artifactVisitorFor(artifactsBuilder, resolvers);

        ImmutableList<DependencyGraphVisitor> visitors = ImmutableList.of(failureCollector, resolutionResultBuilder, localComponentsVisitor, artifactsGraphVisitor);
        doResolve(resolveContext, resolvers, false, IS_LOCAL_EDGE, visitors);
        localComponentsVisitor.complete(ConfigurationInternal.InternalState.BUILD_DEPENDENCIES_RESOLVED);

        Set<UnresolvedDependency> unresolvedDependencies = failureCollector.complete(Collections.emptySet());
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResultBuilder.getResolutionResult(), unresolvedDependencies, null);

        ResolutionHost resolutionHost = resolveContext.getResolutionHost();
        ArtifactVariantSelector artifactVariantSelector = variantSelectorFactory.create(resolveContext.getDependenciesResolverFactory());
        VisitedArtifactSet visitedArtifacts = new DefaultVisitedArtifactSet(graphResults, resolutionHost, artifactsBuilder.complete(), artifactSetResolver, artifactVariantSelector);

        ResolverResults.LegacyResolverResults legacyResolverResults = DefaultResolverResults.DefaultLegacyResolverResults.buildDependenciesResolved(
            // When resolving build dependencies, we ignore the dependencySpec, potentially capturing a greater
            // set of build dependencies than actually required. This is because it takes a lot of extra information
            // from the visited graph to properly filter artifacts by dependencySpec, and we don't want capture that when
            // calculating build dependencies.
            dependencySpec -> visitedArtifacts.select(getImplicitSelectionSpec(resolveContext))
        );

        return DefaultResolverResults.buildDependenciesResolved(graphResults, visitedArtifacts, legacyResolverResults);
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

        ComponentResolvers resolvers = componentResolversFactory.create(resolveContext, getFilteredRepositories(resolveContext), consumerSchema);
        CompositeDependencyArtifactsVisitor artifactVisitors = new CompositeDependencyArtifactsVisitor(ImmutableList.of(
            oldModelVisitor, fileDependencyVisitor, artifactsBuilder
        ));
        graphVisitors.add(artifactVisitorFor(artifactVisitors, resolvers));

        doResolve(resolveContext, resolvers, true, Specs.satisfyAll(), graphVisitors.build());
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
            for (Throwable failure : versionConflictVisitor.collectConflictFailures()) {
                nonFatalFailuresBuilder.add(failure);
            }
        }

        Set<Throwable> nonFatalFailures = nonFatalFailuresBuilder.build();
        Set<UnresolvedDependency> resolutionFailures = failureCollector.complete(lockingFailures);

        ResolutionHost resolutionHost = resolveContext.getResolutionHost();
        MinimalResolutionResult resolutionResult = newModelBuilder.complete(lockingFailures);
        ResolveException failure = exceptionContextualizer.mapFailures(nonFatalFailures, resolutionHost.getDisplayName(), "dependencies");
        VisitedGraphResults graphResults = new DefaultVisitedGraphResults(resolutionResult, resolutionFailures, failure);

        // Only write dependency locks if resolution completed without failure.
        if (lockingVisitor != null && !graphResults.hasAnyFailure()) {
            lockingVisitor.writeLocks();
        }

        ArtifactVariantSelector artifactVariantSelector = variantSelectorFactory.create(resolveContext.getDependenciesResolverFactory());
        VisitedArtifactSet visitedArtifacts = new DefaultVisitedArtifactSet(graphResults, resolutionHost, artifactsResults, artifactSetResolver, artifactVariantSelector);

        // Legacy results
        TransientConfigurationResultsLoader transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(oldTransientModelBuilder, legacyGraphResults);
        DefaultLenientConfiguration lenientConfiguration = new DefaultLenientConfiguration(
            resolutionHost,
            graphResults,
            artifactsResults,
            fileDependencyResults,
            transientConfigurationResultsFactory,
            artifactSetResolver,
            artifactVariantSelector,
            getImplicitSelectionSpec(resolveContext)
        );
        ResolverResults.LegacyResolverResults legacyResolverResults = DefaultResolverResults.DefaultLegacyResolverResults.graphResolved(
            lenientConfiguration,
            new DefaultResolvedConfiguration(graphResults, resolutionHost, visitedArtifacts, lenientConfiguration)
        );

        return DefaultResolverResults.graphResolved(graphResults, visitedArtifacts, legacyResolverResults);
    }

    private static ArtifactSelectionSpec getImplicitSelectionSpec(ResolveContext resolveContext) {
        ImmutableAttributes requestAttributes = resolveContext.getAttributes().asImmutable();
        ResolutionStrategy.SortOrder sortOrder = resolveContext.getResolutionStrategy().getSortOrder();
        return new ArtifactSelectionSpec(requestAttributes, Specs.satisfyAll(), false, false, sortOrder);
    }

    private ResolvedArtifactsGraphVisitor artifactVisitorFor(DependencyArtifactsVisitor artifactsVisitor, ComponentResolvers resolvers) {
        return new ResolvedArtifactsGraphVisitor(
            artifactsVisitor,
            artifactTypeRegistry,
            calculatedValueContainerFactory,
            resolvers.getArtifactResolver(),
            resolvedVariantCache,
            graphVariantSelector,
            consumerSchema
        );
    }

    @Override
    public List<ResolutionAwareRepository> getAllRepositories() {
        return repositoriesSupplier.get();
    }

    /**
     * Perform dependency resolution and visit the results.
     */
    private void doResolve(
        ResolveContext resolveContext,
        ComponentResolvers resolvers,
        boolean includeSyntheticDependencies,
        Spec<DependencyMetadata> edgeFilter,
        ImmutableList<DependencyGraphVisitor> visitors
    ) {
        ResolutionStrategyInternal resolutionStrategy = resolveContext.getResolutionStrategy();

        if (resolutionStrategy.isDependencyLockingEnabled()) {
            if (resolutionStrategy.isFailingOnDynamicVersions()) {
                throw new InvalidUserCodeException(
                    "Resolution strategy has both dependency locking and fail on dynamic versions enabled. " +
                        "You must choose between the two modes."
                );
            } else if (resolutionStrategy.isFailingOnChangingVersions()) {
                throw new InvalidUserCodeException(
                    "Resolution strategy has both dependency locking and fail on changing versions enabled. " +
                        "You must choose between the two modes."
                );
            }
        }

        // TODO: These dependencies should not be provided separately, but should be part of the root variant.
        List<? extends DependencyMetadata> syntheticDependencies = includeSyntheticDependencies ?
            resolveContext.getSyntheticDependencies() : Collections.emptyList();

        dependencyGraphResolver.resolve(
            resolveContext.toRootComponent(),
            syntheticDependencies,
            edgeFilter,
            consumerSchema,
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

    private List<ResolutionAwareRepository> getFilteredRepositories(ResolveContext resolveContext) {
        return repositoriesSupplier.get().stream()
            .filter(repository -> !shouldSkipRepository(repository, resolveContext.getName(), resolveContext.getAttributes()))
            .collect(Collectors.toList());
    }

    /**
     * Determines if the repository should not be used to resolve this configuration.
     */
    private static boolean shouldSkipRepository(
        ResolutionAwareRepository repository,
        String resolveContextName,
        AttributeContainer consumerAttributes
    ) {
        if (!(repository instanceof ContentFilteringRepository)) {
            return false;
        }

        ContentFilteringRepository cfr = (ContentFilteringRepository) repository;

        Set<String> includedConfigurations = cfr.getIncludedConfigurations();
        Set<String> excludedConfigurations = cfr.getExcludedConfigurations();

        if ((includedConfigurations != null && !includedConfigurations.contains(resolveContextName)) ||
            (excludedConfigurations != null && excludedConfigurations.contains(resolveContextName))
        ) {
            return true;
        }

        Map<Attribute<Object>, Set<Object>> requiredAttributes = cfr.getRequiredAttributes();
        return hasNonRequiredAttribute(requiredAttributes, consumerAttributes);
    }

    /**
     * Accepts a map of attribute types to the set of values that are allowed for that attribute type.
     * If the request attributes of the resolve context being resolved do not match the allowed values,
     * then the repository is skipped.
     */
    private static boolean hasNonRequiredAttribute(
        @Nullable Map<Attribute<Object>, Set<Object>> requiredAttributes,
        AttributeContainer consumerAttributes
    ) {
        if (requiredAttributes == null) {
            return false;
        }

        for (Map.Entry<Attribute<Object>, Set<Object>> entry : requiredAttributes.entrySet()) {
            Attribute<Object> key = entry.getKey();
            Set<Object> allowedValues = entry.getValue();
            Object value = consumerAttributes.getAttribute(key);
            if (!allowedValues.contains(value)) {
                return true;
            }
        }

        return false;
    }
}
