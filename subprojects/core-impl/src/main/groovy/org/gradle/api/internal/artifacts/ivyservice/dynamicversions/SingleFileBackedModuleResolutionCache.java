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
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetaData;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.TimeProvider;
import org.gradle.messaging.serialize.DataStreamBackedSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

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
        return cacheLockingManager.createCache(dynamicRevisionsFile, new RevisionKeySerializer(), new ModuleResolutionCacheEntrySerializer());
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
        return new RevisionKey(repository.getId(), revisionId.encodeToString());
    }

    private ModuleResolutionCacheEntry createEntry(ModuleVersionIdentifier moduleVersionIdentifier) {
        return new ModuleResolutionCacheEntry(moduleVersionIdentifier, timeProvider.getCurrentTime());
    }

    private static class RevisionKey {
        private final String repositoryId;
        private final String revisionId;

        private RevisionKey(String repositoryId, String revisionId) {
            this.repositoryId = repositoryId;
            this.revisionId = revisionId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || !(o instanceof RevisionKey)) {
                return false;
            }
            RevisionKey other = (RevisionKey) o;
            return repositoryId.equals(other.repositoryId) && revisionId.equals(other.revisionId);
        }

        @Override
        public int hashCode() {
            return repositoryId.hashCode() ^ revisionId.hashCode();
        }
    }

    private static class RevisionKeySerializer extends DataStreamBackedSerializer<RevisionKey> {
        @Override
        public void write(DataOutput dataOutput, RevisionKey value) throws IOException {
            dataOutput.writeUTF(value.repositoryId);
            dataOutput.writeUTF(value.revisionId);
        }

        @Override
        public RevisionKey read(DataInput dataInput) throws IOException {
            String resolverId = dataInput.readUTF();
            String revisionId = dataInput.readUTF();
            return new RevisionKey(resolverId, revisionId);
        }
    }

    private static class ModuleResolutionCacheEntrySerializer extends DataStreamBackedSerializer<ModuleResolutionCacheEntry> {
        private final ModuleVersionIdentifierSerializer identifierSerializer = new ModuleVersionIdentifierSerializer();

        @Override
        public void write(DataOutput dataOutput, ModuleResolutionCacheEntry value) throws IOException {
            identifierSerializer.write(dataOutput, value.moduleVersionIdentifier);
            dataOutput.writeLong(value.createTimestamp);
        }

        @Override
        public ModuleResolutionCacheEntry read(DataInput dataInput) throws IOException {
            ModuleVersionIdentifier identifier = identifierSerializer.read(dataInput);
            long createTimestamp = dataInput.readLong();
            return new ModuleResolutionCacheEntry(identifier, createTimestamp);
        }
    }

}
