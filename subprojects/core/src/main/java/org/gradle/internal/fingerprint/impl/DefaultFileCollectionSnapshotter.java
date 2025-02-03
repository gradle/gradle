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
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.snapshot.CompositeFileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.RelativePathTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private final FileSystemAccess fileSystemAccess;
    private final Stat stat;
    private final BuildOperationRunner buildOperationRunner;

    public DefaultFileCollectionSnapshotter(FileSystemAccess fileSystemAccess, Stat stat, BuildOperationRunner buildOperationRunner) {
        this.fileSystemAccess = fileSystemAccess;
        this.stat = stat;
        this.buildOperationRunner = buildOperationRunner;
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
//            roots.add(new FileSystemSnapshot() {
//                @Override
//                public Stream<FileSystemLocationSnapshot> roots() {
//                    return delegate.roots();
//                }
//
//                @Override
//                public SnapshotVisitResult accept(FileSystemSnapshotHierarchyVisitor visitor) {
//                    return operation(() -> delegate.accept(visitor), "accept(FileSystemSnapshotHierarchyVisitor)");
//                }
//
//                @Override
//                public SnapshotVisitResult accept(RelativePathTracker pathTracker, RelativePathTrackingFileSystemSnapshotHierarchyVisitor visitor) {
//                    return operation(() -> delegate.accept(pathTracker, visitor), "accept(RelativePathTracker, RelativePathTrackingFileSystemSnapshotHierarchyVisitor)");
//                }
//
//                private <T> T operation(Callable<T> callable, String type) {
//                    return buildOperationRunner.call(new CallableBuildOperation<T>() {
//                        @Override
//                        public T call(BuildOperationContext context) throws Exception {
//                            return callable.call();
//                        }
//
//                        @Override
//                        public BuildOperationDescriptor.Builder description() {
//                            return BuildOperationDescriptor.displayName("Fingerprinting " + file.getName() + " " + type);
//                        }
//                    });
//                }
//            });
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
            fileSystemAccess.read(
                root.getAbsolutePath(),
                new PatternSetSnapshottingFilter(patterns, stat)
            )
                .map(roots::add);
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
