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

import org.gradle.api.Action;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStore;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;

import java.io.EOFException;
import java.util.HashSet;
import java.util.Set;

public class TreeSnapshotRepository {
    private final PersistentIndexedCache<Long, TreeSnapshot> treeSnapshotsCache;
    private final PersistentIndexedCache<Long, Set<Long>> treeSnapshotUsageTrackingCache;
    private final PersistentIndexedCache<Long, Set<Long>> fileSnapshotToTreeSnapshotsCache;

    public TreeSnapshotRepository(PersistentStore store, StringInterner stringInterner) {
        this.treeSnapshotsCache = store.createCache("treeSnapshots", Long.class, new TreeSnapshotSerializer(stringInterner));
        this.treeSnapshotUsageTrackingCache = store.createCache("treeSnapshotUsage", Long.class, new SetSerializer<Long>(new LongSerializer()));
        this.fileSnapshotToTreeSnapshotsCache = store.createCache("fileSnapshotsToTreeSnapshotsIndex", Long.class, new SetSerializer<Long>(new LongSerializer()));
    }

    public TreeSnapshot getTreeSnapshot(Long id) {
        return treeSnapshotsCache.get(id);
    }

    public long maybeStoreTreeSnapshot(final TreeSnapshot treeSnapshot) {
        return treeSnapshot.maybeStoreEntry(new Action<Long>() {
            @Override
            public void execute(Long assignedId) {
                treeSnapshotsCache.put(assignedId, treeSnapshot);
            }
        });
    }

    public synchronized void addTreeSnapshotUsage(FileCollectionSnapshot snapshot, long fileCollectionSnapshotId) {
        Set<Long> treeSnapshotIds = new HashSet<Long>(snapshot.getTreeSnapshotIds());
        for (Long treeSnapshotId : treeSnapshotIds) {
            addTreeSnapshotUser(treeSnapshotId, fileCollectionSnapshotId);
        }
        updateFileSnapshotToTreeSnapshotIndex(fileCollectionSnapshotId, treeSnapshotIds);
    }

    private void updateFileSnapshotToTreeSnapshotIndex(long fileCollectionSnapshotId, Set<Long> treeSnapshotIds) {
        Set<Long> currentTreeSnapshotIds = fileSnapshotToTreeSnapshotsCache.get(fileCollectionSnapshotId);
        if (currentTreeSnapshotIds == null || !currentTreeSnapshotIds.equals(treeSnapshotIds)) {
            fileSnapshotToTreeSnapshotsCache.put(fileCollectionSnapshotId, treeSnapshotIds);
        }
    }

    private void addTreeSnapshotUser(Long treeSnapshotId, long fileCollectionSnapshotId) {
        Set<Long> usageTracking = treeSnapshotUsageTrackingCache.get(treeSnapshotId);
        if (usageTracking == null) {
            usageTracking = new HashSet<Long>();
        }
        if (!usageTracking.contains(fileCollectionSnapshotId)) {
            usageTracking.add(fileCollectionSnapshotId);
            treeSnapshotUsageTrackingCache.put(treeSnapshotId, usageTracking);
        }
    }

    public synchronized void removeTreeSnapshotUsage(long fileCollectionSnapshotId) {
        Set<Long> treeSnapshotIds = fileSnapshotToTreeSnapshotsCache.get(fileCollectionSnapshotId);
        fileSnapshotToTreeSnapshotsCache.remove(fileCollectionSnapshotId);
        for (Long treeSnapshotId : treeSnapshotIds) {
            removeTreeSnapshotUsageAndMaybeRemove(fileCollectionSnapshotId, treeSnapshotId);
        }
    }

    private void removeTreeSnapshotUsageAndMaybeRemove(long fileCollectionSnapshotId, Long treeSnapshotId) {
        Set<Long> fileCollectionSnapshotIds = treeSnapshotUsageTrackingCache.get(treeSnapshotId);
        if (fileCollectionSnapshotIds != null && fileCollectionSnapshotIds.contains(fileCollectionSnapshotId)) {
            fileCollectionSnapshotIds.remove(fileCollectionSnapshotId);
            if (fileCollectionSnapshotIds.size() > 0) {
                treeSnapshotUsageTrackingCache.put(treeSnapshotId, fileCollectionSnapshotIds);
            } else {
                // remove TreeSnapshot since there are no users left
                treeSnapshotUsageTrackingCache.remove(treeSnapshotId);
                treeSnapshotsCache.remove(treeSnapshotId);
            }
        }
    }

    private static class LongSerializer implements Serializer<Long> {
        @Override
        public Long read(Decoder decoder) throws EOFException, Exception {
            return decoder.readLong();
        }

        @Override
        public void write(Encoder encoder, Long value) throws Exception {
            encoder.writeLong(value);
        }
    }
}
