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
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.*;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;

// TODO:DAZ Add in-memory caching for resolveModuleArtifacts()
class InMemoryCachedModuleComponentRepository extends BaseModuleComponentRepository {
    final InMemoryArtifactsCache artifactsCache;
    final InMemoryCacheStats stats;
    private final ModuleComponentRepositoryAccess localAccess;
    private final ModuleComponentRepositoryAccess remoteAccess;

    public InMemoryCachedModuleComponentRepository(InMemoryModuleComponentRepositoryCaches cache, ModuleComponentRepository delegate) {
        super(delegate);
        this.stats = cache.stats;
        this.artifactsCache = cache.artifactsCache;
        this.localAccess = new CachedAccess(delegate.getLocalAccess(), cache.localAccessCache);
        this.remoteAccess = new CachedAccess(delegate.getRemoteAccess(), cache.remoteAccessCache);
    }

    @Override
    public ModuleComponentRepositoryAccess getLocalAccess() {
        return localAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteAccess;
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        if (!artifactsCache.supplyArtifact(artifact.getId(), result)) {
            super.resolveArtifact(artifact, moduleSource, result);
            artifactsCache.newArtifact(artifact.getId(), result);
        }
    }

    private static class CachedAccess extends BaseModuleComponentRepositoryAccess {
        private final InMemoryMetaDataCache cache;

        public CachedAccess(ModuleComponentRepositoryAccess access, InMemoryMetaDataCache cache) {
            super(access);
            this.cache = cache;
        }

        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
            if(!cache.supplyModuleVersions(dependency.getRequested(), result)) {
                super.listModuleVersions(dependency, result);
                cache.newModuleVersions(dependency.getRequested(), result);
            }
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            if(!cache.supplyMetaData(moduleComponentIdentifier, result)) {
                super.resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
                cache.newDependencyResult(moduleComponentIdentifier, result);
            }
        }
    }
}
