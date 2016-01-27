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

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.*;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DefaultResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.DependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactResults;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactsBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyArtifactsVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.CompositeDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.*;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.DefaultResolvedLocalComponentsResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult.ResolvedLocalComponentsResultGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultDependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.StreamingResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.ResolutionResultsStoreFactory;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.store.StoreSet;
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository;
import org.gradle.api.internal.cache.BinaryStore;
import org.gradle.api.internal.cache.Store;
import org.gradle.internal.Factory;
import org.gradle.internal.Transformers;
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultConfigurationResolver implements ConfigurationResolver {
    private final ArtifactDependencyResolver resolver;
    private final RepositoryHandler repositories;
    private final GlobalDependencyResolutionRules metadataHandler;
    private final CacheLockingManager cacheLockingManager;
    private final ResolutionResultsStoreFactory storeFactory;
    private final boolean buildProjectDependencies;

    public DefaultConfigurationResolver(ArtifactDependencyResolver resolver, RepositoryHandler repositories,
                                        GlobalDependencyResolutionRules metadataHandler, CacheLockingManager cacheLockingManager,
                                        ResolutionResultsStoreFactory storeFactory, boolean buildProjectDependencies) {
        this.resolver = resolver;
        this.repositories = repositories;
        this.metadataHandler = metadataHandler;
        this.cacheLockingManager = cacheLockingManager;
        this.storeFactory = storeFactory;
        this.buildProjectDependencies = buildProjectDependencies;
    }

    public void resolve(ConfigurationInternal configuration, ResolverResults results) throws ResolveException {
        List<ResolutionAwareRepository> resolutionAwareRepositories = CollectionUtils.collect(repositories, Transformers.cast(ResolutionAwareRepository.class));
        StoreSet stores = storeFactory.createStoreSet();

        BinaryStore oldModelStore = stores.nextBinaryStore();
        Store<TransientConfigurationResults> oldModelCache = stores.oldModelCache();
        TransientConfigurationResultsBuilder oldTransientModelBuilder = new TransientConfigurationResultsBuilder(oldModelStore, oldModelCache);
        DefaultResolvedConfigurationBuilder oldModelBuilder = new DefaultResolvedConfigurationBuilder(oldTransientModelBuilder);
        ResolvedConfigurationDependencyGraphVisitor oldModelVisitor = new ResolvedConfigurationDependencyGraphVisitor(oldModelBuilder);

        BinaryStore newModelStore = stores.nextBinaryStore();
        Store<ResolvedComponentResult> newModelCache = stores.newModelCache();
        ResolutionResultBuilder newModelBuilder = new StreamingResolutionResultBuilder(newModelStore, newModelCache);
        DependencyGraphVisitor newModelVisitor = new ResolutionResultDependencyGraphVisitor(newModelBuilder);

        ResolvedLocalComponentsResultBuilder localComponentsResultBuilder = new DefaultResolvedLocalComponentsResultBuilder(buildProjectDependencies);
        DependencyGraphVisitor projectModelVisitor = new ResolvedLocalComponentsResultGraphVisitor(localComponentsResultBuilder);

        ResolvedArtifactsBuilder artifactsBuilder = new DefaultResolvedArtifactsBuilder();

        DependencyGraphVisitor graphVisitor = new CompositeDependencyGraphVisitor(oldModelVisitor, newModelVisitor, projectModelVisitor);
        DependencyArtifactsVisitor artifactsVisitor = new CompositeDependencyArtifactsVisitor(oldModelVisitor, artifactsBuilder);

        resolver.resolve(configuration, resolutionAwareRepositories, metadataHandler, graphVisitor, artifactsVisitor);

        DefaultResolverResults defaultResolverResults = (DefaultResolverResults) results;
        defaultResolverResults.resolved(newModelBuilder.complete(), localComponentsResultBuilder.complete());

        ResolvedGraphResults graphResults = oldModelBuilder.complete();
        defaultResolverResults.retainState(graphResults, artifactsBuilder, oldTransientModelBuilder);
    }

    public void resolveArtifacts(ConfigurationInternal configuration, ResolverResults results) throws ResolveException {
        DefaultResolverResults defaultResolverResults = (DefaultResolverResults) results;
        ResolvedGraphResults graphResults = defaultResolverResults.getGraphResults();
        ResolvedArtifactResults artifactResults = defaultResolverResults.getResolvedArtifacts();
        TransientConfigurationResultsBuilder transientConfigurationResultsBuilder = defaultResolverResults.getTransientConfigurationResultsBuilder();

        Factory<TransientConfigurationResults> transientConfigurationResultsFactory =
                new TransientConfigurationResultsLoader(transientConfigurationResultsBuilder, graphResults, artifactResults);

        DefaultLenientConfiguration result = new DefaultLenientConfiguration(
            configuration, cacheLockingManager, graphResults.getUnresolvedDependencies(), artifactResults, transientConfigurationResultsFactory);
        results.withResolvedConfiguration(new DefaultResolvedConfiguration(result));
    }
}
