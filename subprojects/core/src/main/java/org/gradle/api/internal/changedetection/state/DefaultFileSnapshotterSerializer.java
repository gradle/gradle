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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class DefaultFileSnapshotterSerializer implements Serializer<FileCollectionSnapshotImpl> {
    private final StringInterner stringInterner;
    private final TreeSnapshotRepository treeSnapshotRepository;
    private final IncrementalFileSnapshotSerializer incrementalFileSnapshotSerializer = new IncrementalFileSnapshotSerializer();

    public DefaultFileSnapshotterSerializer(StringInterner stringInterner, TreeSnapshotRepository treeSnapshotRepository) {
        this.stringInterner = stringInterner;
        this.treeSnapshotRepository = treeSnapshotRepository;
    }

    public FileCollectionSnapshotImpl read(Decoder decoder) throws Exception {
        List<TreeSnapshot> treeSnapshots = new ArrayList<TreeSnapshot>();
        int sharedTreeCount = decoder.readSmallInt();
        for (int i = 0; i < sharedTreeCount; i++) {
            long treeId = decoder.readLong();
            treeSnapshots.add(treeSnapshotRepository.getTreeSnapshot(treeId));
        }
        TreeSnapshot nonShared = TreeSnapshotSerializer.readStoredTreeSnapshot(-1, decoder, incrementalFileSnapshotSerializer, stringInterner);
        if (!nonShared.getFileSnapshots().isEmpty()) {
            treeSnapshots.add(nonShared);
        }
        return new FileCollectionSnapshotImpl(treeSnapshots);
    }

    public void write(Encoder encoder, FileCollectionSnapshotImpl value) throws Exception {
        final List<TreeSnapshot> treeSnapshots = value.treeSnapshots;
        if (treeSnapshots != null) {
            TreeSnapshot nonShared = null;
            for (TreeSnapshot snapshot : treeSnapshots) {
                if (!snapshot.isShareable()) {
                    if (nonShared != null) {
                        throw new RuntimeException("Multiple non-shared snapshots aren't supported.");
                    }
                    nonShared = snapshot;
                }
            }
            encoder.writeSmallInt(treeSnapshots.size() - ((nonShared != null) ? 1 : 0));
            for (TreeSnapshot snapshot : treeSnapshots) {
                if (snapshot.isShareable()) {
                    encoder.writeLong(treeSnapshotRepository.maybeStoreTreeSnapshot(snapshot));
                }
            }
            if (nonShared != null) {
                TreeSnapshotSerializer.writeTreeSnapshot(nonShared, encoder, incrementalFileSnapshotSerializer);
            } else {
                encoder.writeSmallInt(0);
            }
        } else {
            encoder.writeSmallInt(0);
            encoder.writeSmallInt(value.snapshots.size());
            for (Map.Entry<String, IncrementalFileSnapshot> entry : value.snapshots.entrySet()) {
                encoder.writeString(entry.getKey());
                incrementalFileSnapshotSerializer.write(encoder, entry.getValue());
            }
        }
    }

}
