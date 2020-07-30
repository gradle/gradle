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
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ConflictResolution;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesOnlyVisitedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactsResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.FailOnVersionConflictArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.DefaultResolvedConfigurationBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolutionFailureCollector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedConfigurationDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.FileDependencyCollectingGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.artifacts.transform.ArtifactTransforms;
import org.gradle.api.internal.artifacts.type.ArtifactTypeRegistry;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.cache.internal.BinaryStore;
import org.gradle.cache.internal.Store;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.locking.DependencyLockingArtifactVisitor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultConfigurationResolver implements ConfigurationResolver {
    private static final Spec<DependencyMetadata> IS_LOCAL_EDGE = element -> element instanceof DslOriginDependencyMetadata && ((DslOriginDependencyMetadata) element).getSource() instanceof ProjectDependency;
    private final ArtifactDependencyResolver resolver;
    private final RepositoryHandler repositories;
    private final GlobalDependencyResolutionRules metadataHandler;
    private final ResolutionResultsStoreFactory storeFactory;
    private final boolean buildProjectDependencies;
    private final AttributesSchemaInternal attributesSchema;
    private final ArtifactTransforms artifactTransforms;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ArtifactTypeRegistry artifactTypeRegistry;
    private final ComponentSelectorConverter componentSelectorConverter;
    private final AttributeContainerSerializer attributeContainerSerializer;
    private final BuildIdentifier currentBuild;
    private final AttributeDesugaring attributeDesugaring;
    private final DependencyVerificationOverride dependencyVerificationOverride;
    private final ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory;

    public DefaultConfigurationResolver(ArtifactDependencyResolver resolver, RepositoryHandler repositories,
                                        GlobalDependencyResolutionRules metadataHandler,
                                        ResolutionResultsStoreFactory storeFactory,
                                        boolean buildProjectDependencies,
                                        AttributesSchemaInternal attributesSchema,
                                        ArtifactTransforms artifactTransforms,
                                        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                        BuildOperationExecutor buildOperationExecutor,
                                        ArtifactTypeRegistry artifactTypeRegistry,
                                        ComponentSelectorConverter componentSelectorConverter,
                                        AttributeContainerSerializer attributeContainerSerializer,
                                        BuildIdentifier currentBuild, AttributeDesugaring attributeDesugaring,
                                        DependencyVerificationOverride dependencyVerificationOverride,
                                        ComponentSelectionDescriptorFactory componentSelectionDescriptorFactory) {
        this.resolver = resolver;
        this.repositories = repositories;
        this.metadataHandler = metadataHandler;
        this.storeFactory = storeFactory;
        this.buildProjectDependencies = buildProjectDependencies;
        this.attributesSchema = attributesSchema;
        this.artifactTransforms = artifactTransforms;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.artifactTypeRegistry = artifactTypeRegistry;
        this.componentSelectorConverter = componentSelectorConverter;
        this.attributeContainerSerializer = attributeContainerSerializer;
        this.currentBuild = currentBuild;
        this.attributeDesugaring = attributeDesugaring;
        this.dependencyVerificationOverride = dependencyVerificationOverride;
        this.componentSelectionDescriptorFactory = componentSelectionDescriptorFactory;
    }

    @Override
    public void resolveBuildDependencies(ConfigurationInternal configuration, ResolverResults result) {
        ResolutionStrategyInternal resolutionStrategy = configuration.getResolutionStrategy();
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
        InMemoryResolutionResultBuilder resolutionResultBuilder = new InMemoryResolutionResultBuilder();
        CompositeDependencyGraphVisitor graphVisitor = new CompositeDependencyGraphVisitor(failureCollector, resolutionResultBuilder);
        DefaultResolvedArtifactsBuilder artifactsVisitor = new DefaultResolvedArtifactsBuilder(currentBuild, buildProjectDependencies, resolutionStrategy.getSortOrder());
        resolver.resolve(configuration, ImmutableList.of(), metadataHandler, IS_LOCAL_EDGE, graphVisitor, artifactsVisitor, attributesSchema, artifactTypeRegistry);
        result.graphResolved(resolutionResultBuilder.getResolutionResult(), new ResolvedLocalComponentsResultGraphVisitor(currentBuild), new BuildDependenciesOnlyVisitedArtifactSet(failureCollector.complete(Collections.emptySet()), artifactsVisitor.complete(), artifactTransforms, configuration.getDependenciesResolver()));
    }

    @Override
    public void resolveGraph(ConfigurationInternal configuration, ResolverResults results) {
        List<ResolutionAwareRepository> resolutionAwareRepositories = getRepositories();
        StoreSet stores = storeFactory.createStoreSet();

        BinaryStore oldModelStore = stores.nextBinaryStore();
        Store<TransientConfigurationResults> oldModelCache = stores.oldModelCache();
        TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache, moduleIdentifierFactory, buildOperationExecutor);
        DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
        ResolvedConfigurationDependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder);

        BinaryStore newModelStore = stores.nextBinaryStore();
        Store<ResolvedComponentResult> newModelCache = stores.newModelCache();
        StreamingResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache, moduleIdentifierFactory, attributeContainerSerializer, attributeDesugaring, componentSelectionDescriptorFactory);

        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(currentBuild);

        ResolutionStrategyInternal resolutionStrategy = configuration.getResolutionStrategy();
        DefaultResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder(currentBuild, buildProjectDependencies, resolutionStrategy.getSortOrder());
        FileDependencyCollectingGraphVisitor fileDependencyVisitor = new FileDependencyCollectingGraphVisitor();
        ResolutionFailureCollector failureCollector = new ResolutionFailureCollector(componentSelectorConverter);
        DependencyGraphVisitor graphVisitor = new CompositeDependencyGraphVisitor(newModelBuilder, localComponentsVisitor, failureCollector);

        ImmutableList.Builder<DependencyArtifactsVisitor> visitors = new ImmutableList.Builder<>();
        visitors.add(oldModelVisitor);
        visitors.add(fileDependencyVisitor);
        visitors.add(artifactsBuilder);
        if (resolutionStrategy.getConflictResolution() == ConflictResolution.strict) {
            visitors.add(new FailOnVersionConflictArtifactsVisitor(configuration.getModule().getProjectId().getProjectPath(), configuration.getName()));
        }
        DependencyLockingArtifactVisitor lockingVisitor = null;
        if (resolutionStrategy.isDependencyLockingEnabled()) {
            lockingVisitor = new DependencyLockingArtifactVisitor(configuration.getName(), resolutionStrategy.getDependencyLockingProvider());
            visitors.add(lockingVisitor);
        }
        ImmutableList<DependencyArtifactsVisitor> allVisitors = visitors.build();
        CompositeDependencyArtifactsVisitor artifactsVisitor = new CompositeDependencyArtifactsVisitor(allVisitors);

        resolver.resolve(configuration, resolutionAwareRepositories, metadataHandler, Specs.satisfyAll(), graphVisitor, artifactsVisitor, attributesSchema, artifactTypeRegistry);

        VisitedArtifactsResults artifactsResults = artifactsBuilder.complete();
        VisitedFileDependencyResults fileDependencyResults = fileDependencyVisitor.complete();
        ResolvedGraphResults graphResults = oldModelBuilder.complete();

        // Append to failures for locking and fail on version conflict
        Set<UnresolvedDependency> extraFailures = lockingVisitor == null
            ? Collections.emptySet()
            : lockingVisitor.collectLockingFailures();
        Set<UnresolvedDependency> failures = failureCollector.complete(extraFailures);
        results.graphResolved(newModelBuilder.complete(extraFailures), localComponentsVisitor, new BuildDependenciesOnlyVisitedArtifactSet(failures, artifactsResults, artifactTransforms, configuration.getDependenciesResolver()));

        results.retainState(new ArtifactResolveState(graphResults, artifactsResults, fileDependencyResults, failures, oldTransientModelBuilder));
        if (!results.hasError() && failures.isEmpty()) {
            artifactsVisitor.complete();
        }
    }

    @Override
    public List<ResolutionAwareRepository> getRepositories() {
        return CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
    }

    @Override
    public void resolveArtifacts(ConfigurationInternal configuration, ResolverResults results) {
        ArtifactResolveState resolveState = (ArtifactResolveState) results.getArtifactResolveState();
        ResolvedGraphResults graphResults = resolveState.graphResults;
        VisitedArtifactsResults artifactResults = resolveState.artifactsResults;
        TransientConfigurationResultsBuilder transientConfigurationResultsBuilder = resolveState.transientConfigurationResultsBuilder;

        TransientConfigurationResultsLoader transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(transientConfigurationResultsBuilder, graphResults);

        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, resolveState.failures, artifactResults, resolveState.fileDependencyResults, transientConfigurationResultsFactory, artifactTransforms, buildOperationExecutor, dependencyVerificationOverride);
        results.artifactsResolved(new DefaultResolvedConfiguration(result), result);
    }

    private static class ArtifactResolveState {
        final ResolvedGraphResults graphResults;
        final VisitedArtifactsResults artifactsResults;
        final VisitedFileDependencyResults fileDependencyResults;
        final Set<UnresolvedDependency> failures;
        final TransientConfigurationResultsBuilder transientConfigurationResultsBuilder;

        ArtifactResolveState(ResolvedGraphResults graphResults, VisitedArtifactsResults artifactsResults, VisitedFileDependencyResults fileDependencyResults, Set<UnresolvedDependency> failures, TransientConfigurationResultsBuilder transientConfigurationResultsBuilder) {
            this.graphResults = graphResults;
            this.artifactsResults = artifactsResults;
            this.fileDependencyResults = fileDependencyResults;
            this.failures = failures;
            this.transientConfigurationResultsBuilder = transientConfigurationResultsBuilder;
        }
    }

}
