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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.Map;

public class TaskExecutionSnapshotSerializer extends AbstractSerializer<HistoricalTaskExecution> {
    private final InputPropertiesSerializer inputPropertiesSerializer;
    private final StringInterner stringInterner;
    private final Serializer<FileCollectionSnapshot> fileCollectionSnapshotSerializer;

    TaskExecutionSnapshotSerializer(StringInterner stringInterner, Serializer<FileCollectionSnapshot> fileCollectionSnapshotSerializer) {
        this.fileCollectionSnapshotSerializer = fileCollectionSnapshotSerializer;
        this.inputPropertiesSerializer = new InputPropertiesSerializer();
        this.stringInterner = stringInterner;
    }

    public HistoricalTaskExecution read(Decoder decoder) throws Exception {
        boolean successful = decoder.readBoolean();

        UniqueId buildId = UniqueId.from(decoder.readString());

        ImmutableSortedMap<String, FileCollectionSnapshot> inputFilesSnapshots = readSnapshots(decoder);
        ImmutableSortedMap<String, FileCollectionSnapshot> outputFilesSnapshots = readSnapshots(decoder);
        FileCollectionSnapshot discoveredFilesSnapshot = fileCollectionSnapshotSerializer.read(decoder);

        ImplementationSnapshot taskImplementation = readImplementation(decoder);

        // We can't use an immutable list here because some hashes can be null
        int taskActionsCount = decoder.readSmallInt();
        ImmutableList.Builder<ImplementationSnapshot> taskActionImplementationsBuilder = ImmutableList.builder();
        for (int j = 0; j < taskActionsCount; j++) {
            ImplementationSnapshot actionImpl = readImplementation(decoder);
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
            buildId,
            taskImplementation,
            taskActionImplementations,
            inputProperties,
            cacheableOutputProperties,
            inputFilesSnapshots,
            discoveredFilesSnapshot,
            outputFilesSnapshots,
            successful
        );
    }

    public void write(Encoder encoder, HistoricalTaskExecution execution) throws Exception {
        encoder.writeBoolean(execution.isSuccessful());
        encoder.writeString(execution.getBuildInvocationId().asString());
        writeSnapshots(encoder, execution.getInputFilesSnapshot());
        writeSnapshots(encoder, execution.getOutputFilesSnapshot());
        fileCollectionSnapshotSerializer.write(encoder, execution.getDiscoveredInputFilesSnapshot());
        writeImplementation(encoder, execution.getTaskImplementation());
        encoder.writeSmallInt(execution.getTaskActionImplementations().size());
        for (ImplementationSnapshot actionImpl : execution.getTaskActionImplementations()) {
            writeImplementation(encoder, actionImpl);
        }
        encoder.writeSmallInt(execution.getOutputPropertyNamesForCacheKey().size());
        for (String outputFile : execution.getOutputPropertyNamesForCacheKey()) {
            encoder.writeString(outputFile);
        }
        inputPropertiesSerializer.write(encoder, execution.getInputProperties());
    }

    private static ImplementationSnapshot readImplementation(Decoder decoder) throws IOException {
        String typeName = decoder.readString();
        HashCode classLoaderHash = decoder.readBoolean() ? HashCode.fromBytes(decoder.readBinary()) : null;
        return new ImplementationSnapshot(typeName, classLoaderHash);
    }

    private static void writeImplementation(Encoder encoder, ImplementationSnapshot implementation) throws IOException {
        encoder.writeString(implementation.getTypeName());
        if (implementation.hasUnknownClassLoader()) {
            encoder.writeBoolean(false);
        } else {
            encoder.writeBoolean(true);
            encoder.writeBinary(implementation.getClassLoaderHash().toByteArray());
        }
    }

    private ImmutableSortedMap<String, FileCollectionSnapshot> readSnapshots(Decoder decoder) throws Exception {
        int count = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, FileCollectionSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
            String property = decoder.readString();
            FileCollectionSnapshot snapshot = fileCollectionSnapshotSerializer.read(decoder);
            builder.put(property, snapshot);
        }
        return builder.build();
    }

    private void writeSnapshots(Encoder encoder, Map<String, FileCollectionSnapshot> ids) throws Exception {
        encoder.writeSmallInt(ids.size());
        for (Map.Entry<String, FileCollectionSnapshot> entry : ids.entrySet()) {
            encoder.writeString(entry.getKey());
            fileCollectionSnapshotSerializer.write(encoder, entry.getValue());
        }
    }
}
