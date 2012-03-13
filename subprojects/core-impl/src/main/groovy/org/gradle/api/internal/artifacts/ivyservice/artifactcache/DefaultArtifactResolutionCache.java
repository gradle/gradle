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

import org.apache.ivy.core.IvyPatternHelper;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.TimeProvider;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.Date;

public class DefaultArtifactResolutionCache implements ArtifactResolutionCache {
    
    private final TimeProvider timeProvider;
    private final ArtifactCacheMetaData cacheMetadata;
    private final CacheLockingManager cacheLockingManager;
    private PersistentIndexedCache<ArtifactAtRepositoryKey, ArtifactResolutionCacheEntry> byRepositoryCache;

    public DefaultArtifactResolutionCache(ArtifactCacheMetaData cacheMetadata, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.cacheMetadata = cacheMetadata;
    }
    
    private PersistentIndexedCache<ArtifactAtRepositoryKey, ArtifactResolutionCacheEntry> getByRepositoryCache() {
        if (byRepositoryCache == null) {
            byRepositoryCache = initByRepositoryCache();
        }
        return byRepositoryCache;
    }

    private PersistentIndexedCache<ArtifactAtRepositoryKey, ArtifactResolutionCacheEntry> initByRepositoryCache() {
        File artifactResolutionCacheFile = new File(cacheMetadata.getCacheDir(), getByRepositoryFileName());
        return cacheLockingManager.createCache(artifactResolutionCacheFile, ArtifactAtRepositoryKey.class, ArtifactResolutionCacheEntry.class);
    }

    private String getByRepositoryFileName() {
        return "artifacts-by-repository.bin";
    }

    public CachedArtifactResolution getCachedArtifactResolution(ModuleVersionRepository repository, ArtifactRevisionId artifactId) {
         ArtifactResolutionCacheEntry artifactResolutionCacheEntry = getByRepositoryCache().get(createKey(repository, artifactId));
        if (artifactResolutionCacheEntry == null) {
            return null;
        }
        Date lastModified = artifactResolutionCacheEntry.artifactLastModifiedTimestamp < 0 ? null : new Date(artifactResolutionCacheEntry.artifactLastModifiedTimestamp);
        return new DefaultCachedArtifactResolution(artifactId, artifactResolutionCacheEntry, timeProvider, lastModified, artifactResolutionCacheEntry.artifactUrl);
    }

    public File storeArtifactFile(ModuleVersionRepository repository, ArtifactRevisionId artifactId, File artifactFile, Date lastModified, URL artifactUrl) {
        getByRepositoryCache().put(createKey(repository, artifactId), createEntry(artifactFile, lastModified, artifactUrl));
        return artifactFile;
    }

    public void expireCachedArtifactResolution(ModuleVersionRepository repository, ArtifactRevisionId artifact) {
        getByRepositoryCache().remove(createKey(repository, artifact));
    }

    private ArtifactAtRepositoryKey createKey(ModuleVersionRepository repository, ArtifactRevisionId artifactId) {
        String artifactPath = getArtifactKey(artifactId);
        return new ArtifactAtRepositoryKey(repository, artifactPath);
    }

    private String getArtifactKey(ArtifactRevisionId artifactId) {
        String format = "[organisation]/[module](/[branch])/[revision]/[type]/[artifact](-[classifier])(.[ext])";
        Artifact dummyArtifact = new DefaultArtifact(artifactId, null, null, false);
        return IvyPatternHelper.substitute(format, dummyArtifact);
    }

    private ArtifactResolutionCacheEntry createEntry(File artifactFile, Date lastModified, URL artifactUrl) {
        return new ArtifactResolutionCacheEntry(artifactFile, timeProvider, lastModified == null ? -1 : lastModified.getTime(), artifactUrl);
    }

    private static class ArtifactAtRepositoryKey implements Serializable {
        private final String resolverId;
        private final String artifactId;

        private ArtifactAtRepositoryKey(ModuleVersionRepository repository, String artifactPath) {
            this.resolverId = repository.getId();
            this.artifactId = artifactPath;
        }

        @Override
        public String toString() {
            return resolverId + ":" + artifactId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof ArtifactAtRepositoryKey)) {
                return false;
            }
            ArtifactAtRepositoryKey other = (ArtifactAtRepositoryKey) o;
            return toString().equals(other.toString());
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }
    }

}
