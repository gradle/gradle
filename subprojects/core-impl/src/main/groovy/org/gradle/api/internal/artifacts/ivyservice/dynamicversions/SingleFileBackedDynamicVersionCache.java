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
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.cache.DefaultSerializer;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.FileLock;
import org.gradle.cache.internal.btree.BTreePersistentIndexedCache;
import org.gradle.util.TimeProvider;
import org.jfrog.wharf.ivy.model.WharfResolverMetadata;

import java.io.File;
import java.io.Serializable;

public class SingleFileBackedDynamicVersionCache implements DynamicVersionCache {
    private final TimeProvider timeProvider;
    private final ArtifactCacheMetaData cacheMetadata;
    private final CacheLockingManager cacheLockingManager;
    private PersistentIndexedCache<RevisionKey, DynamicVersionCacheEntry> cache;

    public SingleFileBackedDynamicVersionCache(ArtifactCacheMetaData cacheMetadata, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.cacheMetadata = cacheMetadata;
    }
    
    private PersistentIndexedCache<RevisionKey, DynamicVersionCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<RevisionKey, DynamicVersionCacheEntry> initCache() {
        File dynamicRevisionsFile = new File(cacheMetadata.getCacheDir(), "dynamic-revisions.bin");
        FileLock dynamicRevisionsLock = cacheLockingManager.getCacheMetadataFileLock(dynamicRevisionsFile);
        return new BTreePersistentIndexedCache<RevisionKey, DynamicVersionCacheEntry>(dynamicRevisionsFile, dynamicRevisionsLock,
                new DefaultSerializer<DynamicVersionCacheEntry>(DynamicVersionCacheEntry.class.getClassLoader()));
    }

    public ResolvedDynamicVersion getResolvedDynamicVersion(DependencyResolver resolver, ModuleRevisionId dynamicVersion) {
        DynamicVersionCacheEntry dynamicVersionCacheEntry = getCache().get(createKey(resolver, dynamicVersion));
        return dynamicVersionCacheEntry == null ? null : new DefaultResolvedDynamicVersion(dynamicVersionCacheEntry, timeProvider);
    }

    public void saveResolvedDynamicVersion(DependencyResolver resolver, ModuleRevisionId dynamicVersion, ModuleRevisionId resolvedVersion) {
        getCache().put(createKey(resolver, dynamicVersion), createEntry(resolvedVersion));
    }

    private RevisionKey createKey(DependencyResolver resolver, ModuleRevisionId revisionId) {
        return new RevisionKey(resolver, revisionId);
    }

    private static class RevisionKey implements Serializable {
        private final String resolverId;
        private final String revisionId;

        private RevisionKey(DependencyResolver resolver, ModuleRevisionId revision) {
            this.resolverId = new WharfResolverMetadata(resolver).getId();
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

    private DynamicVersionCacheEntry createEntry(ModuleRevisionId revisionId) {
        return new DynamicVersionCacheEntry(revisionId, timeProvider);
    }

}
