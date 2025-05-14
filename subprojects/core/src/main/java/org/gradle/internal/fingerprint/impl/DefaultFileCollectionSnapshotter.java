/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileSystemAccess fileSystemAccess;
    private final Stat stat;

    public DefaultFileCollectionSnapshotter(FileSystemAccess fileSystemAccess, Stat stat) {
        this.fileSystemAccess = fileSystemAccess;
        this.stat = stat;
    }

    @Override
    public Result snapshot(FileCollection fileCollection) {
        SnapshottingVisitor visitor = new SnapshottingVisitor();
        ((FileCollectionInternal) fileCollection).visitStructure(visitor);
        FileSystemSnapshot snapshot = CompositeFileSystemSnapshot.of(visitor.getRoots());
        boolean containsArchiveTrees = visitor.containsArchiveTrees();
        return new Result() {
            @Override
            public FileSystemSnapshot getSnapshot() {
                return snapshot;
            }

            @Override
            public boolean containsArchiveTrees() {
                return containsArchiveTrees;
            }
        };
    }

    private class SnapshottingVisitor implements FileCollectionStructureVisitor {
        private final List<FileSystemSnapshot> roots = new ArrayList<>();
        private boolean containsArchiveTrees;

        @Override
        public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
            for (File file : contents) {
                roots.add(fileSystemAccess.read(file.getAbsolutePath()));
            }
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
            fileSystemAccess.read(
                root.getAbsolutePath(),
                new PatternSetSnapshottingFilter(patterns, stat)
            )
                .ifPresent(roots::add);
        }

        @Override
        public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
            roots.add(fileSystemAccess.read(file.getAbsolutePath()));
            containsArchiveTrees = true;
        }

        public List<FileSystemSnapshot> getRoots() {
            return roots;
        }

        public boolean containsArchiveTrees() {
            return containsArchiveTrees;
        }
    }
}
