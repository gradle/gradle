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
 * A wrapper that extends the relative path of the snapshot to something longer.
 *
 * It delegates everything to the wrapped snapshot.
 */
public class PathCompressingSnapshotWrapper extends AbstractFileSystemNode implements MetadataSnapshot {
    private final MetadataSnapshot snapshot;

    public PathCompressingSnapshotWrapper(String pathToParent, MetadataSnapshot snapshot) {
        super(pathToParent);
        this.snapshot = snapshot;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String absolutePath, int offset) {
        return snapshot.invalidate(absolutePath, offset).map(splitSnapshot -> splitSnapshot.withPathToParent(getPathToParent()));
    }

    @Override
    public FileSystemNode update(String absolutePath, int offset, MetadataSnapshot newSnapshot) {
        return snapshot.update(absolutePath, offset, newSnapshot).withPathToParent(getPathToParent());
    }

    @Override
    protected Optional<MetadataSnapshot> getMetadata() {
        return Optional.of(snapshot);
    }

    @Override
    protected Optional<MetadataSnapshot> getChildMetadata(String absolutePath, int offset) {
        return snapshot.getSnapshot(absolutePath, offset);
    }

    @Override
    public FileSystemNode withPathToParent(String newPathToParent) {
        return getPathToParent().equals(newPathToParent)
            ? this
            : new PathCompressingSnapshotWrapper(newPathToParent, snapshot);
    }

    @Override
    public FileType getType() {
        return snapshot.getType();
    }
}
