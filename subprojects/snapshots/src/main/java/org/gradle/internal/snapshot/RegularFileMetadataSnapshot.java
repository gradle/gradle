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

import java.io.File;
import java.util.List;
import java.util.Optional;

public class RegularFileMetadataSnapshot extends AbstractFileSystemNode implements MetadataSnapshot {

    public RegularFileMetadataSnapshot(String prefix) {
        super(prefix);
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    @Override
    public Optional<MetadataSnapshot> getSnapshot(String filePath, int offset) {
        return FileSystemNode.thisOrGet(
            this, filePath, offset,
            () -> Optional.of(AbstractFileSystemLocationSnapshot.missingSnapshotForAbsolutePath(filePath)));
    }

    @Override
    public FileSystemNode update(String path, MetadataSnapshot snapshot) {
        return this;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String path) {
        return Optional.empty();
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
        return new RegularFileMetadataSnapshot(newPrefix);
    }
}
