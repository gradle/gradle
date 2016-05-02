/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.EOFException;

class TreeSnapshotSerializer implements org.gradle.internal.serialize.Serializer<TreeSnapshot> {
    private final IncrementalFileSnapshotSerializer incrementalFileSnapshotSerializer = new IncrementalFileSnapshotSerializer();
    private final StringInterner stringInterner;

    public TreeSnapshotSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    @Override
    public TreeSnapshot read(Decoder decoder) throws EOFException, Exception {
        final long assignedId = decoder.readLong();
        return readStoredTreeSnapshot(assignedId, decoder, incrementalFileSnapshotSerializer, stringInterner);
    }

    @Override
    public void write(Encoder encoder, TreeSnapshot value) throws Exception {
        encoder.writeLong(value.getAssignedId());
        writeTreeSnapshot(value, encoder, incrementalFileSnapshotSerializer);
    }

    static void writeTreeSnapshot(TreeSnapshot treeSnapshot, Encoder encoder, IncrementalFileSnapshotSerializer incrementalFileSnapshotSerializer) throws Exception {
        encoder.writeSmallInt(treeSnapshot.getFileSnapshots().size());
        for (FileSnapshotWithKey fileSnapshotWithKey : treeSnapshot.getFileSnapshots()) {
            encoder.writeString(fileSnapshotWithKey.getKey());
            incrementalFileSnapshotSerializer.write(encoder, fileSnapshotWithKey.getIncrementalFileSnapshot());
        }
    }

    static TreeSnapshot readStoredTreeSnapshot(long assignedId, Decoder decoder, IncrementalFileSnapshotSerializer incrementalFileSnapshotSerializer, StringInterner stringInterner) throws Exception {
        final int entryCount = decoder.readSmallInt();
        ImmutableList.Builder<FileSnapshotWithKey> fileSnapshotWithKeyListBuilder = ImmutableList.builder();
        for (int i = 0; i < entryCount; i++) {
            String key = stringInterner.intern(decoder.readString());
            fileSnapshotWithKeyListBuilder.add(new FileSnapshotWithKey(key, incrementalFileSnapshotSerializer.read(decoder)));
        }
        final ImmutableList<FileSnapshotWithKey> fileSnapshotWithKeyList = fileSnapshotWithKeyListBuilder.build();
        return new StoredTreeSnapshot(fileSnapshotWithKeyList, assignedId);
    }
}
