/*
 * Copyright 2017 the original author or authors.
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
import com.google.common.collect.Lists;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.internal.nativeintegration.filesystem.FileMetadataSnapshot;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;

import java.io.File;
import java.util.List;

import static org.gradle.internal.nativeintegration.filesystem.FileType.*;

public class DefaultFileSystemSnapshotter implements FileSystemSnapshotter {
    private final FileHasher hasher;
    private final StringInterner stringInterner;
    private final FileSystem fileSystem;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileSystemMirror fileSystemMirror;

    public DefaultFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileSystemMirror = fileSystemMirror;
    }

    @Override
    public FileSnapshot snapshotFile(File file) {
        FileSnapshot snapshot = fileSystemMirror.getFile(file.getAbsolutePath());
        if (snapshot == null) {
            snapshot = calculateDetails(file);
            fileSystemMirror.putFile(snapshot);
        }
        return snapshot;
    }

    @Override
    public FileTreeSnapshot snapshotDirectoryTree(File dir) {
        FileTreeSnapshot snapshot = fileSystemMirror.getDirectoryTree(dir.getAbsolutePath());
        if (snapshot == null) {
            // Scan the directory
            snapshot = doSnapshot(directoryFileTreeFactory.create(dir));
            fileSystemMirror.putDirectory(snapshot);
        }
        return snapshot;
    }

    @Override
    public FileTreeSnapshot snapshotDirectoryTree(DirectoryFileTree dirTree) {
        // Currently cache only those trees where we want everything from a directory
        if (!dirTree.getPatterns().isEmpty()) {
            List<FileSnapshot> elements = Lists.newArrayList();
            dirTree.visit(new FileVisitorImpl(elements));
            return new DirectoryTreeDetails(dirTree.getDir().getAbsolutePath(), elements);
        }

        FileTreeSnapshot snapshot = fileSystemMirror.getDirectoryTree(dirTree.getDir().getAbsolutePath());
        if (snapshot == null) {
            // Scan the directory
            snapshot = doSnapshot(dirTree);
            fileSystemMirror.putDirectory(snapshot);
        }
        return snapshot;
    }

    @Override
    public List<FileSnapshot> snapshotTree(FileTreeInternal tree) {
        List<FileSnapshot> elements = Lists.newArrayList();
        tree.visitTreeOrBackingFile(new FileVisitorImpl(elements));
        return elements;
    }

    private FileTreeSnapshot doSnapshot(DirectoryFileTree directoryTree) {
        String path = getPath(directoryTree.getDir());
        List<FileSnapshot> elements = Lists.newArrayList();
        directoryTree.visit(new FileVisitorImpl(elements));
        return new DirectoryTreeDetails(path, ImmutableList.copyOf(elements));
    }

    private String getPath(File file) {
        return stringInterner.intern(file.getAbsolutePath());
    }

    private FileSnapshot calculateDetails(File file) {
        String path = getPath(file);
        FileMetadataSnapshot stat = fileSystem.stat(file);
        switch (stat.getType()) {
            case Missing:
                return new DefaultFileSnapshot(path, new RelativePath(true, file.getName()), Missing, true, MissingFileSnapshot.getInstance());
            case Directory:
                return new DefaultFileSnapshot(path, new RelativePath(false, file.getName()), Directory, true, DirSnapshot.getInstance());
            case RegularFile:
                return new DefaultFileSnapshot(path, new RelativePath(true, file.getName()), RegularFile, true, fileSnapshot(file, stat));
            default:
                throw new IllegalArgumentException("Unrecognized file type: " + stat.getType());
        }
    }

    private FileHashSnapshot fileSnapshot(FileTreeElement fileDetails) {
        return new FileHashSnapshot(hasher.hash(fileDetails), fileDetails.getLastModified());
    }

    private FileHashSnapshot fileSnapshot(File file, FileMetadataSnapshot fileDetails) {
        return new FileHashSnapshot(hasher.hash(file, fileDetails), fileDetails.getLastModified());
    }

    private class FileVisitorImpl implements FileVisitor {
        private final List<FileSnapshot> fileTreeElements;

        FileVisitorImpl(List<FileSnapshot> fileTreeElements) {
            this.fileTreeElements = fileTreeElements;
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            fileTreeElements.add(new DefaultFileSnapshot(getPath(dirDetails.getFile()), dirDetails.getRelativePath(), Directory, false, DirSnapshot.getInstance()));
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            fileTreeElements.add(new DefaultFileSnapshot(getPath(fileDetails.getFile()), fileDetails.getRelativePath(), RegularFile, false, fileSnapshot(fileDetails)));
        }
    }
}
