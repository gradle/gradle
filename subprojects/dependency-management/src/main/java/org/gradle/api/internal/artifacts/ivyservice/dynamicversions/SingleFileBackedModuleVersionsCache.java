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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.util.BuildCommencedTimeProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.Set;

public class SingleFileBackedModuleVersionsCache implements ModuleVersionsCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleFileBackedModuleVersionsCache.class);

    private final BuildCommencedTimeProvider timeProvider;
    private final CacheLockingManager cacheLockingManager;
    private PersistentIndexedCache<ModuleKey, ModuleVersionsCacheEntry> cache;

    public SingleFileBackedModuleVersionsCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        this.timeProvider = timeProvider;
        this.cacheLockingManager = cacheLockingManager;
    }

    private PersistentIndexedCache<ModuleKey, ModuleVersionsCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<ModuleKey, ModuleVersionsCacheEntry> initCache() {
        return cacheLockingManager.createCache("module-versions", new ModuleKeySerializer(), new ModuleVersionsCacheEntrySerializer());
    }

    public void cacheModuleVersionList(ModuleComponentRepository repository, ModuleIdentifier moduleId, Set<String> listedVersions) {
        LOGGER.debug("Caching version list in module versions cache: Using '{}' for '{}'", listedVersions, moduleId);
        getCache().put(createKey(repository, moduleId), createEntry(listedVersions));
    }

    public CachedModuleVersionList getCachedModuleResolution(ModuleComponentRepository repository, ModuleIdentifier moduleId) {
        ModuleVersionsCacheEntry moduleVersionsCacheEntry = getCache().get(createKey(repository, moduleId));
        if (moduleVersionsCacheEntry == null) {
            return null;
        }
        return new DefaultCachedModuleVersionList(moduleVersionsCacheEntry, timeProvider);
    }

    private ModuleKey createKey(ModuleComponentRepository repository, ModuleIdentifier moduleId) {
        return new ModuleKey(repository.getId(), moduleId);
    }

    private ModuleVersionsCacheEntry createEntry(Set<String> listedVersions) {
        return new ModuleVersionsCacheEntry(listedVersions, timeProvider.getCurrentTime());
    }

    private static class ModuleKey {
        private final String repositoryId;
        private final ModuleIdentifier moduleId;

        private ModuleKey(String repositoryId, ModuleIdentifier moduleId) {
            this.repositoryId = repositoryId;
            this.moduleId = moduleId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof ModuleKey)) {
                return false;
            }
            ModuleKey other = (ModuleKey) o;
            return repositoryId.equals(other.repositoryId) && moduleId.equals(other.moduleId);
        }

        @Override
        public int hashCode() {
            return repositoryId.hashCode() ^ moduleId.hashCode();
        }
    }

    private static class ModuleKeySerializer extends AbstractSerializer<ModuleKey> {
        public void write(Encoder encoder, ModuleKey value) throws Exception {
            encoder.writeString(value.repositoryId);
            encoder.writeString(value.moduleId.getGroup());
            encoder.writeString(value.moduleId.getName());
        }

        public ModuleKey read(Decoder decoder) throws Exception {
            String resolverId = decoder.readString();
            String group = decoder.readString();
            String module = decoder.readString();
            return new ModuleKey(resolverId, new DefaultModuleIdentifier(group, module));
        }
    }

    private static class ModuleVersionsCacheEntrySerializer extends AbstractSerializer<ModuleVersionsCacheEntry> {

        public void write(Encoder encoder, ModuleVersionsCacheEntry value) throws Exception {
            Set<String> versions = value.moduleVersionListing;
            encoder.writeInt(versions.size());
            for (String version : versions) {
                encoder.writeString(version);
            }
            encoder.writeLong(value.createTimestamp);
        }

        public ModuleVersionsCacheEntry read(Decoder decoder) throws Exception {
            int size = decoder.readInt();
            Set<String> versions = new LinkedHashSet<String>();
            for (int i = 0; i < size; i++) {
                versions.add(decoder.readString());
            }
            long createTimestamp = decoder.readLong();
            return new ModuleVersionsCacheEntry(versions, createTimestamp);
        }
    }

}
