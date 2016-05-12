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
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.List;

class DefaultVisitedTree implements VisitedTree {
    private final boolean shareable;
    private final long nextId;
    private final List<FileTreeElement> entries;
    private final Collection<File> missingFiles;
    private TreeSnapshot treeSnapshot;

    public DefaultVisitedTree(List<FileTreeElement> entries, boolean shareable, long nextId, Collection<File> missingFiles) {
        this.shareable = shareable;
        this.nextId = nextId;
        this.entries = entries;
        this.missingFiles = missingFiles;
    }

    @Override
    public Collection<FileTreeElement> getEntries() {
        return entries;
    }

    @Override
    public List<FileTreeElement> filter(PatternSet patternSet) {
        ImmutableList.Builder<FileTreeElement> filtered = ImmutableList.builder();
        final Spec<FileTreeElement> spec = patternSet.getAsSpec();
        for (FileTreeElement element : entries) {
            if (spec.isSatisfiedBy(element)) {
                filtered.add(element);
            }
        }
        return filtered.build();
    }

    @Override
    public synchronized TreeSnapshot maybeCreateSnapshot(final FileSnapshotter fileSnapshotter, final StringInterner stringInterner) {
        if (treeSnapshot == null) {
            treeSnapshot = createTreeSnapshot(fileSnapshotter, stringInterner);
        }
        return treeSnapshot;
    }

    private TreeSnapshot createTreeSnapshot(final FileSnapshotter fileSnapshotter, final StringInterner stringInterner) {
        final Collection<FileSnapshotWithKey> fileSnapshots = CollectionUtils.collect(getEntries(), new Transformer<FileSnapshotWithKey, FileTreeElement>() {
            @Override
            public FileSnapshotWithKey transform(FileTreeElement fileTreeElement) {
                String absolutePath = getInternedAbsolutePath(fileTreeElement.getFile(), stringInterner);
                IncrementalFileSnapshot incrementalFileSnapshot;
                if (fileTreeElement.isDirectory()) {
                    incrementalFileSnapshot = DirSnapshot.getInstance();
                } else {
                    incrementalFileSnapshot = new FileHashSnapshot(fileSnapshotter.snapshot(fileTreeElement).getHash(), fileTreeElement.getLastModified());
                }
                return new FileSnapshotWithKey(absolutePath, incrementalFileSnapshot);
            }
        });
        if (missingFiles != null) {
            for (File file : missingFiles) {
                fileSnapshots.add(new FileSnapshotWithKey(getInternedAbsolutePath(file, stringInterner), MissingFileSnapshot.getInstance()));
            }
        }
        return new DefaultTreeSnapshot(fileSnapshots, shareable, nextId);
    }

    private String getInternedAbsolutePath(File file, StringInterner stringInterner) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    @Override
    public boolean isShareable() {
        return shareable;
    }

    private static class DefaultTreeSnapshot implements TreeSnapshot {
        private final Collection<FileSnapshotWithKey> fileSnapshots;
        private final boolean shareable;
        private final long nextId;
        private Long assignedId;

        public DefaultTreeSnapshot(Collection<FileSnapshotWithKey> fileSnapshots, boolean shareable, long nextId) {
            this.fileSnapshots = fileSnapshots;
            this.shareable = shareable;
            this.nextId = nextId;
        }

        @Override
        public boolean isShareable() {
            return shareable;
        }

        @Override
        public Collection<FileSnapshotWithKey> getFileSnapshots() {
            return fileSnapshots;
        }

        @Override
        public Long getAssignedId() {
            return assignedId;
        }

        @Override
        public synchronized Long maybeStoreEntry(Action<Long> storeEntryAction) {
            if (assignedId == null) {
                assignedId = nextId;
                storeEntryAction.execute(assignedId);
            }
            return assignedId;
        }
    }
}
