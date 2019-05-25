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
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.gradle.internal.snapshot.impl.SnapshotSerializer;

import java.util.Map;

public class DefaultPreviousExecutionStateSerializer extends AbstractSerializer<AfterPreviousExecutionState> {
    private final Serializer<FileCollectionFingerprint> fileCollectionFingerprintSerializer;
    private final Serializer<ImplementationSnapshot> implementationSnapshotSerializer;
    private final Serializer<ValueSnapshot> valueSnapshotSerializer = new SnapshotSerializer();

    public DefaultPreviousExecutionStateSerializer(Serializer<FileCollectionFingerprint> fileCollectionFingerprintSerializer) {
        this.fileCollectionFingerprintSerializer = fileCollectionFingerprintSerializer;
        this.implementationSnapshotSerializer = new ImplementationSnapshot.SerializerImpl();
    }

    @Override
    public AfterPreviousExecutionState read(Decoder decoder) throws Exception {
        OriginMetadata originMetadata = new OriginMetadata(
            UniqueId.from(decoder.readString()),
            decoder.readLong()
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
        ImmutableSortedMap<String, FileCollectionFingerprint> outputFilesFingerprints = readFingerprints(decoder);

        boolean successful = decoder.readBoolean();

        return new DefaultAfterPreviousExecutionState(
            originMetadata,
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            inputFilesFingerprints,
            outputFilesFingerprints,
            successful
        );
    }

    @Override
    public void write(Encoder encoder, AfterPreviousExecutionState execution) throws Exception {
        OriginMetadata originMetadata = execution.getOriginMetadata();
        encoder.writeString(originMetadata.getBuildInvocationId().asString());
        encoder.writeLong(originMetadata.getExecutionTime());

        implementationSnapshotSerializer.write(encoder, execution.getImplementation());
        ImmutableList<ImplementationSnapshot> additionalImplementations = execution.getAdditionalImplementations();
        encoder.writeSmallInt(additionalImplementations.size());
        for (ImplementationSnapshot actionImpl : additionalImplementations) {
            implementationSnapshotSerializer.write(encoder, actionImpl);
        }

        writeInputProperties(encoder, execution.getInputProperties());
        writeFingerprints(encoder, execution.getInputFileProperties());
        writeFingerprints(encoder, execution.getOutputFileProperties());

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

    private ValueSnapshot readValueSnapshot(Decoder decoder) throws Exception {
        return valueSnapshotSerializer.read(decoder);
    }

    private void writeValueSnapshot(Encoder encoder, ValueSnapshot snapshot) throws Exception {
        valueSnapshotSerializer.write(encoder, snapshot);
    }

}
