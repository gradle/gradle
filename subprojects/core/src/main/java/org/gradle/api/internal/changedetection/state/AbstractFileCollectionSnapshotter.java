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

    public FileCollectionSnapshot snapshot(final FileCollection input, final boolean allowReuse) {
        final List<VisitedTree> fileTreeElements = Lists.newLinkedList();
        final List<File> missingFiles = Lists.newArrayList();
        visitFiles(input, fileTreeElements, missingFiles, allowReuse);

        if (fileTreeElements.isEmpty() && missingFiles.isEmpty()) {
            return emptySnapshot();
        }

        final List<TreeSnapshot> treeSnapshots = new ArrayList<TreeSnapshot>();
        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                final List<VisitedTree> nonShareableTrees = new ArrayList<VisitedTree>();
                for (VisitedTree tree : fileTreeElements) {
                    if (tree.isShareable()) {
                        treeSnapshots.add(tree.maybeCreateSnapshot(snapshotter, stringInterner));
                    } else {
                        nonShareableTrees.add(tree);
                    }
                }
                if (!nonShareableTrees.isEmpty() || !missingFiles.isEmpty()) {
                    VisitedTree nonShareableTree = createJoinedTree(nonShareableTrees, missingFiles);
                    treeSnapshots.add(nonShareableTree.maybeCreateSnapshot(snapshotter, stringInterner));
                }
            }
        });
        return new FileCollectionSnapshotImpl(treeSnapshots);
    }

    abstract VisitedTree createJoinedTree(List<VisitedTree> nonShareableTrees, Collection<File> missingFiles);

    abstract protected void visitFiles(FileCollection input, List<VisitedTree> visitedTrees, List<File> missingFiles, boolean allowReuse);
}
