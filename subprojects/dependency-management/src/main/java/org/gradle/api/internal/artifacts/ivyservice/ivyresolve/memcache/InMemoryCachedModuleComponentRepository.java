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
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentVersionSelectionResolveResult;

// TODO:DAZ Should share local and remote caches, and use make in-memory caching a decorator over filesystem caching
class InMemoryCachedModuleComponentRepository extends BaseModuleComponentRepository {
    final InMemoryCacheStats stats;
    private final ModuleComponentRepositoryAccess localAccess;
    private final ModuleComponentRepositoryAccess remoteAccess;

    public InMemoryCachedModuleComponentRepository(InMemoryModuleComponentRepositoryCaches cache, ModuleComponentRepository delegate) {
        super(delegate);
        this.stats = cache.stats;
        this.localAccess = new CachedAccess(delegate.getLocalAccess(), cache.localArtifactsCache, cache.localMetaDataCache);
        this.remoteAccess = new CachedAccess(delegate.getRemoteAccess(), cache.remoteArtifactsCache, cache.remoteMetaDataCache);
    }

    @Override
    public ModuleComponentRepositoryAccess getLocalAccess() {
        return localAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteAccess;
    }

    private class CachedAccess extends BaseModuleComponentRepositoryAccess {
        private final InMemoryMetaDataCache metaDataCache;
        private final InMemoryArtifactsCache artifactsCache;

        public CachedAccess(ModuleComponentRepositoryAccess access, InMemoryArtifactsCache artifactsCache, InMemoryMetaDataCache metaDataCache) {
            super(access);
            this.artifactsCache = artifactsCache;
            this.metaDataCache = metaDataCache;
        }

        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleComponentVersionSelectionResolveResult result) {
            if(!metaDataCache.supplyModuleVersions(dependency.getRequested(), result)) {
                super.listModuleVersions(dependency, result);
                metaDataCache.newModuleVersions(dependency.getRequested(), result);
            }
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleComponentMetaDataResolveResult result) {
            if(!metaDataCache.supplyMetaData(moduleComponentIdentifier, result)) {
                super.resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
                metaDataCache.newDependencyResult(moduleComponentIdentifier, result);
            }
        }

        public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            if (!artifactsCache.supplyArtifact(artifact.getId(), result)) {
                super.resolveArtifact(artifact, moduleSource, result);
                artifactsCache.newArtifact(artifact.getId(), result);
            }
        }
    }
}
