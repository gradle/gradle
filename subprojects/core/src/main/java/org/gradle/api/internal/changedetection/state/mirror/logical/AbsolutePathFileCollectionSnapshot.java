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
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.rules.FileChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChangeVisitor;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.EmptyFileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * File collection snapshot with absolute paths.
 */
public class AbsolutePathFileCollectionSnapshot extends RootHoldingFileCollectionSnapshot {
    private static final Comparator<Map.Entry<String, FileContentSnapshot>> BY_STRING_KEY = new Comparator<Map.Entry<String, FileContentSnapshot>>() {

        @Override
        public int compare(Map.Entry<String, FileContentSnapshot> o1, Map.Entry<String, FileContentSnapshot> o2) {
            return o1.getKey().compareTo(o2.getKey());
        }
    };

    private final boolean includeMissingFiles;
    private Map<String, FileContentSnapshot> content;
    private final Factory<List<File>> cachedElementsFactory = Factories.softReferenceCache(new Factory<List<File>>() {
        @Override
        public List<File> create() {
            return doGetElements();
        }
    });

    public AbsolutePathFileCollectionSnapshot(ListMultimap<String, LogicalSnapshot> roots, boolean includeMissingFiles) {
        super(roots);
        this.includeMissingFiles = includeMissingFiles;
    }

    public AbsolutePathFileCollectionSnapshot(Map<String, FileContentSnapshot> content, @Nullable HashCode hashCode) {
        super(hashCode);
        this.content = content;
        this.includeMissingFiles = false;
    }

    @Override
    public boolean visitChangesSince(FileCollectionSnapshot oldSnapshot, String propertyTitle, boolean includeAdded, TaskStateChangeVisitor visitor) {
        Map<String, FileContentSnapshot> current = getContentSnapshots();
        Map<String, FileContentSnapshot> previous = (oldSnapshot == EmptyFileCollectionSnapshot.INSTANCE) ? ImmutableMap.<String, FileContentSnapshot>of() : ((AbsolutePathFileCollectionSnapshot) oldSnapshot).getContentSnapshots();
        Set<String> unaccountedForPreviousSnapshots = new LinkedHashSet<String>(previous.keySet());

        for (Map.Entry<String, FileContentSnapshot> currentEntry : current.entrySet()) {
            String currentAbsolutePath = currentEntry.getKey();
            FileContentSnapshot currentSnapshot = currentEntry.getValue();
            if (unaccountedForPreviousSnapshots.remove(currentAbsolutePath)) {
                FileContentSnapshot previousSnapshot = previous.get(currentAbsolutePath);
                if (!currentSnapshot.isContentUpToDate(previousSnapshot)) {
                    if (!visitor.visitChange(FileChange.modified(currentAbsolutePath, propertyTitle, previousSnapshot.getType(), currentSnapshot.getType()))) {
                        return false;
                    }
                }
                // else, unchanged; check next file
            } else if (includeAdded) {
                if (!visitor.visitChange(FileChange.added(currentAbsolutePath, propertyTitle, currentSnapshot.getType()))) {
                    return false;
                }
            }
        }

        for (String previousAbsolutePath : unaccountedForPreviousSnapshots) {
            if (!visitor.visitChange(FileChange.removed(previousAbsolutePath, propertyTitle, previous.get(previousAbsolutePath).getType()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doGetHash(DefaultBuildCacheHasher hasher) {
        List<Map.Entry<String, FileContentSnapshot>> entries = Lists.newArrayList(getContentSnapshots().entrySet());
        Collections.sort(entries, BY_STRING_KEY);
        for (Map.Entry<String, FileContentSnapshot> entry : entries) {
            hasher.putString(entry.getKey());
            hasher.putHash(entry.getValue().getContentMd5());
        }
    }

    @Override
    public Collection<File> getElements() {
        return cachedElementsFactory.create();
    }

    private List<File> doGetElements() {
        Map<String, FileContentSnapshot> content = getContentSnapshots();
        List<File> files = Lists.newArrayListWithCapacity(content.size());
        for (String name : content.keySet()) {
            files.add(new File(name));
        }
        return files;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> getSnapshots() {
        throw new UnsupportedOperationException("Getting snapshots is not supported!");
    }

    @Override
    public Map<String, FileContentSnapshot> getContentSnapshots() {
        Preconditions.checkState(content != null || getRoots() != null, "If no roots are given the content must be present.");
        if (content == null) {
            content = doGetContentSnapshots();
        }
        return content;
    }

    private Map<String, FileContentSnapshot> doGetContentSnapshots() {
        final ImmutableMap.Builder<String, FileContentSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (Map.Entry<String, LogicalSnapshot> entry : getRoots().entries()) {
            entry.getValue().accept(new HierarchicalSnapshotVisitor() {

                @Override
                public void preVisitDirectory(String path, String name) {
                    if (processedEntries.add(path)) {
                        builder.put(path, DirContentSnapshot.INSTANCE);
                    }
                }

                @Override
                public void visit(String path, String name, FileContentSnapshot content) {
                    if (!includeMissingFiles && content.getType() == FileType.Missing) {
                        return;
                    }
                    if (processedEntries.add(path)) {
                        builder.put(path, content);
                    }
                }

                @Override
                public void postVisitDirectory() {
                }

            });
        }
        return builder.build();
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putHash(getHash());
    }

    public static class SerializerImpl extends AbstractSerializer<AbsolutePathFileCollectionSnapshot> {

        private final AbsolutePathSnapshotSerializer snapshotSerializer;
        private final HashCodeSerializer hashCodeSerializer;

        public SerializerImpl(StringInterner stringInterner) {
            this.snapshotSerializer = new AbsolutePathSnapshotSerializer(stringInterner);
            this.hashCodeSerializer = new HashCodeSerializer();
        }

        public AbsolutePathFileCollectionSnapshot read(Decoder decoder) throws Exception {
            int type = decoder.readSmallInt();
            Preconditions.checkState(type == 1);
            boolean hasHash = decoder.readBoolean();
            HashCode hash = hasHash ? hashCodeSerializer.read(decoder) : null;
            Map<String, FileContentSnapshot> snapshots = snapshotSerializer.read(decoder);
            return new AbsolutePathFileCollectionSnapshot(snapshots, hash);
        }

        public void write(Encoder encoder, AbsolutePathFileCollectionSnapshot value) throws Exception {
            encoder.writeSmallInt(1);
            boolean hasHash = value.hasHash();
            encoder.writeBoolean(hasHash);
            if (hasHash) {
                hashCodeSerializer.write(encoder, value.getHash());
            }
            snapshotSerializer.write(encoder, value.getContentSnapshots());
        }

        @Override
        public boolean equals(Object obj) {
            if (!super.equals(obj)) {
                return false;
            }

            AbsolutePathFileCollectionSnapshot.SerializerImpl rhs = (AbsolutePathFileCollectionSnapshot.SerializerImpl) obj;
            return Objects.equal(snapshotSerializer, rhs.snapshotSerializer)
                && Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(super.hashCode(), snapshotSerializer, hashCodeSerializer);
        }
    }
}
