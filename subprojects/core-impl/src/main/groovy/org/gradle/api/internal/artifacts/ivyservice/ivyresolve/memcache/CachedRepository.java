/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionSelectionResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LocalAwareModuleVersionRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;

// TODO:DAZ Investigate whether we need in-memory caching for localListModuleVersions(), listModuleVersions(), resolveModuleArtifacts()
class CachedRepository implements LocalAwareModuleVersionRepository {
    final DependencyMetadataCache cache;
    final LocalAwareModuleVersionRepository delegate;
    final DependencyMetadataCacheStats stats;

    public CachedRepository(DependencyMetadataCache cache, LocalAwareModuleVersionRepository delegate, DependencyMetadataCacheStats stats) {
        this.cache = cache;
        this.delegate = delegate;
        this.stats = stats;
    }

    public String getId() {
        return delegate.getId();
    }

    public String getName() {
        return delegate.getName();
    }

    public void localListModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        delegate.localListModuleVersions(dependency, result);
    }

    public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        delegate.listModuleVersions(dependency, result);
    }

    public void getLocalDependency(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
        if(!cache.supplyLocalMetaData(moduleComponentIdentifier, result)) {
            delegate.getLocalDependency(dependency, moduleComponentIdentifier, result);
            cache.newLocalDependencyResult(moduleComponentIdentifier, result);
        }
    }

    public void getDependency(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
        if(!cache.supplyMetaData(moduleComponentIdentifier, result)) {
            delegate.getDependency(dependency, moduleComponentIdentifier, result);
            cache.newDependencyResult(moduleComponentIdentifier, result);
        }
    }

    public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        delegate.resolveModuleArtifacts(component, context, result);
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        ComponentArtifactIdentifier artifactId = artifact.getId();
        if (!cache.supplyArtifact(artifactId, result)) {
            delegate.resolveArtifact(artifact, moduleSource, result);
            if (result.getFailure() == null) {
                cache.newArtifact(artifactId, result);
            }
        }
    }

}
