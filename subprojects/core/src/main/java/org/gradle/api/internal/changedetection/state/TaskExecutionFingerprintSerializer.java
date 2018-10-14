/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import java.util.Map;

public class TaskExecutionFingerprintSerializer extends AbstractSerializer<HistoricalTaskExecution> {
    private final InputPropertiesSerializer inputPropertiesSerializer;
    private final Serializer<HistoricalFileCollectionFingerprint> fileCollectionFingerprintSerializer;
    private final Serializer<ImplementationSnapshot> implementationSnapshotSerializer;

    public TaskExecutionFingerprintSerializer(Serializer<HistoricalFileCollectionFingerprint> fileCollectionFingerprintSerializer) {
        this.fileCollectionFingerprintSerializer = fileCollectionFingerprintSerializer;
        this.inputPropertiesSerializer = new InputPropertiesSerializer();
        this.implementationSnapshotSerializer = new ImplementationSnapshot.SerializerImpl();
    }

    public HistoricalTaskExecution read(Decoder decoder) throws Exception {
        boolean successful = decoder.readBoolean();

        OriginTaskExecutionMetadata originExecutionMetadata = new OriginTaskExecutionMetadata(
            UniqueId.from(decoder.readString()),
            decoder.readLong()
        );

        ImmutableSortedMap<String, HistoricalFileCollectionFingerprint> inputFilesFingerprints = readFingerprints(decoder);
        ImmutableSortedMap<String, HistoricalFileCollectionFingerprint> outputFilesFingerprints = readFingerprints(decoder);

        ImplementationSnapshot taskImplementation = implementationSnapshotSerializer.read(decoder);

        // We can't use an immutable list here because some hashes can be null
        int taskActionsCount = decoder.readSmallInt();
        ImmutableList.Builder<ImplementationSnapshot> taskActionImplementationsBuilder = ImmutableList.builder();
        for (int j = 0; j < taskActionsCount; j++) {
            ImplementationSnapshot actionImpl = implementationSnapshotSerializer.read(decoder);
            taskActionImplementationsBuilder.add(actionImpl);
        }
        ImmutableList<ImplementationSnapshot> taskActionImplementations = taskActionImplementationsBuilder.build();

        int cacheableOutputPropertiesCount = decoder.readSmallInt();
        ImmutableSortedSet.Builder<String> cacheableOutputPropertiesBuilder = ImmutableSortedSet.naturalOrder();
        for (int j = 0; j < cacheableOutputPropertiesCount; j++) {
            cacheableOutputPropertiesBuilder.add(decoder.readString());
        }
        ImmutableSortedSet<String> cacheableOutputProperties = cacheableOutputPropertiesBuilder.build();

        ImmutableSortedMap<String, ValueSnapshot> inputProperties = inputPropertiesSerializer.read(decoder);

        return new HistoricalTaskExecution(
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            cacheableOutputProperties,
            inputFilesFingerprints,
            outputFilesFingerprints,
            successful,
            originExecutionMetadata
        );
    }

    public void write(Encoder encoder, HistoricalTaskExecution execution) throws Exception {
        encoder.writeBoolean(execution.isSuccessful());
        encoder.writeString(execution.getOriginExecutionMetadata().getBuildInvocationId().asString());
        encoder.writeLong(execution.getOriginExecutionMetadata().getExecutionTime());
        writeFingerprints(encoder, execution.getInputFingerprints());
        writeFingerprints(encoder, execution.getOutputFingerprints());
        implementationSnapshotSerializer.write(encoder, execution.getTaskImplementation());
        encoder.writeSmallInt(execution.getTaskActionImplementations().size());
        for (ImplementationSnapshot actionImpl : execution.getTaskActionImplementations()) {
            implementationSnapshotSerializer.write(encoder, actionImpl);
        }
        encoder.writeSmallInt(execution.getOutputPropertyNamesForCacheKey().size());
        for (String outputFile : execution.getOutputPropertyNamesForCacheKey()) {
            encoder.writeString(outputFile);
        }
        inputPropertiesSerializer.write(encoder, execution.getInputProperties());
    }

    private ImmutableSortedMap<String, HistoricalFileCollectionFingerprint> readFingerprints(Decoder decoder) throws Exception {
        int count = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, HistoricalFileCollectionFingerprint> builder = ImmutableSortedMap.naturalOrder();
        for (int fingerprintIdx = 0; fingerprintIdx < count; fingerprintIdx++) {
            String property = decoder.readString();
            HistoricalFileCollectionFingerprint fingerprint = fileCollectionFingerprintSerializer.read(decoder);
            builder.put(property, fingerprint);
        }
        return builder.build();
    }

    private void writeFingerprints(Encoder encoder, Map<String, HistoricalFileCollectionFingerprint> fingerprints) throws Exception {
        encoder.writeSmallInt(fingerprints.size());
        for (Map.Entry<String, HistoricalFileCollectionFingerprint> entry : fingerprints.entrySet()) {
            encoder.writeString(entry.getKey());
            fileCollectionFingerprintSerializer.write(encoder, entry.getValue());
        }
    }
}
