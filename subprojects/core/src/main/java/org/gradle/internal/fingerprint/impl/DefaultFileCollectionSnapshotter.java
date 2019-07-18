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
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.DefaultFileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotBuilder;
import org.gradle.internal.snapshot.FileSystemSnapshotter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final Stat stat;

    public DefaultFileCollectionSnapshotter(FileSystemSnapshotter fileSystemSnapshotter, Stat stat) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.stat = stat;
    }

    @Override
    public List<FileSystemSnapshot> snapshot(FileCollection fileCollection) {
        FileCollectionLeafVisitorImpl visitor = new FileCollectionLeafVisitorImpl();
        ((FileCollectionInternal) fileCollection).visitLeafCollections(visitor);
        return visitor.getRoots();
    }


    private class FileCollectionLeafVisitorImpl implements FileCollectionLeafVisitor {
        private final List<FileSystemSnapshot> roots = new ArrayList<FileSystemSnapshot>();

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                roots.add(fileSystemSnapshotter.snapshot(file));
            }
        }

        @Override
        public void visitGenericFileTree(FileTreeInternal fileTree) {
            roots.add(snapshotFileTree(fileTree));
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns) {
            roots.add(fileSystemSnapshotter.snapshotDirectoryTree(root, new PatternSetSnapshottingFilter(patterns, stat)));
        }

        public List<FileSystemSnapshot> getRoots() {
            return roots;
        }
    }

    private FileSystemSnapshot snapshotFileTree(final FileTreeInternal tree) {
        final FileSystemSnapshotBuilder builder = fileSystemSnapshotter.newFileSystemSnapshotBuilder();
        tree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                builder.addDir(dirDetails.getFile(), dirDetails.getRelativePath().getSegments());
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                builder.addFile(fileDetails.getFile(), fileDetails.getRelativePath().getSegments(), fileDetails.getName(), new DefaultFileMetadata(FileType.RegularFile, fileDetails.getLastModified(), fileDetails.getSize()));
            }
        });
        return builder.build();
    }
}
