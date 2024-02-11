/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingAccessCoordinator;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifierSerializer;
import org.gradle.api.internal.artifacts.metadata.ModuleComponentFileArtifactIdentifierSerializer;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentFileArtifactIdentifier;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.cached.AbstractCachedIndex;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.internal.BuildCommencedTimeProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DefaultModuleArtifactCache extends AbstractCachedIndex<ArtifactAtRepositoryKey, CachedArtifact> implements ModuleArtifactCache {
    private static final ArtifactAtRepositoryKeySerializer KEY_SERIALIZER = keySerializer();
    private final BuildCommencedTimeProvider timeProvider;

    public DefaultModuleArtifactCache(String persistentCacheFile, BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingAccessCoordinator cacheAccessCoordinator, FileAccessTracker fileAccessTracker, Path commonRootPath) {
        super(persistentCacheFile, KEY_SERIALIZER, new CachedArtifactSerializer(commonRootPath), cacheAccessCoordinator, fileAccessTracker);
        this.timeProvider = timeProvider;
    }

    protected static ArtifactAtRepositoryKeySerializer keySerializer() {
        DefaultSerializerRegistry serializerRegistry = new DefaultSerializerRegistry();
        serializerRegistry.register(DefaultModuleComponentArtifactIdentifier.class, new ComponentArtifactIdentifierSerializer());
        serializerRegistry.register(ModuleComponentFileArtifactIdentifier.class, new ModuleComponentFileArtifactIdentifierSerializer());
        return new ArtifactAtRepositoryKeySerializer(serializerRegistry.build(ComponentArtifactIdentifier.class));
    }

    @Override
    public void store(final ArtifactAtRepositoryKey key, final File artifactFile, HashCode moduleDescriptorHash) {
        assertArtifactFileNotNull(artifactFile);
        assertKeyNotNull(key);
        storeInternal(key, createEntry(artifactFile, moduleDescriptorHash));
    }

    private DefaultCachedArtifact createEntry(File artifactFile, HashCode moduleDescriptorHash) {
        return new DefaultCachedArtifact(artifactFile, timeProvider.getCurrentTime(), moduleDescriptorHash);
    }

    @Override
    public void storeMissing(ArtifactAtRepositoryKey key, List<String> attemptedLocations, HashCode descriptorHash) {
        storeInternal(key, createMissingEntry(attemptedLocations, descriptorHash));
    }

    private CachedArtifact createMissingEntry(List<String> attemptedLocations, HashCode descriptorHash) {
        return new DefaultCachedArtifact(attemptedLocations, timeProvider.getCurrentTime(), descriptorHash);
    }

    @Override
    public CachedArtifact lookup(ArtifactAtRepositoryKey key) {
        assertKeyNotNull(key);

        return super.lookup(key);
    }

    private static class ArtifactAtRepositoryKeySerializer implements Serializer<ArtifactAtRepositoryKey> {
        private final Serializer<ComponentArtifactIdentifier> artifactIdSerializer;

        public ArtifactAtRepositoryKeySerializer(Serializer<ComponentArtifactIdentifier> artifactIdSerializer) {
            this.artifactIdSerializer = artifactIdSerializer;
        }

        @Override
        public void write(Encoder encoder, ArtifactAtRepositoryKey value) throws Exception {
            encoder.writeString(value.getRepositoryId());
            artifactIdSerializer.write(encoder, value.getArtifactId());
        }

        @Override
        public ArtifactAtRepositoryKey read(Decoder decoder) throws Exception {
            String repositoryId = decoder.readString();
            ComponentArtifactIdentifier artifactIdentifier = artifactIdSerializer.read(decoder);
            return new ArtifactAtRepositoryKey(repositoryId, artifactIdentifier);
        }
    }

    @VisibleForTesting
    static class CachedArtifactSerializer implements Serializer<CachedArtifact> {

        private final Path commonRootPath;

        public CachedArtifactSerializer(Path commonRootPath) {
            this.commonRootPath = commonRootPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CachedArtifactSerializer that = (CachedArtifactSerializer) o;
            return commonRootPath.equals(that.commonRootPath);
        }

        @Override
        public int hashCode() {
            return commonRootPath.hashCode();
        }

        @Override
        public void write(Encoder encoder, CachedArtifact value) throws Exception {
            encoder.writeBoolean(value.isMissing());
            encoder.writeLong(value.getCachedAt());
            byte[] hash = value.getDescriptorHash().toByteArray();
            encoder.writeBinary(hash);
            if (!value.isMissing()) {
                encoder.writeString(relativizeAndNormalizeFilePath(value.getCachedFile()));
            } else {
                encoder.writeSmallInt(value.attemptedLocations().size());
                for (String location : value.attemptedLocations()) {
                    encoder.writeString(location);
                }
            }
        }

        @Override
        public CachedArtifact read(Decoder decoder) throws Exception {
            boolean isMissing = decoder.readBoolean();
            long createTimestamp = decoder.readLong();
            byte[] encodedHash = decoder.readBinary();
            HashCode hash = HashCode.fromBytes(encodedHash);
            if (!isMissing) {
                return new DefaultCachedArtifact(denormalizeAndResolveFilePath(decoder.readString()), createTimestamp, hash);
            } else {
                int size = decoder.readSmallInt();
                List<String> attempted = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    attempted.add(decoder.readString());
                }
                return new DefaultCachedArtifact(attempted, createTimestamp, hash);
            }
        }

        private String relativizeAndNormalizeFilePath(File cachedFile) {
            Path filePath = cachedFile.toPath();
            assert filePath.startsWith(commonRootPath) : "Attempting to cache file " + filePath + " not in " + commonRootPath;
            String systemDependentPath = commonRootPath.relativize(filePath).toString();
            if (!filePath.getFileSystem().getSeparator().equals("/")) {
                return systemDependentPath.replace(filePath.getFileSystem().getSeparator(), "/");
            }
            return systemDependentPath;
        }

        private File denormalizeAndResolveFilePath(String relativePath) throws IOException {
            if (!commonRootPath.getFileSystem().getSeparator().equals("/")) {
                relativePath = relativePath.replace("/", commonRootPath.getFileSystem().getSeparator());
            }
            return commonRootPath.resolve(relativePath).toFile();
        }
    }

}
