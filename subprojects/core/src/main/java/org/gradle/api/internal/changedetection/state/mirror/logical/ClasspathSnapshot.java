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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
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
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public class ClasspathSnapshot extends RootHoldingFileCollectionSnapshot {

    public ClasspathSnapshot(ListMultimap<String, LogicalSnapshot> roots) {
        super(roots);
    }

    private ClasspathSnapshot(Map<String, NormalizedFileSnapshot> snapshots, @Nullable HashCode hashCode) {
        super(hashCode);
        this.snapshots = snapshots;
    }

    private Map<String, NormalizedFileSnapshot> snapshots;

    @Override
    public boolean visitChangesSince(FileCollectionSnapshot oldSnapshot, String propertyTitle, boolean includeAdded, TaskStateChangeVisitor visitor) {
        Iterator<Map.Entry<String, NormalizedFileSnapshot>> currentEntries = getSnapshots().entrySet().iterator();
        Iterator<Map.Entry<String, NormalizedFileSnapshot>> previousEntries = oldSnapshot.getSnapshots().entrySet().iterator();
        while (true) {
            if (currentEntries.hasNext()) {
                Map.Entry<String, NormalizedFileSnapshot> current = currentEntries.next();
                String currentAbsolutePath = current.getKey();
                if (previousEntries.hasNext()) {
                    Map.Entry<String, NormalizedFileSnapshot> previous = previousEntries.next();
                    NormalizedFileSnapshot currentNormalizedSnapshot = current.getValue();
                    NormalizedFileSnapshot previousNormalizedSnapshot = previous.getValue();
                    String currentNormalizedPath = currentNormalizedSnapshot.getNormalizedPath();
                    String previousNormalizedPath = previousNormalizedSnapshot.getNormalizedPath();
                    if (currentNormalizedPath.equals(previousNormalizedPath)) {
                        if (!currentNormalizedSnapshot.getSnapshot().isContentUpToDate(previousNormalizedSnapshot.getSnapshot())) {
                            if (!visitor.visitChange(
                                FileChange.modified(currentAbsolutePath, propertyTitle,
                                    previousNormalizedSnapshot.getSnapshot().getType(),
                                    currentNormalizedSnapshot.getSnapshot().getType()
                                ))) {
                                return false;
                            }
                        }
                    } else {
                        String previousAbsolutePath = previous.getKey();
                        if (!visitor.visitChange(FileChange.removed(previousAbsolutePath, propertyTitle, previousNormalizedSnapshot.getSnapshot().getType()))) {
                            return false;
                        }
                        if (includeAdded) {
                            if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentNormalizedSnapshot.getSnapshot().getType()))) {
                                return false;
                            }
                        }
                    }
                } else {
                    if (includeAdded) {
                        if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, current.getValue().getSnapshot().getType()))) {
                            return false;
                        }
                    }
                }
            } else {
                if (previousEntries.hasNext()) {
                    Map.Entry<String, NormalizedFileSnapshot> previousEntry = previousEntries.next();
                    if (!visitor.visitChange(FileChange.removed(previousEntry.getKey(), propertyTitle, previousEntry.getValue().getSnapshot().getType()))) {
                        return false;
                    }
                } else {
                    return true;
                }
            }
        }
    }

    @Override
    protected void doGetHash(DefaultBuildCacheHasher hasher) {
        for (NormalizedFileSnapshot normalizedSnapshot : getSnapshots().values()) {
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
        Preconditions.checkNotNull(getRoots());
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (Map.Entry<String, LogicalSnapshot> entry : getRoots().entries()) {
            final String basePath = entry.getKey();
            final int rootIndex = basePath.length() + 1;
            final ImmutableSortedMap.Builder<String, NormalizedFileSnapshot> rootBuilder = ImmutableSortedMap.naturalOrder();
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
                        NormalizedFileSnapshot normalizedFileSnapshot = isRoot() ? new IgnoredPathFileSnapshot(content) : new IndexedNormalizedFileSnapshot(absolutePath, getIndex(name), content);
                        rootBuilder.put(
                            absolutePath,
                            normalizedFileSnapshot);
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
            builder.putAll(rootBuilder.build());
        }
        return builder.build();
    }

    @Override
    public Map<String, FileContentSnapshot> getContentSnapshots() {
        throw new UnsupportedOperationException("Only supported for outputs");
    }

    public static class SerializerImpl implements Serializer<ClasspathSnapshot> {

        private final HashCodeSerializer hashCodeSerializer;
        private final SnapshotMapSerializer snapshotMapSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.hashCodeSerializer = new HashCodeSerializer();
            this.snapshotMapSerializer = new SnapshotMapSerializer(stringInterner);
        }

        @Override
        public ClasspathSnapshot read(Decoder decoder) throws IOException {
            int type = decoder.readSmallInt();
            Preconditions.checkState(type == 2);
            boolean hasHash = decoder.readBoolean();
            HashCode hash = hasHash ? hashCodeSerializer.read(decoder) : null;
            Map<String, NormalizedFileSnapshot> snapshots = snapshotMapSerializer.read(decoder);
            return new ClasspathSnapshot(snapshots, hash);
        }

        @Override
        public void write(Encoder encoder, ClasspathSnapshot value) throws Exception {
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

            ClasspathSnapshot.SerializerImpl rhs = (ClasspathSnapshot.SerializerImpl) obj;
            return Objects.equal(snapshotMapSerializer, rhs.snapshotMapSerializer)
                && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotMapSerializer, hashCodeSerializer);
        }
    }
}
