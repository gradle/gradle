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

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.cache.CacheAccess;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

abstract class AbstractFileCollectionSnapshotter implements FileCollectionSnapshotter {
    protected final FileSnapshotter snapshotter;
    protected final StringInterner stringInterner;
    protected final FileResolver fileResolver;
    private final VisitedTreesPreCheckHasher visitedTreesPreCheckHasher = new VisitedTreesPreCheckHasher();
    protected CacheAccess cacheAccess;

    public AbstractFileCollectionSnapshotter(FileSnapshotter snapshotter, CacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
        this.fileResolver = fileResolver;
    }

    public FileCollectionSnapshot emptySnapshot() {
        return new FileCollectionSnapshotImpl(Collections.<String, IncrementalFileSnapshot>emptyMap());
    }

    public FileCollectionSnapshot.PreCheck preCheck(final FileCollection files, final boolean allowReuse) {
        return new DefaultFileCollectionSnapshotPreCheck(files, allowReuse);
    }

    public FileCollectionSnapshot snapshot(final FileCollectionSnapshot.PreCheck preCheck) {
        if (preCheck.isEmpty()) {
            return emptySnapshot();
        }

        final List<TreeSnapshot> treeSnapshots = new ArrayList<TreeSnapshot>();
        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                final List<VisitedTree> nonShareableTrees = new ArrayList<VisitedTree>();
                for (VisitedTree tree : preCheck.getVisitedTrees()) {
                    if (tree.isShareable()) {
                        treeSnapshots.add(tree.maybeCreateSnapshot(snapshotter, stringInterner));
                    } else {
                        nonShareableTrees.add(tree);
                    }
                }
                if (!nonShareableTrees.isEmpty() || !preCheck.getMissingFiles().isEmpty()) {
                    VisitedTree nonShareableTree = createJoinedTree(nonShareableTrees, preCheck.getMissingFiles());
                    treeSnapshots.add(nonShareableTree.maybeCreateSnapshot(snapshotter, stringInterner));
                }
            }
        });
        return new FileCollectionSnapshotImpl(treeSnapshots);
    }

    private Collection<FileSnapshotWithKey> createMissingFileSnapshots(Collection<File> missingFiles) {
        List<FileSnapshotWithKey> missingFileSnapshots = new ArrayList<FileSnapshotWithKey>();
        for (File missingFile : missingFiles) {
            missingFileSnapshots.add(new FileSnapshotWithKey(getInternedAbsolutePath(missingFile), MissingFileSnapshot.getInstance()));
        }
        return missingFileSnapshots;
    }

    abstract VisitedTree createJoinedTree(List<VisitedTree> nonShareableTrees, Collection<File> missingFiles);

    private String getInternedAbsolutePath(File file) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    abstract protected void visitFiles(FileCollection input, List<VisitedTree> visitedTrees, List<File> missingFiles, boolean allowReuse);

    private final class DefaultFileCollectionSnapshotPreCheck implements FileCollectionSnapshot.PreCheck {
        private final List<VisitedTree> visitedTrees;
        private final List<File> missingFiles;
        private final FileCollection files;
        private Integer hash;

        public DefaultFileCollectionSnapshotPreCheck(FileCollection files, boolean allowReuse) {
            this.files = files;
            visitedTrees = Lists.newLinkedList();
            missingFiles = Lists.newArrayList();
            visitFiles(files, visitedTrees, missingFiles, allowReuse);
        }

        @Override
        public Integer getHash() {
            if (hash == null) {
                hash = visitedTreesPreCheckHasher.calculatePreCheckHash(visitedTrees);
            }
            return hash;
        }

        @Override
        public FileCollection getFiles() {
            return files;
        }

        @Override
        public Collection<VisitedTree> getVisitedTrees() {
            return visitedTrees;
        }

        @Override
        public Collection<File> getMissingFiles() {
            return missingFiles;
        }

        @Override
        public boolean isEmpty() {
            for (VisitedTree tree : visitedTrees) {
                if (!tree.getEntries().isEmpty()) {
                    return false;
                }
            }
            return missingFiles.isEmpty();
        }
    }
}
