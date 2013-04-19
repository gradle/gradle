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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LocalAwareModuleVersionRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleSource;

import java.io.File;

/**
* By Szczepan Faber on 4/19/13
*/
class CachedRepository implements LocalAwareModuleVersionRepository {
    private DependencyMetadataCache cache;
    private LocalAwareModuleVersionRepository delegate;
    private DependencyMetadataCacheStats stats;

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

    public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
        CachedModuleVersionResult fromCache = cache.localDescriptors.get(dependency.getRequested());
        if (fromCache == null) {
            delegate.getLocalDependency(dependency, result);
            CachedModuleVersionResult cachedResult = new CachedModuleVersionResult(result);
            if (cachedResult.isCacheable()) {
                cache.localDescriptors.put(dependency.getRequested(), cachedResult);
            }
        } else {
            stats.localMetadataCached++;
            fromCache.supply(result);
        }
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaData result) {
        CachedModuleVersionResult fromCache = cache.descriptors.get(dependency.getRequested());
        if (fromCache == null) {
            delegate.getDependency(dependency, result);
            CachedModuleVersionResult cachedResult = new CachedModuleVersionResult(result);
            if (cachedResult.isCacheable()) {
                cache.descriptors.put(dependency.getRequested(), cachedResult);
            }
        } else {
            stats.metadataCached++;
            fromCache.supply(result);
        }
    }

    public void resolve(Artifact artifact, BuildableArtifactResolveResult result, ModuleSource moduleSource) {
        ArtifactIdentifier id = new DefaultArtifactIdentifier(artifact);
        File fromCache = cache.artifacts.get(id);
        if (fromCache == null) {
            delegate.resolve(artifact, result, moduleSource);
            if (result.getFailure() == null) {
                cache.artifacts.put(id, result.getFile());
            }
        } else {
            stats.artifactsCached++;
            result.resolved(fromCache);
        }
    }
}
