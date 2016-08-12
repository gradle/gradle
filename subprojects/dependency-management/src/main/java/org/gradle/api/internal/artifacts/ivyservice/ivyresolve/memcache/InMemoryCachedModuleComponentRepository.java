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
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepositoryAccess;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepositoryAccess;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

class InMemoryCachedModuleComponentRepository extends BaseModuleComponentRepository {
    private final ModuleComponentRepositoryAccess localAccess;
    private final ModuleComponentRepositoryAccess remoteAccess;

    public InMemoryCachedModuleComponentRepository(InMemoryModuleComponentRepositoryCaches cache, ModuleComponentRepository delegate) {
        super(delegate);
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

        CachedAccess(ModuleComponentRepositoryAccess access, InMemoryArtifactsCache artifactsCache, InMemoryMetaDataCache metaDataCache) {
            super(access);
            this.artifactsCache = artifactsCache;
            this.metaDataCache = metaDataCache;
        }

        @Override
        public String toString() {
            return "in-memory cache > " + getDelegate().toString();
        }

        @Override
        public void listModuleVersions(DependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            if (!metaDataCache.supplyModuleVersions(dependency.getRequested(), result)) {
                super.listModuleVersions(dependency, result);
                metaDataCache.newModuleVersions(dependency.getRequested(), result);
            }
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
            if (!metaDataCache.supplyMetaData(moduleComponentIdentifier, result)) {
                super.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
                metaDataCache.newDependencyResult(moduleComponentIdentifier, result);
            }
        }

        @Override
        public void resolveArtifacts(ComponentResolveMetadata component, BuildableComponentArtifactsResolveResult result) {
            if (!artifactsCache.supplyArtifacts(component.getComponentId(), result)) {
                super.resolveArtifacts(component, result);
                artifactsCache.newArtifacts(component.getComponentId(), result);
            }
        }

        @Override
        public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            if (!artifactsCache.supplyArtifacts(component.getComponentId(), artifactType, result)) {
                super.resolveArtifactsWithType(component, artifactType, result);
                artifactsCache.newArtifacts(component.getComponentId(), artifactType, result);
            }
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            if (!artifactsCache.supplyArtifact(artifact.getId(), result)) {
                super.resolveArtifact(artifact, moduleSource, result);
                artifactsCache.newArtifact(artifact.getId(), result);
            }
        }
    }
}
