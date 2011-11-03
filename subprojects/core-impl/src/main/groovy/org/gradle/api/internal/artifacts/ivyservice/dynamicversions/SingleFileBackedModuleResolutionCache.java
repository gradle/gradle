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

public class SingleFileBackedModuleResolutionCache implements ModuleResolutionCache {
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
        FileLock dynamicRevisionsLock = cacheLockingManager.getCacheMetadataFileLock(dynamicRevisionsFile);
        return new BTreePersistentIndexedCache<RevisionKey, ModuleResolutionCacheEntry>(dynamicRevisionsFile, dynamicRevisionsLock,
                new DefaultSerializer<ModuleResolutionCacheEntry>(ModuleResolutionCacheEntry.class.getClassLoader()));
    }

    public void recordResolvedDynamicVersion(DependencyResolver resolver, ModuleRevisionId requestedVersion, ModuleRevisionId resolvedVersion) {
        getCache().put(createKey(resolver, requestedVersion), createEntry(resolvedVersion));
    }

    public void recordChangingModuleResolution(DependencyResolver resolver, ModuleRevisionId module) {
        getCache().put(createKey(resolver, module), createEntry(null));
    }

    public CachedModuleResolution getCachedModuleResolution(DependencyResolver resolver, ModuleRevisionId moduleId) {
        ModuleResolutionCacheEntry moduleResolutionCacheEntry = getCache().get(createKey(resolver, moduleId));
        if (moduleResolutionCacheEntry == null) {
            return null;
        }
        return new DefaultCachedModuleResolution(moduleId, moduleResolutionCacheEntry, timeProvider);
    }

    private RevisionKey createKey(DependencyResolver resolver, ModuleRevisionId revisionId) {
        return new RevisionKey(resolver, revisionId);
    }

    private ModuleResolutionCacheEntry createEntry(ModuleRevisionId revisionId) {
        return new ModuleResolutionCacheEntry(revisionId, timeProvider);
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

}
