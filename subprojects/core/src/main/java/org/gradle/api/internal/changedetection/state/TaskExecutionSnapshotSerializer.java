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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.Map;

public class TaskExecutionSnapshotSerializer extends AbstractSerializer<TaskExecutionSnapshot> {
    private final InputPropertiesSerializer inputPropertiesSerializer;
    private final StringInterner stringInterner;

    TaskExecutionSnapshotSerializer(StringInterner stringInterner) {
        this.inputPropertiesSerializer = new InputPropertiesSerializer();
        this.stringInterner = stringInterner;
    }

    public TaskExecutionSnapshot read(Decoder decoder) throws Exception {
        boolean successful = decoder.readBoolean();

        UniqueId buildId = UniqueId.from(decoder.readString());

        ImmutableSortedMap<String, Long> inputFilesSnapshotIds = readSnapshotIds(decoder);
        ImmutableSortedMap<String, Long> outputFilesSnapshotIds = readSnapshotIds(decoder);
        Long discoveredFilesSnapshotId = decoder.readLong();

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

        int outputFilesCount = decoder.readSmallInt();
        ImmutableSet.Builder<String> declaredOutputFilePathsBuilder = ImmutableSet.builder();
        for (int j = 0; j < outputFilesCount; j++) {
            declaredOutputFilePathsBuilder.add(stringInterner.intern(decoder.readString()));
        }
        ImmutableSet<String> declaredOutputFilePaths = declaredOutputFilePathsBuilder.build();

        ImmutableSortedMap<String, ValueSnapshot> inputProperties = inputPropertiesSerializer.read(decoder);

        return new TaskExecutionSnapshot(
            successful,
            buildId,
            taskImplementation,
            taskActionImplementations,
            cacheableOutputProperties,
            declaredOutputFilePaths,
            inputProperties,
            inputFilesSnapshotIds,
            discoveredFilesSnapshotId,
            outputFilesSnapshotIds
        );
    }

    public void write(Encoder encoder, TaskExecutionSnapshot execution) throws Exception {
        encoder.writeBoolean(execution.isSuccessful());
        encoder.writeString(execution.getBuildInvocationId().asString());
        writeSnapshotIds(encoder, execution.getInputFilesSnapshotIds());
        writeSnapshotIds(encoder, execution.getOutputFilesSnapshotIds());
        encoder.writeLong(execution.getDiscoveredFilesSnapshotId());
        writeImplementation(encoder, execution.getTaskImplementation());
        encoder.writeSmallInt(execution.getTaskActionsImplementations().size());
        for (ImplementationSnapshot actionImpl : execution.getTaskActionsImplementations()) {
            writeImplementation(encoder, actionImpl);
        }
        encoder.writeSmallInt(execution.getCacheableOutputProperties().size());
        for (String outputFile : execution.getCacheableOutputProperties()) {
            encoder.writeString(outputFile);
        }
        encoder.writeSmallInt(execution.getDeclaredOutputFilePaths().size());
        for (String outputFile : execution.getDeclaredOutputFilePaths()) {
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
            encoder.writeBinary(implementation.getClassLoaderHash().asBytes());
        }
    }

    private static ImmutableSortedMap<String, Long> readSnapshotIds(Decoder decoder) throws IOException {
        int count = decoder.readSmallInt();
        ImmutableSortedMap.Builder<String, Long> builder = ImmutableSortedMap.naturalOrder();
        for (int snapshotIdx = 0; snapshotIdx < count; snapshotIdx++) {
            String property = decoder.readString();
            long id = decoder.readLong();
            builder.put(property, id);
        }
        return builder.build();
    }

    private static void writeSnapshotIds(Encoder encoder, Map<String, Long> ids) throws IOException {
        encoder.writeSmallInt(ids.size());
        for (Map.Entry<String, Long> entry : ids.entrySet()) {
            encoder.writeString(entry.getKey());
            encoder.writeLong(entry.getValue());
        }
    }
}
