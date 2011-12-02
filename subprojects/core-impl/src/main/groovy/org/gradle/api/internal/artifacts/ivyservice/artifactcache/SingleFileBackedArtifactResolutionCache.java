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
package org.gradle.api.internal.artifacts.ivyservice.artifactcache;

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.TimeProvider;

import java.io.File;
import java.io.Serializable;

public class SingleFileBackedArtifactResolutionCache implements ArtifactResolutionCache {
    private final TimeProvider timeProvider;
    private final ArtifactCacheMetaData cacheMetadata;
    private final CacheLockingManager cacheLockingManager;
    private ArtifactFileStore artifactFileStore;
    private PersistentIndexedCache<RevisionKey, ArtifactResolutionCacheEntry> cache;

    public SingleFileBackedArtifactResolutionCache(ArtifactCacheMetaData cacheMetadata, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.cacheMetadata = cacheMetadata;
    }
    
    private PersistentIndexedCache<RevisionKey, ArtifactResolutionCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<RevisionKey, ArtifactResolutionCacheEntry> initCache() {
        artifactFileStore = new LinkingArtifactFileStore(new File(cacheMetadata.getCacheDir(), "artifacts"));
        File artifactResolutionCacheFile = new File(cacheMetadata.getCacheDir(), "artifacts.bin");
        return cacheLockingManager.createCache(artifactResolutionCacheFile, RevisionKey.class, ArtifactResolutionCacheEntry.class);
    }

    public CachedArtifactResolution getCachedArtifactResolution(ModuleVersionRepository repository, ArtifactRevisionId artifactId) {
         ArtifactResolutionCacheEntry artifactResolutionCacheEntry = getCache().get(createKey(repository, artifactId));
        if (artifactResolutionCacheEntry == null) {
            return null;
        }
        return new DefaultCachedArtifactResolution(artifactId, artifactResolutionCacheEntry, timeProvider);
    }

    public File storeArtifactFile(ModuleVersionRepository repository, ArtifactRevisionId artifactId, File artifactFile) {
        if (artifactFile == null) {
            artifactFileStore.removeArtifactFile(repository, artifactId);
            getCache().put(createKey(repository, artifactId), createEntry(null));
            return null;
        } else {
            File cacheFile = artifactFileStore.storeArtifactFile(repository, artifactId, artifactFile);
            getCache().put(createKey(repository, artifactId), createEntry(cacheFile));
            return cacheFile;
        }
    }

    public void expireCachedArtifactResolution(ModuleVersionRepository repository, ArtifactRevisionId artifact) {
        getCache().remove(createKey(repository, artifact));
        artifactFileStore.removeArtifactFile(repository, artifact);
    }

    private RevisionKey createKey(ModuleVersionRepository repository, ArtifactRevisionId artifactId) {
        String artifactPath = artifactFileStore.getArtifactPath(artifactId);
        return new RevisionKey(repository, artifactPath);
    }

    private ArtifactResolutionCacheEntry createEntry(File artifactFile) {
        return new ArtifactResolutionCacheEntry(artifactFile, timeProvider);
    }

    private static class RevisionKey implements Serializable {
        private final String resolverId;
        private final String artifactId;

        private RevisionKey(ModuleVersionRepository repository, String artifactPath) {
            this.resolverId = repository.getId();
            this.artifactId = artifactPath;
        }

        @Override
        public String toString() {
            return resolverId + ":" + artifactId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof RevisionKey)) {
                return false;
            }
            RevisionKey other = (RevisionKey) o;
            return toString().equals(other.toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }

}
