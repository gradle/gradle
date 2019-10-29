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

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * A wrapper that extends the relative path of the snapshot to something longer.
 *
 * It delegates everything to the wrapped snapshot.
 */
public class PathCompressingSnapshotWrapper extends AbstractFileSystemNode {
    private final MetadataSnapshot snapshot;

    public PathCompressingSnapshotWrapper(String prefix, MetadataSnapshot snapshot) {
        super(prefix);
        this.snapshot = snapshot;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String absolutePath, int offset) {
        return snapshot.invalidate(absolutePath, offset).map(splitSnapshot -> splitSnapshot.withPrefix(getPrefix()));
    }

    @Override
    public FileSystemNode update(String absolutePath, int offset, MetadataSnapshot newSnapshot) {
        return snapshot.update(absolutePath, offset, newSnapshot).withPrefix(getPrefix());
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(String absolutePath, int offset) {
        return FileSystemNode.thisOrGet(
            snapshot, absolutePath, offset,
            () -> snapshot.getSnapshot(absolutePath, offset)
        );
    }

    @Override
    public void collect(int depth, List<String> prefixes) {
        if (depth == 0) {
            prefixes.add(getPrefix());
        } else {
            prefixes.add(depth + ":" + getPrefix().replace(File.separatorChar, '/'));
        }
    }

    @Override
    public FileSystemNode withPrefix(String newPrefix) {
        return new PathCompressingSnapshotWrapper(newPrefix, snapshot);
    }
}
