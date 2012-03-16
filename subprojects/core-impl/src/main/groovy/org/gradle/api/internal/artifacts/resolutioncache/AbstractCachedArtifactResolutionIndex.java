/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.resolutioncache;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.util.TimeProvider;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

public abstract class AbstractCachedArtifactResolutionIndex<K, P extends Serializable> implements CachedArtifactResolutionIndex<K> {

    private final File persistentCacheFile;
    private final Class<P> keyType;
    private final TimeProvider timeProvider;
    private final CacheLockingManager cacheLockingManager;

    private PersistentIndexedCache<P, CachedArtifactResolutionIndexEntry> persistentCache;
    
    public AbstractCachedArtifactResolutionIndex(File persistentCacheFile, Class<P> keyType, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.persistentCacheFile = persistentCacheFile;
        this.keyType = keyType;
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
    }

    private PersistentIndexedCache<P, CachedArtifactResolutionIndexEntry> getPersistentCache() {
        if (persistentCache == null) {
            persistentCache = initPersistentCache();
        }
        return persistentCache;
    }

    private PersistentIndexedCache<P, CachedArtifactResolutionIndexEntry> initPersistentCache() {
        return cacheLockingManager.createCache(persistentCacheFile, keyType, CachedArtifactResolutionIndexEntry.class);
    }

    private CachedArtifactResolutionIndexEntry createEntry(File artifactFile, Date lastModified, String artifactUrl) {
        return new CachedArtifactResolutionIndexEntry(artifactFile, timeProvider, lastModified == null ? -1 : lastModified.getTime(), artifactUrl);
    }

    protected CachedArtifactResolution toCachedArtifactResolution(CachedArtifactResolutionIndexEntry entry) {
        if (entry == null) {
            return null;
        }
        Date lastModified = entry.artifactLastModifiedTimestamp < 0 ? null : new Date(entry.artifactLastModifiedTimestamp);
        return new DefaultCachedArtifactResolution(entry, timeProvider, lastModified, entry.artifactUrl);
    }

    protected abstract P createKey(K publicKey);
    
    public void store(K key, File artifactFile, Date lastModified, String sourceUrl) {
        getPersistentCache().put(createKey(key), createEntry(artifactFile, lastModified, sourceUrl));
    }

    public CachedArtifactResolution lookup(K index) {
        return toCachedArtifactResolution(getPersistentCache().get(createKey(index)));
    }

    public void clear(K key) {
        getPersistentCache().remove(createKey(key));
    }

}
