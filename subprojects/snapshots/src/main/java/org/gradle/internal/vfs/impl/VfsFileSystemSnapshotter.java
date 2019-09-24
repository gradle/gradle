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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotBuilder;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.vfs.VirtualFileSystem;

import javax.annotation.Nullable;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class VfsFileSystemSnapshotter implements FileSystemSnapshotter {

    private final VirtualFileSystem virtualFileSystem;

    public VfsFileSystemSnapshotter(VirtualFileSystem virtualFileSystem) {
        this.virtualFileSystem = virtualFileSystem;
    }

    @Nullable
    @Override
    public HashCode getRegularFileContentHash(File file) {
        AtomicReference<FileSystemLocationSnapshot> snapshotHolder = new AtomicReference<>();
        virtualFileSystem.read(file.getAbsolutePath(), new FileSystemSnapshotVisitor() {
            @Override
            public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                return false;
            }

            @Override
            public void visitFile(FileSystemLocationSnapshot fileSnapshot) {
                snapshotHolder.set(fileSnapshot);
            }

            @Override
            public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
            }
        });


        FileSystemLocationSnapshot snapshot = snapshotHolder.get();
        return (snapshot != null && snapshot.getType() == FileType.RegularFile)
            ? snapshot.getHash()
            : null;
    }

    @Override
    public FileSystemLocationSnapshot snapshot(File file) {
        MerkleDirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.sortingRequired();
        virtualFileSystem.read(file.getAbsolutePath(), builder);
        return builder.getResult();
    }

    @Override
    public FileSystemSnapshot snapshotDirectoryTree(File root, SnapshottingFilter filter) {
        MerkleDirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.sortingRequired();
        virtualFileSystem.read(root.getAbsolutePath(), filter, builder);
        FileSystemLocationSnapshot result = builder.getResult();
        return (result == null || result.getType() == FileType.Missing) ? FileSystemSnapshot.EMPTY : result;
    }

    @Override
    public FileSystemSnapshot snapshotWithBuilder(Consumer<FileSystemSnapshotBuilder> buildAction) {
        throw new UnsupportedOperationException("Arbitrary trees are currently not supported for the Virtual File System");
    }
}
