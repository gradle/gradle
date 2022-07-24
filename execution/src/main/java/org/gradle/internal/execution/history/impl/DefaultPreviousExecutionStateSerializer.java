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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshotSerializer;
import org.gradle.internal.snapshot.impl.SnapshotSerializer;

import java.time.Duration;
import java.util.Map;

public class DefaultPreviousExecutionStateSerializer extends AbstractSerializer<PreviousExecutionState> {
    private final Serializer<FileCollectionFingerprint> fileCollectionFingerprintSerializer;
    private final Serializer<FileSystemSnapshot> fileSystemSnapshotSerializer;
    private final Serializer<ImplementationSnapshot> implementationSnapshotSerializer;
    private final Serializer<ValueSnapshot> valueSnapshotSerializer;

    public DefaultPreviousExecutionStateSerializer(
        Serializer<FileCollectionFingerprint> fileCollectionFingerprintSerializer,
        Serializer<FileSystemSnapshot> fileSystemSnapshotSerializer,
        ClassLoaderHierarchyHasher classLoaderHasher
    ) {
        this.fileCollectionFingerprintSerializer = fileCollectionFingerprintSerializer;
        this.fileSystemSnapshotSerializer = fileSystemSnapshotSerializer;
        this.implementationSnapshotSerializer = new ImplementationSnapshotSerializer();
        this.valueSnapshotSerializer = new SnapshotSerializer(classLoaderHasher);
    }

    @Override
    public PreviousExecutionState read(Decoder decoder) throws Exception {
        OriginMetadata originMetadata = new OriginMetadata(
            decoder.readString(),
            Duration.ofMillis(decoder.readLong())
        );

        ImplementationSnapshot taskImplementation = implementationSnapshotSerializer.read(decoder);

        // We can't use an immutable list here because some hashes can be null
        int taskActionsCount = decoder.readSmallInt();
        ImmutableList.Builder<ImplementationSnapshot> taskActionImplementationsBuilder = ImmutableList.builder();
        for (int j = 0; j < taskActionsCount; j++) {
            ImplementationSnapshot actionImpl = implementationSnapshotSerializer.read(decoder);
            taskActionImplementationsBuilder.add(actionImpl);
        }
        ImmutableList<ImplementationSnapshot> taskActionImplementations = taskActionImplementationsBuilder.build();

        ImmutableSortedMap<String, ValueSnapshot> inputProperties = readInputProperties(decoder);
        ImmutableSortedMap<String, FileCollectionFingerprint> inputFilesFingerprints = readFingerprints(decoder);
        ImmutableSortedMap<String, FileSystemSnapshot> outputFilesSnapshots = readSnapshots(decoder);

        boolean successful = decoder.readBoolean();

        return new DefaultPreviousExecutionState(
            originMetadata,
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            inputFilesFingerprints,
            outputFilesSnapshots,
            successful
        );
    }

    @Override
    public void write(Encoder encoder, PreviousExecutionState execution) throws Exception {
        OriginMetadata originMetadata = execution.getOriginMetadata();
        encoder.writeString(originMetadata.getBuildInvocationId());
        encoder.writeLong(originMetadata.getExecutionTime().toMillis());

        implementationSnapshotSerializer.write(encoder, execution.getImplementation());
        ImmutableList<ImplementationSnapshot> additionalImplementations = execution.getAdditionalImplementations();
        encoder.writeSmallInt(additionalImplementations.size());
        for (ImplementationSnapshot actionImpl : additionalImplementations) {
            implementationSnapshotSerializer.write(encoder, actionImpl);
        }

        writeInputProperties(encoder, execution.getInputProperties());
        writeFingerprints(encoder, execution.getInputFileProperties());
        writeSnapshots(encoder, execution.getOutputFilesProducedByWork());

        encoder.writeBoolean(execution.isSuccessful());
    }

    public ImmutableSortedMap<String, ValueSnapshot> readInputProperties(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableSortedMap.of();
        }
        if (size == 1) {
            return ImmutableSortedMap.of(decoder.readString(), readValueSnapshot(decoder));
        }

        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < size; i++) {
            builder.put(decoder.readString(), readValueSnapshot(decoder));
        }
        return builder.build();
    }

    public void writeInputProperties(Encoder encoder, ImmutableMap<String, ValueSnapshot> properties) throws Exception {
        encoder.writeSmallInt(properties.size());
        for (Map.Entry<String, ValueSnapshot> entry : properties.entrySet()) {
            encoder.writeString(entry.getKey());
            writeValueSnapshot(encoder, entry.getValue());
        }
    }

    private ImmutableSortedMap<String, FileCollectionFingerprint> readFingerprints(Decoder decoder) throws Exception {
        int count = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, FileCollectionFingerprint> builder = ImmutableSortedMap.naturalOrder();
        for (int fingerprintIdx = 0; fingerprintIdx < count; fingerprintIdx++) {
            String property = decoder.readString();
            FileCollectionFingerprint fingerprint = fileCollectionFingerprintSerializer.read(decoder);
            builder.put(property, fingerprint);
        }
        return builder.build();
    }

    private void writeFingerprints(Encoder encoder, Map<String, FileCollectionFingerprint> fingerprints) throws Exception {
        encoder.writeSmallInt(fingerprints.size());
        for (Map.Entry<String, FileCollectionFingerprint> entry : fingerprints.entrySet()) {
            encoder.writeString(entry.getKey());
            fileCollectionFingerprintSerializer.write(encoder, entry.getValue());
        }
    }

    private ImmutableSortedMap<String, FileSystemSnapshot> readSnapshots(Decoder decoder) throws Exception {
        int count = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
            String property = decoder.readString();
            FileSystemSnapshot snapshot = fileSystemSnapshotSerializer.read(decoder);
            builder.put(property, snapshot);
        }
        return builder.build();
    }

    private void writeSnapshots(Encoder encoder, ImmutableSortedMap<String, FileSystemSnapshot> snapshots) throws Exception {
        encoder.writeSmallInt(snapshots.size());
        for (Map.Entry<String, FileSystemSnapshot> entry : snapshots.entrySet()) {
            encoder.writeString(entry.getKey());
            fileSystemSnapshotSerializer.write(encoder, entry.getValue());
        }
    }

    private ValueSnapshot readValueSnapshot(Decoder decoder) throws Exception {
        return valueSnapshotSerializer.read(decoder);
    }

    private void writeValueSnapshot(Encoder encoder, ValueSnapshot snapshot) throws Exception {
        valueSnapshotSerializer.write(encoder, snapshot);
    }

}
