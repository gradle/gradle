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

package org.gradle.internal.file.impl;

import org.gradle.internal.file.FileMetadataSnapshot;
import org.gradle.internal.file.FileType;

public class DefaultFileMetadata implements FileMetadataSnapshot {
    private static final FileMetadataSnapshot DIR = new DefaultFileMetadata(FileType.Directory, 0, 0, false);
    private static final FileMetadataSnapshot SYMLINKED_DIR = new DefaultFileMetadata(FileType.Directory, 0, 0, true);
    private static final FileMetadataSnapshot MISSING = new DefaultFileMetadata(FileType.Missing, 0, 0, false);
    private static final FileMetadataSnapshot BROKEN_SYMLINK = new DefaultFileMetadata(FileType.Missing, 0, 0, true);
    private final FileType type;
    private final long lastModified;
    private final long length;
    private final boolean isSymlink;

    public DefaultFileMetadata(FileType type, long lastModified, long length, boolean isSymlink) {
        this.type = type;
        this.lastModified = lastModified;
        this.length = length;
        this.isSymlink = isSymlink;
    }

    public static FileMetadataSnapshot file(long lastModified, long length) {
        return new DefaultFileMetadata(FileType.RegularFile, lastModified, length, false);
    }

    public static FileMetadataSnapshot symlinkedFile(long lastModified, long length) {
        return new DefaultFileMetadata(FileType.RegularFile, lastModified, length, true);
    }

    public static FileMetadataSnapshot directory() {
        return DIR;
    }

    public static FileMetadataSnapshot missing() {
        return MISSING;
    }

    public static FileMetadataSnapshot brokenSymlink() {
        return BROKEN_SYMLINK;
    }

    public static FileMetadataSnapshot symlinkedDirectory() {
        return SYMLINKED_DIR;
    }

    @Override
    public FileType getType() {
        return type;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public long getLength() {
        return length;
    }

    @Override
    public boolean isSymlink() {
        return isSymlink;
    }
}
