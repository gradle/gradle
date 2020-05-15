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

package org.gradle.internal.fingerprint.impl;

import com.google.common.base.Preconditions;
import com.google.common.collect.Interner;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.RegularFileSnapshot;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class FileSystemSnapshotBuilder {

    private final Interner<String> stringInterner;
    private final FileHasher fileHasher;
    private DirectoryBuilder rootDirectoryBuilder;
    private String rootPath;
    private String rootName;
    private RegularFileSnapshot rootFileSnapshot;

    public FileSystemSnapshotBuilder(Interner<String> stringInterner, FileHasher fileHasher) {
        this.stringInterner = stringInterner;
        this.fileHasher = fileHasher;
    }

    public void addDir(File dir, String[] segments) {
        checkNoRootFileSnapshot("directory", dir);
        DirectoryBuilder rootBuilder = getOrCreateRootDir(dir, segments);
        rootBuilder.addDir(segments, 0);
    }

    public void addFile(File file, String[] segments, String fileName, FileMetadata metadata) {
        checkNoRootFileSnapshot("another root file", file);
        HashCode contentHash = fileHasher.hash(file, metadata.getLength(), metadata.getLastModified());
        RegularFileSnapshot fileSnapshot = new RegularFileSnapshot(stringInterner.intern(file.getAbsolutePath()), fileName, contentHash, metadata);
        if (segments.length == 0) {
            rootFileSnapshot = fileSnapshot;
        } else {
            DirectoryBuilder rootDir = getOrCreateRootDir(file, segments);
            rootDir.addFile(segments, 0, fileSnapshot);
        }
    }

    private void checkNoRootFileSnapshot(String description, File file) {
        if (rootFileSnapshot != null) {
            throw new IllegalArgumentException(String.format("Cannot add %s '%s' for root file '%s'", description, file, rootFileSnapshot.getAbsolutePath()));
        }
    }

    private DirectoryBuilder getOrCreateRootDir(File dir, String[] segments) {
        if (rootDirectoryBuilder == null) {
            rootDirectoryBuilder = new DirectoryBuilder();
            Path rootDir = dir.toPath();
            for (String ignored : segments) {
                rootDir = rootDir.getParent();
            }
            rootPath = stringInterner.intern(rootDir.toAbsolutePath().toString());
            rootName = stringInterner.intern(rootDir.getFileName().toString());
        }
        return rootDirectoryBuilder;
    }

    public FileSystemSnapshot build() {
        if (rootFileSnapshot != null) {
            return rootFileSnapshot;
        }
        if (rootDirectoryBuilder == null) {
            return FileSystemSnapshot.EMPTY;
        }
        MerkleDirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.sortingRequired();
        builder.preVisitDirectory(rootPath, rootName);
        rootDirectoryBuilder.accept(rootPath, builder);
        builder.postVisitDirectory(determineAccessTypeForLocation(rootPath));
        return Preconditions.checkNotNull(builder.getResult());
    }

    private class DirectoryBuilder {
        private final Map<String, DirectoryBuilder> subDirs = new HashMap<>();
        private final Map<String, RegularFileSnapshot> files = new HashMap<>();

        public void addFile(String[] segments, int offset, RegularFileSnapshot fileSnapshot) {
            if (segments.length == offset) {
                throw new IllegalStateException("A file cannot be in the same place as a directory: " + fileSnapshot.getAbsolutePath());
            }
            String currentSegment = stringInterner.intern(segments[offset]);

            if (segments.length == offset + 1) {
                if (subDirs.containsKey(currentSegment)) {
                    throw new IllegalStateException("A file cannot be added in the same place as a directory: " + fileSnapshot.getAbsolutePath());
                }
                files.put(currentSegment, fileSnapshot);
            } else {
                DirectoryBuilder subDir = getOrCreateSubDir(currentSegment);
                subDir.addFile(segments, offset + 1, fileSnapshot);
            }
        }

        public void addDir(String[] segments, int offset) {
            if (segments.length == offset) {
                return;
            }
            String currentSegment = stringInterner.intern(segments[offset]);
            DirectoryBuilder subDir = getOrCreateSubDir(currentSegment);
            subDir.addDir(segments, offset + 1);
        }

        private DirectoryBuilder getOrCreateSubDir(String currentSegment) {
            if (files.containsKey(currentSegment)) {
                RegularFileSnapshot fileSnapshot = files.get(currentSegment);
                throw new IllegalStateException("A file cannot be added in the same place as a directory:" + fileSnapshot.getAbsolutePath());
            }
            DirectoryBuilder subDir = subDirs.get(currentSegment);
            if (subDir == null) {
                subDir = new DirectoryBuilder();
                subDirs.put(currentSegment, subDir);
            }
            return subDir;
        }

        public void accept(String directoryPath, MerkleDirectorySnapshotBuilder builder) {
            for (Map.Entry<String, DirectoryBuilder> entry : subDirs.entrySet()) {
                String dirName = entry.getKey();
                String dirPath = stringInterner.intern(directoryPath + File.separatorChar + dirName);
                builder.preVisitDirectory(dirPath, dirName);
                entry.getValue().accept(dirPath, builder);
                builder.postVisitDirectory(determineAccessTypeForLocation(dirPath));
            }
            for (RegularFileSnapshot fileSnapshot : files.values()) {
                builder.visitFile(fileSnapshot);
            }
        }
    }

    private static AccessType determineAccessTypeForLocation(String absolutePath) {
        return AccessType.viaSymlink(Files.isSymbolicLink(Paths.get(absolutePath)));
    }
}
