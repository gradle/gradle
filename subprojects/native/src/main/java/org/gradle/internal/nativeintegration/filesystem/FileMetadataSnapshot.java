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

package org.gradle.internal.nativeintegration.filesystem;

public class FileMetadataSnapshot {
    private static final FileMetadataSnapshot DIR = new FileMetadataSnapshot(FileType.Directory, 0, 0);
    private static final FileMetadataSnapshot MISSING = new FileMetadataSnapshot(FileType.Missing, 0, 0);
    private final FileType type;
    private final long lastModified;
    private final long length;

    public FileMetadataSnapshot(FileType type, long lastModified, long length) {
        this.type = type;
        this.lastModified = lastModified;
        this.length = length;
    }

    public static FileMetadataSnapshot directory() {
        return DIR;
    }

    public static FileMetadataSnapshot missing() {
        return MISSING;
    }

    public FileType getType() {
        return type;
    }

    /**
     * Note: always 0 for directories and missing files.
     */
    public long getLastModified() {
        return lastModified;
    }

    /**
     * Note: always 0 for directories and missing files.
     */
    public long getLength() {
        return length;
    }
}
