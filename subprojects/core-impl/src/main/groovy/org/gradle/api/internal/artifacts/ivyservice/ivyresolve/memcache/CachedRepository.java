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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LocalAwareModuleVersionRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

/**
* By Szczepan Faber on 4/19/13
*/
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

    public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        if(!cache.supplyLocalMetaData(dependency.getRequested(), result)) {
            delegate.getLocalDependency(dependency, result);
            cache.newLocalDependencyResult(dependency.getRequested(), result);
        }
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        if(!cache.supplyMetaData(dependency.getRequested(), result)) {
            delegate.getDependency(dependency, result);
            cache.newDependencyResult(dependency.getRequested(), result);
        }
    }

    public void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        ArtifactIdentifier id = new DefaultArtifactIdentifier(artifact);
        if(!cache.supplyArtifact(id, result)) {
            delegate.resolve(artifact, result, moduleSource);
            cache.newArtifact(id, result);
        }
    }
}
