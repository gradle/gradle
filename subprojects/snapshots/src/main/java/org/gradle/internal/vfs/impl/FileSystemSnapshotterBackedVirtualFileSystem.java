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

import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror;
import org.gradle.internal.vfs.VirtualFileSystem;

import java.io.File;

public class FileSystemSnapshotterBackedVirtualFileSystem implements VirtualFileSystem {

    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final DefaultFileSystemMirror fileSystemMirror;

    public FileSystemSnapshotterBackedVirtualFileSystem(FileSystemSnapshotter fileSystemSnapshotter, DefaultFileSystemMirror fileSystemMirror) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.fileSystemMirror = fileSystemMirror;
    }

    @Override
    public void read(String location, FileSystemSnapshotVisitor visitor) {
        fileSystemSnapshotter.snapshot(new File(location)).accept(visitor);
    }

    @Override
    public void read(String location, SnapshottingFilter filter, FileSystemSnapshotVisitor visitor) {
        fileSystemSnapshotter.snapshotDirectoryTree(new File(location), filter).accept(visitor);
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        fileSystemMirror.beforeOutputChange();
        action.run();
    }
}
