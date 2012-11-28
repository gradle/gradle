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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyXmlModuleDescriptorWriter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.api.internal.filestore.FileStoreEntry;
import org.gradle.api.internal.filestore.PathKeyFileStore;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.TimeProvider;
import org.gradle.util.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;

public class DefaultModuleDescriptorCache implements ModuleDescriptorCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultModuleDescriptorCache.class);

    private final TimeProvider timeProvider;
    private final ArtifactCacheMetaData cacheMetadata;
    private final CacheLockingManager cacheLockingManager;

    private final ModuleDescriptorStore moduleDescriptorStore;
    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> cache;

    public DefaultModuleDescriptorCache(ArtifactCacheMetaData cacheMetadata, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
        this.cacheMetadata = cacheMetadata;

        // TODO:DAZ inject this
        moduleDescriptorStore = new ModuleDescriptorStore(new PathKeyFileStore(cacheMetadata.getCacheDir()), new IvyXmlModuleDescriptorWriter(), new IvyXmlModuleDescriptorParser());
    }

    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<RevisionKey, ModuleDescriptorCacheEntry> initCache() {
        File artifactResolutionCacheFile = new File(cacheMetadata.getCacheDir(), "module-metadata.bin");
        return cacheLockingManager.createCache(artifactResolutionCacheFile, RevisionKey.class, ModuleDescriptorCacheEntry.class);
    }

    public CachedModuleDescriptor getCachedModuleDescriptor(ModuleVersionRepository repository, ModuleRevisionId moduleRevisionId) {
        ModuleDescriptorCacheEntry moduleDescriptorCacheEntry = getCache().get(createKey(repository, moduleRevisionId));
        if (moduleDescriptorCacheEntry == null) {
            return null;
        }
        if (moduleDescriptorCacheEntry.isMissing) {
            return new DefaultCachedModuleDescriptor(moduleDescriptorCacheEntry, null, timeProvider);
        }
        ModuleDescriptor descriptor = moduleDescriptorStore.getModuleDescriptor(repository, moduleRevisionId);
        if (descriptor == null) {
            // Descriptor file has been manually deleted - ignore the entry
            return null;
        }
        return new DefaultCachedModuleDescriptor(moduleDescriptorCacheEntry, descriptor, timeProvider);
    }

    public void cacheModuleDescriptor(ModuleVersionRepository repository, ModuleRevisionId moduleRevisionId, ModuleDescriptor moduleDescriptor, boolean isChanging) {
        if (moduleDescriptor == null) {
            LOGGER.debug("Recording absence of module descriptor in cache: {} [changing = {}]", moduleRevisionId, isChanging);
            getCache().put(createKey(repository, moduleRevisionId), createMissingEntry(isChanging));
        } else {
            LOGGER.debug("Recording module descriptor in cache: {} [changing = {}]", moduleDescriptor.getModuleRevisionId(), isChanging);
            FileStoreEntry fileStoreEntry = moduleDescriptorStore.putModuleDescriptor(repository, moduleDescriptor);
            getCache().put(createKey(repository, moduleRevisionId), createEntry(isChanging, fileStoreEntry.getSha1()));
        }
    }

    private RevisionKey createKey(ModuleVersionRepository resolver, ModuleRevisionId moduleRevisionId) {
        return new RevisionKey(resolver, moduleRevisionId);
    }

    private ModuleDescriptorCacheEntry createMissingEntry(boolean changing) {
        return new ModuleDescriptorCacheEntry(changing, true, timeProvider, BigInteger.ZERO);
    }

    private ModuleDescriptorCacheEntry createEntry(boolean changing, HashValue moduleDescriptorHash) {
        return new ModuleDescriptorCacheEntry(changing, false, timeProvider, new BigInteger(1, moduleDescriptorHash.asByteArray()));
    }

    private static class RevisionKey implements Serializable {
        private final String resolverId;
        private final String moduleRevisionId;

        private RevisionKey(ModuleVersionRepository repository, ModuleRevisionId moduleRevisionId) {
            this.resolverId = repository.getId();
            this.moduleRevisionId = moduleRevisionId == null ? null : moduleRevisionId.encodeToString();
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof RevisionKey)) {
                return false;
            }
            RevisionKey other = (RevisionKey) o;
            return resolverId.equals(other.resolverId) && moduleRevisionId.equals(other.moduleRevisionId);
        }

        @Override
        public int hashCode() {
            return resolverId.hashCode() ^ moduleRevisionId.hashCode();
        }
    }

}
