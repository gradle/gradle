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
// TODO:DAZ Change this so that it's a decoration over the file-system caching, rather than something completely separate.
class CachedRepository extends BaseModuleComponentRepository {
    final DependencyMetadataCache cache;
    final DependencyMetadataCacheStats stats;
    private final ModuleComponentRepositoryAccess localAccess;
    private final ModuleComponentRepositoryAccess remoteAccess;

    public CachedRepository(DependencyMetadataCache cache, ModuleComponentRepository delegate, DependencyMetadataCacheStats stats) {
        super(delegate);
        this.cache = cache;
        this.stats = stats;
        this.localAccess = new LocalAccess(delegate.getLocalAccess(), cache);
        this.remoteAccess = new RemoteAccess(delegate.getRemoteAccess(), cache);
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
        if (!cache.supplyArtifact(artifact.getId(), result)) {
            super.resolveArtifact(artifact, moduleSource, result);
            cache.newArtifact(artifact.getId(), result);
        }
    }

    // TODO:DAZ Merge the local and remote implementations, by using 2 simpler cache instances
    private static class LocalAccess extends BaseModuleComponentRepositoryAccess {
        private final DependencyMetadataCache cache;

        public LocalAccess(ModuleComponentRepositoryAccess access, DependencyMetadataCache cache) {
            super(access);
            this.cache = cache;
        }

        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
            if(!cache.supplyLocalModuleVersions(dependency.getRequested(), result)) {
                super.listModuleVersions(dependency, result);
                cache.newLocalModuleVersions(dependency.getRequested(), result);
            }
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            if(!cache.supplyLocalMetaData(moduleComponentIdentifier, result)) {
                super.resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
                cache.newLocalDependencyResult(moduleComponentIdentifier, result);
            }
        }
    }

    private static class RemoteAccess extends BaseModuleComponentRepositoryAccess {
        private final DependencyMetadataCache cache;

        public RemoteAccess(ModuleComponentRepositoryAccess access, DependencyMetadataCache cache) {
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
