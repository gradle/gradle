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
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
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

public class NonePathFileCollectionSnapshot extends RootHoldingFileCollectionSnapshot {
    private static final Comparator<IncrementalFileSnapshotWithAbsolutePath> ENTRY_COMPARATOR = new Comparator<IncrementalFileSnapshotWithAbsolutePath>() {
        @Override
        public int compare(IncrementalFileSnapshotWithAbsolutePath o1, IncrementalFileSnapshotWithAbsolutePath o2) {
            return o1.getSnapshot().getContentMd5().compareTo(o2.getSnapshot().getContentMd5());
        }
    };
    private static final Comparator<FileContentSnapshot> CONTENT_COMPARATOR = new Comparator<FileContentSnapshot>() {
        @Override
        public int compare(FileContentSnapshot o1, FileContentSnapshot o2) {
            return o1.getContentMd5().compareTo(o2.getContentMd5());
        }
    };

    public NonePathFileCollectionSnapshot(ListMultimap<String, LogicalSnapshot> roots) {
        super(roots);
    }

    public NonePathFileCollectionSnapshot(Map<String, FileContentSnapshot> snapshots, @Nullable HashCode hashCode) {
        super(hashCode);
        this.snapshots = snapshots;
    }

    private Map<String, FileContentSnapshot> snapshots;

    @Override
    public boolean visitChangesSince(FileCollectionSnapshot oldSnapshot, String propertyTitle, boolean includeAdded, TaskStateChangeVisitor visitor) {
        Map<String, FileContentSnapshot> previous = oldSnapshot.getContentSnapshots();
        Map<String, FileContentSnapshot> current = getContentSnapshots();
        ListMultimap<FileContentSnapshot, IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousSnapshots = MultimapBuilder.hashKeys(previous.size()).linkedListValues().build();
        for (Map.Entry<String, FileContentSnapshot> entry : previous.entrySet()) {
            String absolutePath = entry.getKey();
            FileContentSnapshot previousSnapshot = entry.getValue();
            unaccountedForPreviousSnapshots.put(previousSnapshot, new IncrementalFileSnapshotWithAbsolutePath(absolutePath, previousSnapshot));
        }

        for (Map.Entry<String, FileContentSnapshot> entry : current.entrySet()) {
            String currentAbsolutePath = entry.getKey();
            FileContentSnapshot currentSnapshot = entry.getValue();
            List<IncrementalFileSnapshotWithAbsolutePath> previousSnapshotsForContent = unaccountedForPreviousSnapshots.get(currentSnapshot);
            if (previousSnapshotsForContent.isEmpty()) {
                if (includeAdded) {
                    if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentSnapshot.getType()))) {
                        return false;
                    }
                }
            } else {
                previousSnapshotsForContent.remove(0);
            }
        }

        List<IncrementalFileSnapshotWithAbsolutePath> unaccountedForPreviousEntries = Lists.newArrayList(unaccountedForPreviousSnapshots.values());
        Collections.sort(unaccountedForPreviousEntries, ENTRY_COMPARATOR);
        for (IncrementalFileSnapshotWithAbsolutePath unaccountedForPreviousSnapshotEntry : unaccountedForPreviousEntries) {
            if (!visitor.visitChange(FileChange.removed(unaccountedForPreviousSnapshotEntry.getAbsolutePath(), propertyTitle, unaccountedForPreviousSnapshotEntry.getSnapshot().getType()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doGetHash(DefaultBuildCacheHasher hasher) {
        List<FileContentSnapshot> normalizedSnapshots = Lists.newArrayList(getContentSnapshots().values());
        Collections.sort(normalizedSnapshots, CONTENT_COMPARATOR);
        for (FileContentSnapshot normalizedSnapshot : normalizedSnapshots) {
            hasher.putHash(normalizedSnapshot.getContentMd5());
        }
    }

    @Override
    public Collection<File> getElements() {
        throw new UnsupportedOperationException("Only supported for outputs");
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        throw new UnsupportedOperationException("Cannot get snapshots with none path sensitivity!");
    }

    private Map<String, FileContentSnapshot> doGetContentSnapshots() {
        Preconditions.checkState(snapshots != null || getRoots() != null, "If no roots are given the snapshots must be present.");
        final ImmutableSortedMap.Builder<String, FileContentSnapshot> builder = ImmutableSortedMap.naturalOrder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (Map.Entry<String, LogicalSnapshot> entry : getRoots().entries()) {
            final String basePath = entry.getKey();
            final int rootIndex = basePath.length() + 1;
            entry.getValue().accept(new HierarchicalSnapshotVisitor() {
                private Deque<String> absolutePaths = new LinkedList<String>();

                @Override
                public void preVisitDirectory(String name) {
                    String absolutePath = getAbsolutePath(name);
                    absolutePaths.addLast(absolutePath);
                }

                @Override
                public void visit(String name, FileContentSnapshot content) {
                    String absolutePath = getAbsolutePath(name);
                    if (processedEntries.add(absolutePath)) {
                        builder.put(absolutePath, content);
                    }
                }

                private String getAbsolutePath(String name) {
                    String parent = absolutePaths.peekLast();
                    return parent == null ? basePath : childPath(parent, name);
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
        if (snapshots == null) {
            snapshots = doGetContentSnapshots();
        }
        return snapshots;
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

    public static class SerializerImpl implements Serializer<NonePathFileCollectionSnapshot> {

        private final HashCodeSerializer hashCodeSerializer;
        private final AbsolutePathSnapshotSerializer snapshotSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.hashCodeSerializer = new HashCodeSerializer();
            this.snapshotSerializer = new AbsolutePathSnapshotSerializer(stringInterner);
        }

        @Override
        public NonePathFileCollectionSnapshot read(Decoder decoder) throws IOException {
            int type = decoder.readSmallInt();
            Preconditions.checkState(type == 3);
            boolean hasHash = decoder.readBoolean();
            HashCode hash = hasHash ? hashCodeSerializer.read(decoder) : null;
            Map<String, FileContentSnapshot> snapshots = snapshotSerializer.read(decoder);
            return new NonePathFileCollectionSnapshot(snapshots, hash);
        }

        @Override
        public void write(Encoder encoder, NonePathFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(3);
            encoder.writeBoolean(value.hasHash());
            if (value.hasHash()) {
                hashCodeSerializer.write(encoder, value.getHash());
            }
            snapshotSerializer.write(encoder, value.getContentSnapshots());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            NonePathFileCollectionSnapshot.SerializerImpl rhs = (NonePathFileCollectionSnapshot.SerializerImpl) obj;
            return Objects.equal(snapshotSerializer, rhs.snapshotSerializer)
                && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotSerializer, hashCodeSerializer);
        }
    }
}
