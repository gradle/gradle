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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions;

import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultModuleVersionsCache extends InMemoryModuleVersionsCache {

    private final CacheLockingManager cacheLockingManager;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

    private PersistentIndexedCache<ModuleAtRepositoryKey, ModuleVersionsCacheEntry> cache;

    public DefaultModuleVersionsCache(BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        super(timeProvider);
        this.cacheLockingManager = cacheLockingManager;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
    }

    private PersistentIndexedCache<ModuleAtRepositoryKey, ModuleVersionsCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private PersistentIndexedCache<ModuleAtRepositoryKey, ModuleVersionsCacheEntry> initCache() {
        return cacheLockingManager.createCache("module-versions", new ModuleKeySerializer(moduleIdentifierFactory), new ModuleVersionsCacheEntrySerializer());
    }

    @Override
    protected void store(ModuleAtRepositoryKey key, ModuleVersionsCacheEntry entry) {
        super.store(key, entry);
        getCache().put(key, entry);
    }

    @Override
    protected ModuleVersionsCacheEntry get(ModuleAtRepositoryKey key) {
        ModuleVersionsCacheEntry entry = super.get(key);
        if (entry == null) {
            entry = getCache().get(key);
            if (entry != null) {
                super.store(key, entry);
            }
        }
        return entry;
    }

    private static class ModuleKeySerializer extends AbstractSerializer<ModuleAtRepositoryKey> {
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;

        private ModuleKeySerializer(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
            this.moduleIdentifierFactory = moduleIdentifierFactory;
        }

        public void write(Encoder encoder, ModuleAtRepositoryKey value) throws Exception {
            encoder.writeString(value.repositoryId);
            encoder.writeString(value.moduleId.getGroup());
            encoder.writeString(value.moduleId.getName());
        }

        public ModuleAtRepositoryKey read(Decoder decoder) throws Exception {
            String resolverId = decoder.readString();
            String group = decoder.readString();
            String module = decoder.readString();
            return new ModuleAtRepositoryKey(resolverId, moduleIdentifierFactory.module(group, module));
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
