/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.dynamicversions;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.TimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;

public class SingleFileBackedModuleResolutionCache implements ModuleResolutionCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleFileBackedModuleResolutionCache.class);

    private final TimeProvider timeProvider;
    private final ArtifactCacheMetaData cacheMetadata;
    private final CacheLockingManager cacheLockingManager;
    private PersistentIndexedCache<RevisionKey, ModuleResolutionCacheEntry> cache;

    public SingleFileBackedModuleResolutionCache(ArtifactCacheMetaData cacheMetadata, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.cacheMetadata = cacheMetadata;
    }

    private PersistentIndexedCache<RevisionKey, ModuleResolutionCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<RevisionKey, ModuleResolutionCacheEntry> initCache() {
        File dynamicRevisionsFile = new File(cacheMetadata.getCacheDir(), "dynamic-revisions.bin");
        return cacheLockingManager.createCache(dynamicRevisionsFile, RevisionKey.class, ModuleResolutionCacheEntry.class);
    }

    public void cacheModuleResolution(ModuleVersionRepository repository, ModuleRevisionId requestedVersion, ModuleVersionIdentifier moduleVersionIdentifier) {
        if (requestedVersion.equals(moduleVersionIdentifier)) {
            return;
        }

        LOGGER.debug("Caching resolved revision in dynamic revision cache: Will use '{}' for '{}'", moduleVersionIdentifier, requestedVersion);
        getCache().put(createKey(repository, requestedVersion), createEntry(moduleVersionIdentifier));
    }

    public CachedModuleResolution getCachedModuleResolution(ModuleVersionRepository repository, ModuleRevisionId moduleId) {
        ModuleResolutionCacheEntry moduleResolutionCacheEntry = getCache().get(createKey(repository, moduleId));
        if (moduleResolutionCacheEntry == null) {
            return null;
        }
        return new DefaultCachedModuleResolution(moduleId, moduleResolutionCacheEntry, timeProvider);
    }

    private RevisionKey createKey(ModuleVersionRepository repository, ModuleRevisionId revisionId) {
        return new RevisionKey(repository, revisionId);
    }

    private ModuleResolutionCacheEntry createEntry(ModuleVersionIdentifier moduleVersionIdentifier) {
        return new ModuleResolutionCacheEntry(moduleVersionIdentifier, timeProvider);
    }

    private static class RevisionKey implements Serializable {
        private final String resolverId;
        private final String revisionId;

        private RevisionKey(ModuleVersionRepository repository, ModuleRevisionId revision) {
            this.resolverId = repository.getId();
            this.revisionId = revision.encodeToString();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof RevisionKey)) {
                return false;
            }
            RevisionKey other = (RevisionKey) o;
            return resolverId.equals(other.resolverId) && revisionId.equals(other.revisionId);
        }

        @Override
        public int hashCode() {
            return resolverId.hashCode() ^ revisionId.hashCode();
        }
    }

}
