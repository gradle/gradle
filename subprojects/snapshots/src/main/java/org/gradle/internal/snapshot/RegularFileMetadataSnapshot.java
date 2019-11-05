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

import org.gradle.internal.file.FileType;

import java.util.Optional;

/**
 * An incomplete snapshot of an existing file.
 *
 * The content hash is unknown.
 */
public class RegularFileMetadataSnapshot extends AbstractFileSystemNode implements MetadataSnapshot {

    public RegularFileMetadataSnapshot(String pathToParent) {
        super(pathToParent);
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    @Override
    protected Optional<MetadataSnapshot> getMetadata() {
        return Optional.of(this);
    }

    @Override
    protected Optional<MetadataSnapshot> getChildMetadata(String absolutePath, int offset) {
        return Optional.of(SnapshotUtil.missingSnapshotForAbsolutePath(absolutePath));
    }

    @Override
    public FileSystemNode store(String absolutePath, int offset, MetadataSnapshot snapshot) {
        return this;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String absolutePath, int offset) {
        return Optional.empty();
    }

    @Override
    public FileSystemNode withPathToParent(String newPathToParent) {
        return getPathToParent().equals(newPathToParent)
            ? this
            : new RegularFileMetadataSnapshot(newPathToParent);
    }
}
