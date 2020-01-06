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

package org.gradle.internal.snapshot;

import org.gradle.internal.file.FileMetadataSnapshot;

import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

public class FileMetadata {

    public static FileMetadata from(BasicFileAttributes attributes) {
        return new FileMetadata(attributes.size(), attributes.lastModifiedTime().toMillis());
    }

    public static FileMetadata from(FileMetadataSnapshot metadataSnapshot) {
        return new FileMetadata(metadataSnapshot.getLength(), metadataSnapshot.getLastModified());
    }

    private final long size;
    private final long lastModified;

    public FileMetadata(long size, long lastModified) {
        this.size = size;
        this.lastModified = lastModified;
    }

    public long getSize() {
        return size;
    }

    public long getLastModified() {
        return lastModified;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FileMetadata that = (FileMetadata) o;
        return size == that.size && lastModified == that.lastModified;
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, lastModified);
    }
}
