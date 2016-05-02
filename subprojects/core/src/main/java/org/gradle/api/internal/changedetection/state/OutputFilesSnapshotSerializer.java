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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;

import java.util.HashMap;
import java.util.Map;

class OutputFilesSnapshotSerializer implements Serializer<OutputFilesCollectionSnapshotter.OutputFilesSnapshot> {
    private final Serializer<FileCollectionSnapshot> serializer;
    private final StringInterner stringInterner;

    public OutputFilesSnapshotSerializer(Serializer<FileCollectionSnapshot> serializer, StringInterner stringInterner) {
        this.serializer = serializer;
        this.stringInterner = stringInterner;
    }

    public OutputFilesCollectionSnapshotter.OutputFilesSnapshot read(Decoder decoder) throws Exception {
        Map<String, Boolean> roots = new HashMap<String, Boolean>();
        int rootFileIdsCount = decoder.readSmallInt();
        for (int i = 0; i < rootFileIdsCount; i++) {
            String key = stringInterner.intern(decoder.readString());
            roots.put(key, decoder.readBoolean());
        }
        FileCollectionSnapshot snapshot = serializer.read(decoder);

        return new OutputFilesCollectionSnapshotter.OutputFilesSnapshot(roots, snapshot);
    }

    public void write(Encoder encoder, OutputFilesCollectionSnapshotter.OutputFilesSnapshot value) throws Exception {
        int roots = value.roots.size();
        encoder.writeSmallInt(roots);
        for (Map.Entry<String, Boolean> entry : value.roots.entrySet()) {
            encoder.writeString(entry.getKey());
            encoder.writeBoolean(entry.getValue());
        }

        serializer.write(encoder, value.filesSnapshot);
    }
}
