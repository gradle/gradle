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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.IgnoredPathFileSnapshot;
import org.gradle.api.internal.changedetection.state.IndexedNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.SnapshotMapSerializer;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RelativePathFileCollectionSnapshot extends RootHoldingFileCollectionSnapshot {
    private static final Comparator<Map.Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>> ENTRY_COMPARATOR = new Comparator<Map.Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>>() {
        @Override
        public int compare(Map.Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> o1, Map.Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    public RelativePathFileCollectionSnapshot(ListMultimap<String, LogicalSnapshot> roots) {
        super(roots);
    }

    public RelativePathFileCollectionSnapshot(Map<String, NormalizedFileSnapshot> snapshots, @Nullable HashCode hashCode) {
        super(hashCode);
        this.snapshots = snapshots;
    }

    private Map<String, NormalizedFileSnapshot> snapshots;

    @Override
    public boolean visitChangesSince(FileCollectionSnapshot oldSnapshot, String propertyTitle, boolean includeAdded, TaskStateChangeVisitor visitor) {
        Map<String, NormalizedFileSnapshot> previous = oldSnapshot.getSnapshots();
        Map<String, NormalizedFileSnapshot> current = getSnapshots();
        ListMultimap<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousSnapshots = MultimapBuilder.hashKeys(previous.size()).linkedListValues().build();
        ListMultimap<String, IncrementalFileSnapshotWithAbsolutePath> addedFiles = MultimapBuilder.hashKeys().linkedListValues().build();
        for (Map.Entry<String, NormalizedFileSnapshot> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            NormalizedFileSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousSnapshots.put(previousSnapshot, new IncrementalFileSnapshotWithAbsolutePath(absolutePath, previousSnapshot.getSnapshot()));
        }

        for (Map.Entry<String, NormalizedFileSnapshot> entry : current.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            NormalizedFileSnapshot currentNormalizedSnapshot = entry.getValue();
            FileContentSnapshot currentSnapshot = currentNormalizedSnapshot.getSnapshot();
            List<IncrementalFileSnapshotWithAbsolutePath> previousSnapshotsForNormalizedPath = unaccountedForPreviousSnapshots.get(currentNormalizedSnapshot);
            if (previousSnapshotsForNormalizedPath.isEmpty()) {
                IncrementalFileSnapshotWithAbsolutePath currentSnapshotWithAbsolutePath = new IncrementalFileSnapshotWithAbsolutePath(currentAbsolutePath, currentSnapshot);
                addedFiles.put(currentNormalizedSnapshot.getNormalizedPath(), currentSnapshotWithAbsolutePath);
            } else {
                IncrementalFileSnapshotWithAbsolutePath previousSnapshotWithAbsolutePath = previousSnapshotsForNormalizedPath.remove(0);
                FileContentSnapshot previousSnapshot = previousSnapshotWithAbsolutePath.getSnapshot();
                if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                    if (!visitor.visitChange(FileChange.modified(currentAbsolutePath, propertyTitle, previousSnapshot.getType(), currentSnapshot.getType()))) {
                        return false;
                    }
                }
            }
        }

        List<Map.Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath>> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousSnapshots.entries());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (Map.Entry<NormalizedFileSnapshot, IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousSnapshotEntry : unaccountedForPreviousEntries) {
            NormalizedFileSnapshot previousSnapshot = unaccountedForPreviousSnapshotEntry.getKey();
            String normalizedPath = previousSnapshot.getNormalizedPath();
            List<IncrementalFileSnapshotWithAbsolutePath> addedFilesForNormalizedPath = addedFiles.get(normalizedPath);
            if (!addedFilesForNormalizedPath.isEmpty()) {
                // There might be multiple files with the same normalized path, here we choose one of them
                IncrementalFileSnapshotWithAbsolutePath modifiedSnapshot = addedFilesForNormalizedPath.remove(0);
                if (!visitor.visitChange(FileChange.modified(modifiedSnapshot.getAbsolutePath(), propertyTitle, previousSnapshot.getSnapshot().getType(), modifiedSnapshot.getSnapshot().getType()))) {
                    return false;
                }
            } else {
                IncrementalFileSnapshotWithAbsolutePath removedSnapshot = unaccountedForPreviousSnapshotEntry.getValue();
                if (!visitor.visitChange(FileChange.removed(removedSnapshot.getAbsolutePath(), propertyTitle, removedSnapshot.getSnapshot().getType()))) {
                    return false;
                }
            }
        }

        if (includeAdded) {
            for (IncrementalFileSnapshotWithAbsolutePath addedFile : addedFiles.values()) {
                if (!visitor.visitChange(FileChange.added(addedFile.getAbsolutePath(), propertyTitle, addedFile.getSnapshot().getType()))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected void doGetHash(DefaultBuildCacheHasher hasher) {
        List<NormalizedFileSnapshot> normalizedSnapshots = Lists.newArrayList(getSnapshots().values());
        Collections.sort(normalizedSnapshots);
        for (NormalizedFileSnapshot normalizedSnapshot : normalizedSnapshots) {
            normalizedSnapshot.appendToHasher(hasher);
        }
    }

    @Override
    public Collection<File> getElements() {
        throw new UnsupportedOperationException("Only supported for outputs");
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        if (snapshots == null) {
            Preconditions.checkState(getRoots() != null, "If no roots are given the snapshots must be provided.");
            snapshots = doGetSnapshots();
        }
        return snapshots;
    }

    private Map<String, NormalizedFileSnapshot> doGetSnapshots() {
        final ImmutableSortedMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableSortedMap.naturalOrder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (Map.Entry<String, LogicalSnapshot> entry : getRoots().entries()) {
            final String basePath = entry.getKey();
            final int rootIndex = basePath.length() + 1;
            entry.getValue().accept(new HierarchicalSnapshotVisitor() {
                private Deque<String> absolutePaths = new LinkedList<String>();

                @Override
                public void preVisitDirectory(String name) {
                    String absolutePath = getAbsolutePath(name);
                    if (processedEntries.add(absolutePath)) {
                        NormalizedFileSnapshot snapshot = isRoot() ? new IgnoredPathFileSnapshot(DirContentSnapshot.INSTANCE) : new IndexedNormalizedFileSnapshot(absolutePath, getIndex(name), DirContentSnapshot.INSTANCE);
                        builder.put(absolutePath, snapshot);
                    }
                    absolutePaths.addLast(absolutePath);
                }

                @Override
                public void visit(String name, FileContentSnapshot content) {
                    String absolutePath = getAbsolutePath(name);
                    if (processedEntries.add(absolutePath)) {
                        builder.put(
                            absolutePath,
                            new IndexedNormalizedFileSnapshot(absolutePath, getIndex(name), content));
                    }
                }

                private String getAbsolutePath(String name) {
                    String parent = absolutePaths.peekLast();
                    return parent == null ? basePath : childPath(parent, name);
                }

                private int getIndex(String name) {
                    return isRoot() ? basePath.length() - name.length() : rootIndex;
                }

                private boolean isRoot() {
                    return absolutePaths.isEmpty();
                }

                @Override
                public void postVisitDirectory() {
                    absolutePaths.removeLast();
                }

                private String childPath(String parent, String name) {
                    return parent + File.separatorChar + name;
                }
            });
        }
        return builder.build();
    }

    @Override
    public Map<String, FileContentSnapshot> getContentSnapshots() {
        throw new UnsupportedOperationException("Only supported for outputs");
    }

    private static class IncrementalFileSnapshotWithAbsolutePath {
        private final String absolutePath;
        private final FileContentSnapshot snapshot;

        public IncrementalFileSnapshotWithAbsolutePath(String absolutePath, FileContentSnapshot snapshot) {
            this.absolutePath = absolutePath;
            this.snapshot = snapshot;
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public FileContentSnapshot getSnapshot() {
            return snapshot;
        }

        @Override
        public String toString() {
            return String.format("%s (%s)", getSnapshot(), absolutePath);
        }
    }

    public static class SerializerImpl implements Serializer<RelativePathFileCollectionSnapshot> {

        private final HashCodeSerializer hashCodeSerializer;
        private final SnapshotMapSerializer snapshotMapSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.hashCodeSerializer = new HashCodeSerializer();
            this.snapshotMapSerializer = new SnapshotMapSerializer(stringInterner);
        }

        @Override
        public RelativePathFileCollectionSnapshot read(Decoder decoder) throws IOException {
            int type = decoder.readSmallInt();
            Preconditions.checkState(type == 2);
            boolean hasHash = decoder.readBoolean();
            HashCode hash = hasHash ? hashCodeSerializer.read(decoder) : null;
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            return new RelativePathFileCollectionSnapshot(snapshots, hash);
        }

        @Override
        public void write(Encoder encoder, RelativePathFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(2);
            encoder.writeBoolean(value.hasHash());
            if (value.hasHash()) {
                hashCodeSerializer.write(encoder, value.getHash());
            }
            snapshotMapSerializer.write(encoder, value.getSnapshots());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            RelativePathFileCollectionSnapshot.SerializerImpl rhs = (RelativePathFileCollectionSnapshot.SerializerImpl) obj;
            return Objects.equal(snapshotMapSerializer, rhs.snapshotMapSerializer)
                && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotMapSerializer, hashCodeSerializer);
        }
    }
}
