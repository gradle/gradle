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

public class DefaultFileMetadataSnapshot implements FileMetadataSnapshot {
    private static final FileMetadataSnapshot DIR = new DefaultFileMetadataSnapshot(FileType.Directory, 0, 0, AccessType.DIRECT);
    private static final FileMetadataSnapshot DIR_ACCESSED_VIA_SYMLINK = new DefaultFileMetadataSnapshot(FileType.Directory, 0, 0, AccessType.VIA_SYMLINK);
    private static final FileMetadataSnapshot MISSING = new DefaultFileMetadataSnapshot(FileType.Missing, 0, 0, AccessType.DIRECT);
    private static final FileMetadataSnapshot BROKEN_SYMLINK = new DefaultFileMetadataSnapshot(FileType.Missing, 0, 0, AccessType.VIA_SYMLINK);
    private final FileType type;
    private final long lastModified;
    private final long length;
    private final AccessType accessType;

    private DefaultFileMetadataSnapshot(FileType type, long lastModified, long length, AccessType accessType) {
        this.type = type;
        this.lastModified = lastModified;
        this.length = length;
        this.accessType = accessType;
    }

    public static FileMetadataSnapshot file(long lastModified, long length, AccessType accessType) {
        return new DefaultFileMetadataSnapshot(FileType.RegularFile, lastModified, length, accessType);
    }

    public static FileMetadataSnapshot directory(AccessType accessType) {
        switch (accessType) {
            case DIRECT:
                return DIR;
            case VIA_SYMLINK:
                return DIR_ACCESSED_VIA_SYMLINK;
            default:
                throw new AssertionError();
        }
    }

    public static FileMetadataSnapshot missing(AccessType accessType) {
        switch (accessType) {
            case DIRECT:
                return MISSING;
            case VIA_SYMLINK:
                return BROKEN_SYMLINK;
            default:
                throw new AssertionError();
        }
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
    public AccessType getAccessType() {
        return accessType;
    }

}
