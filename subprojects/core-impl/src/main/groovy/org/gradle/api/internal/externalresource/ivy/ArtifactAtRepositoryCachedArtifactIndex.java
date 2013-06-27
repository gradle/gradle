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

package org.gradle.api.internal.externalresource.ivy;

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionIdentifierSerializer;
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.externalresource.cached.CachedArtifact;
import org.gradle.api.internal.externalresource.cached.CachedArtifactIndex;
import org.gradle.api.internal.externalresource.cached.DefaultCachedArtifact;
import org.gradle.internal.TimeProvider;
import org.gradle.messaging.serialize.DataStreamBackedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

public class ArtifactAtRepositoryCachedArtifactIndex extends AbstractCachedIndex<ArtifactAtRepositoryKey, CachedArtifact> implements CachedArtifactIndex {
    private final TimeProvider timeProvider;

    public ArtifactAtRepositoryCachedArtifactIndex(File persistentCacheFile, TimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        super(persistentCacheFile, new ArtifactAtRepositoryKeySerializer(), new CachedArtifactSerializer(), cacheLockingManager);
        this.timeProvider = timeProvider;
    }

    private DefaultCachedArtifact createEntry(File artifactFile, BigInteger moduleDescriptorHash) {
        return new DefaultCachedArtifact(artifactFile, timeProvider.getCurrentTime(), moduleDescriptorHash);
    }

    public void store(final ArtifactAtRepositoryKey key, final File artifactFile, BigInteger moduleDescriptorHash) {
        assertArtifactFileNotNull(artifactFile);
        assertKeyNotNull(key);
        storeInternal(key, createEntry(artifactFile, moduleDescriptorHash));
    }

    public void storeMissing(ArtifactAtRepositoryKey key, BigInteger descriptorHash) {
        storeInternal(key, createMissingEntry(descriptorHash));
    }

    CachedArtifact createMissingEntry(BigInteger descriptorHash) {
        return new DefaultCachedArtifact(timeProvider.getCurrentTime(), descriptorHash);
    }

    private static class ArtifactAtRepositoryKeySerializer extends DataStreamBackedSerializer<ArtifactAtRepositoryKey> {
        private final ModuleVersionIdentifierSerializer modIdSerializer = new ModuleVersionIdentifierSerializer();

        @Override
        public void write(DataOutput dataOutput, ArtifactAtRepositoryKey value) throws IOException {
            dataOutput.writeUTF(value.getRepositoryId());
            ArtifactIdentifier artifact = value.getArtifactId();
            modIdSerializer.write(dataOutput, artifact.getModuleVersionIdentifier());
            dataOutput.writeUTF(artifact.getName());
            writeNullable(dataOutput, artifact.getExtension());
            writeNullable(dataOutput, artifact.getClassifier());
            writeNullable(dataOutput, artifact.getType());
        }

        @Override
        public ArtifactAtRepositoryKey read(DataInput dataInput) throws IOException {
            String repositoryId = dataInput.readUTF();
            ModuleVersionIdentifier moduleVersionIdentifier = modIdSerializer.read(dataInput);
            String artifactName = dataInput.readUTF();
            String extension = readNullable(dataInput);
            String classifier = readNullable(dataInput);
            String type = readNullable(dataInput);
            return new ArtifactAtRepositoryKey(repositoryId, new DefaultArtifactIdentifier(moduleVersionIdentifier, artifactName, type, extension, classifier));
        }

        String readNullable(DataInput dataInput) throws IOException {
            if (dataInput.readBoolean()) {
                return dataInput.readUTF();
            } else {
                return null;
            }
        }

        void writeNullable(DataOutput dataOutput, String value) throws IOException {
            if (value == null) {
                dataOutput.writeBoolean(false);
            } else {
                dataOutput.writeBoolean(true);
                dataOutput.writeUTF(value);
            }
        }
    }

    private static class CachedArtifactSerializer extends DataStreamBackedSerializer<CachedArtifact> {
        @Override
        public void write(DataOutput dataOutput, CachedArtifact value) throws IOException {
            dataOutput.writeBoolean(value.isMissing());
            if (!value.isMissing()) {
                dataOutput.writeUTF(value.getCachedFile().getPath());
            }
            dataOutput.writeLong(value.getCachedAt());
            byte[] hash = value.getDescriptorHash().toByteArray();
            dataOutput.writeInt(hash.length);
            dataOutput.write(hash);
        }

        @Override
        public CachedArtifact read(DataInput dataInput) throws Exception {
            boolean isMissing = dataInput.readBoolean();
            File file;
            if (!isMissing) {
                file = new File(dataInput.readUTF());
            } else {
                file = null;
            }
            long createTimestamp = dataInput.readLong();
            int count = dataInput.readInt();
            byte[] encodedHash = new byte[count];
            dataInput.readFully(encodedHash);
            BigInteger hash = new BigInteger(encodedHash);
            return new DefaultCachedArtifact(file, createTimestamp, hash);
        }
    }
}
