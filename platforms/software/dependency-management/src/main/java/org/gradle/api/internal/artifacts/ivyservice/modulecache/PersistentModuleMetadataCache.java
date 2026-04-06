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

import com.google.common.base.Objects;
import com.google.common.collect.Interner;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheMetadata;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.AttributeContainerSerializer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentIdentifierSerializer;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.cache.IndexedCache;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.resource.local.DefaultPathKeyFileStore;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.kryo.KryoBackedDecoder;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;

public class PersistentModuleMetadataCache extends AbstractModuleMetadataCache {

    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final Interner<String> stringInterner;

    private IndexedCache<ModuleComponentAtRepositoryKey, ModuleMetadataCacheEntry> cache;
    private final ModuleMetadataStore moduleMetadataStore;
    private final ArtifactCacheLockingAccessCoordinator artifactCacheLockingManager;
    private final ModuleMetadataSerializer moduleMetadataSerializer;

    public PersistentModuleMetadataCache(
        BuildCommencedTimeProvider timeProvider,
        ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator,
        ArtifactCacheMetadata artifactCacheMetadata,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        AttributeContainerSerializer attributeContainerSerializer,
        CapabilitySelectorSerializer capabilitySelectorSerializer,
        MavenMutableModuleMetadataFactory mavenMetadataFactory,
        IvyMutableModuleMetadataFactory ivyMetadataFactory,
        Interner<String> stringInterner,
        ModuleSourcesSerializer moduleSourcesSerializer,
        ChecksumService checksumService
    ) {
        super(timeProvider);
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.stringInterner = stringInterner;
        this.moduleMetadataSerializer = new ModuleMetadataSerializer(attributeContainerSerializer, capabilitySelectorSerializer, mavenMetadataFactory, ivyMetadataFactory, moduleSourcesSerializer);
        this.moduleMetadataStore =new ModuleMetadataStore(new DefaultPathKeyFileStore(checksumService, artifactCacheMetadata.getMetaDataStoreDirectory()));
        this.artifactCacheLockingManager = cacheAccessCoordinator;
    }

    private IndexedCache<ModuleComponentAtRepositoryKey, ModuleMetadataCacheEntry> getCache() {
        if (cache == null) {
            cache = initCache();
        }
        return cache;
    }

    private IndexedCache<ModuleComponentAtRepositoryKey, ModuleMetadataCacheEntry> initCache() {
        return artifactCacheLockingManager.createCache("module-metadata", new RevisionKeySerializer(), new ModuleMetadataCacheEntrySerializer());
    }

    @Override
    protected CachedMetadata get(ModuleComponentAtRepositoryKey key) {
        final IndexedCache<ModuleComponentAtRepositoryKey, ModuleMetadataCacheEntry> cache = getCache();
        ReadResult result = artifactCacheLockingManager.useCache(() -> {
            ModuleMetadataCacheEntry entry = cache.getIfPresent(key);
            if (entry == null) {
                return null;
            }
            if (entry.isMissing()) {
                return new ReadResult.Missing(entry);
            }

            byte[] data = moduleMetadataStore.getModuleDescriptor(key);
            if (data == null) {
                // Descriptor file has been deleted - ignore the entry
                cache.remove(key);
                return null;
            }

            return new ReadResult.Found(entry, data);
        });

        if (result == null) {
            return null;
        } else if (result instanceof ReadResult.Missing missing) {
            return new DefaultCachedMetadata(missing.entry(), null, timeProvider);
        } else if (result instanceof ReadResult.Found found) {
            MutableModuleComponentResolveMetadata metadata;
            try {
                try (StringDeduplicatingDecoder decoder = new StringDeduplicatingDecoder(new KryoBackedDecoder(new ByteArrayInputStream(found.data())), stringInterner)) {
                    metadata = moduleMetadataSerializer.read(decoder, moduleIdentifierFactory, new HashMap<>());
                }
            } catch (IOException e) {
                throw new RuntimeException("Could not parse module metadata for " + key, e);
            }
            ModuleMetadataCacheEntry entry = found.entry();
            return new DefaultCachedMetadata(entry, entry.configure(metadata), timeProvider);
        } else {
            throw new IllegalStateException("Unexpected result type: " + result.getClass());
        }
    }

    @Override
    protected CachedMetadata store(final ModuleComponentAtRepositoryKey key, final ModuleMetadataCacheEntry entry, final CachedMetadata cachedMetadata) {
        if (entry.isMissing()) {
            getCache().put(key, entry);
        } else {
            byte[] data;
            try {
                ModuleComponentResolveMetadata metadata = cachedMetadata.getMetadata();
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                try (KryoBackedEncoder encoder = new KryoBackedEncoder(os)) {
                    moduleMetadataSerializer.write(encoder, metadata, new HashMap<>());
                }
                data = os.toByteArray();
            } catch (IOException e) {
                throw new RuntimeException("Could not encode module metadata for " + key, e);
            }
            // Need to lock the cache in order to write to the module metadata store
            artifactCacheLockingManager.useCache(() -> {
                moduleMetadataStore.putModuleDescriptor(key, data);
                getCache().put(key, entry);
            });
        }
        return cachedMetadata;
    }

    private static class RevisionKeySerializer extends AbstractSerializer<ModuleComponentAtRepositoryKey> {
        private final ComponentIdentifierSerializer componentIdSerializer = new ComponentIdentifierSerializer();

        @Override
        public void write(Encoder encoder, ModuleComponentAtRepositoryKey value) throws Exception {
            encoder.writeString(value.getRepositoryId());
            componentIdSerializer.write(encoder, value.getComponentId());
        }

        @Override
        public ModuleComponentAtRepositoryKey read(Decoder decoder) throws Exception {
            String resolverId = decoder.readString();
            ModuleComponentIdentifier identifier = (ModuleComponentIdentifier) componentIdSerializer.read(decoder);
            return new ModuleComponentAtRepositoryKey(resolverId, identifier);
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            RevisionKeySerializer rhs = (RevisionKeySerializer) obj;
            return Objects.equal(componentIdSerializer, rhs.componentIdSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), componentIdSerializer);
        }
    }

    sealed interface ReadResult {
        record Missing(ModuleMetadataCacheEntry entry) implements ReadResult {}
        record Found(ModuleMetadataCacheEntry entry, byte[] data) implements ReadResult {}
    }

}
