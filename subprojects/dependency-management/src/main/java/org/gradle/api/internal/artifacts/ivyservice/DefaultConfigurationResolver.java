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
import org.gradle.api.AttributesSchema;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ConfigurationResolver;
import org.gradle.api.internal.artifacts.GlobalDependencyResolutionRules;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.FileDependencyResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.DefaultResolvedConfigurationBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedConfigurationDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedGraphResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.FileDependencyCollectingGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultConfigurationResolver implements ConfigurationResolver {
    private final ArtifactDependencyResolver resolver;
    private final RepositoryHandler repositories;
    private final GlobalDependencyResolutionRules metadataHandler;
    private final CacheLockingManager cacheLockingManager;
    private final ResolutionResultsStoreFactory storeFactory;
    private final boolean buildProjectDependencies;
    private final AttributesSchema attributesSchema;

    public DefaultConfigurationResolver(ArtifactDependencyResolver resolver, RepositoryHandler repositories,
                                        GlobalDependencyResolutionRules metadataHandler, CacheLockingManager cacheLockingManager,
                                        ResolutionResultsStoreFactory storeFactory, boolean buildProjectDependencies, AttributesSchema attributesSchema) {
        this.resolver = resolver;
        this.repositories = repositories;
        this.metadataHandler = metadataHandler;
        this.cacheLockingManager = cacheLockingManager;
        this.storeFactory = storeFactory;
        this.buildProjectDependencies = buildProjectDependencies;
        this.attributesSchema = attributesSchema;
    }

    @Override
    public void resolveBuildDependencies(ConfigurationInternal configuration, ResolverResults result) {
        FileDependencyCollectingGraphVisitor fileDependenciesVisitor = new FileDependencyCollectingGraphVisitor();
        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(buildProjectDependencies);
        resolver.resolve(configuration, ImmutableList.<ResolutionAwareRepository>of(), metadataHandler, new Spec<DependencyMetadata>() {
            @Override
            public boolean isSatisfiedBy(DependencyMetadata element) {
                return element instanceof DslOriginDependencyMetadata && ((DslOriginDependencyMetadata) element).getSource() instanceof ProjectDependency;
            }
        }, new CompositeDependencyGraphVisitor(localComponentsVisitor, fileDependenciesVisitor), new CompositeDependencyArtifactsVisitor(), attributesSchema);
        result.graphResolved(localComponentsVisitor, fileDependenciesVisitor);
    }

    public void resolveGraph(ConfigurationInternal configuration, ResolverResults results) {
        List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
        StoreSet stores = storeFactory.createStoreSet();

        BinaryStore oldModelStore = stores.nextBinaryStore();
        Store<TransientConfigurationResults> oldModelCache = stores.oldModelCache();
        TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache);
        DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
        ResolvedConfigurationDependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder);

        BinaryStore newModelStore = stores.nextBinaryStore();
        Store<ResolvedComponentResult> newModelCache = stores.newModelCache();
        StreamingResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache);

        ResolvedLocalComponentsResultGraphVisitor localComponentsVisitor = new ResolvedLocalComponentsResultGraphVisitor(buildProjectDependencies);

        ResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder();
        FileDependencyCollectingGraphVisitor fileDependencyVisitor = new FileDependencyCollectingGraphVisitor();

        DependencyGraphVisitor graphVisitor = new CompositeDependencyGraphVisitor(oldModelVisitor, newModelBuilder, localComponentsVisitor, fileDependencyVisitor);
        DependencyArtifactsVisitor artifactsVisitor = new CompositeDependencyArtifactsVisitor(oldModelVisitor, artifactsBuilder);

        resolver.resolve(configuration, resolutionAwareRepositories, metadataHandler, Specs.<DependencyMetadata>satisfyAll(), graphVisitor, artifactsVisitor, attributesSchema);

        results.graphResolved(newModelBuilder.complete(), localComponentsVisitor, fileDependencyVisitor);

        results.retainState(new ArtifactResolveState(oldModelBuilder.complete(), artifactsBuilder, fileDependencyVisitor, oldTransientModelBuilder));
    }

    public void resolveArtifacts(ConfigurationInternal configuration, ResolverResults results) {
        ArtifactResolveState resolveState = (ArtifactResolveState) results.getArtifactResolveState();
        ResolvedGraphResults graphResults = resolveState.graphResults;
        ResolvedArtifactResults artifactResults = resolveState.artifactsBuilder.resolve();
        TransientConfigurationResultsBuilder transientConfigurationResultsBuilder = resolveState.transientConfigurationResultsBuilder;

        Factory<TransientConfigurationResults> transientConfigurationResultsFactory = new TransientConfigurationResultsLoader(transientConfigurationResultsBuilder, graphResults, artifactResults);

        ArtifactTransformer transformer = new ArtifactTransformer(configuration.getFormat(), configuration.getResolutionStrategy());
        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, cacheLockingManager, graphResults.getUnresolvedDependencies(), artifactResults, resolveState.fileDependencyResults, transientConfigurationResultsFactory, transformer);
        results.artifactsResolved(new DefaultResolvedConfiguration(result), result);
    }

    private static class ArtifactResolveState {
        final ResolvedGraphResults graphResults;
        final ResolvedArtifactsBuilder artifactsBuilder;
        final FileDependencyResults fileDependencyResults;
        final TransientConfigurationResultsBuilder transientConfigurationResultsBuilder;

        ArtifactResolveState(ResolvedGraphResults graphResults, ResolvedArtifactsBuilder artifactsBuilder, FileDependencyResults fileDependencyResults, TransientConfigurationResultsBuilder transientConfigurationResultsBuilder) {
            this.graphResults = graphResults;
            this.artifactsBuilder = artifactsBuilder;
            this.fileDependencyResults = fileDependencyResults;
            this.transientConfigurationResultsBuilder = transientConfigurationResultsBuilder;
        }
    }
}
