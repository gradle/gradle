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

package org.gradle.internal.snapshot;

import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.Optional;

/**
 * A complete snapshot of a regular file.
 *
 * The snapshot includes the content hash of the file.
 */
public class RegularFileSnapshot extends AbstractCompleteFileSystemLocationSnapshot {
    private final HashCode contentHash;
    private final FileMetadata metadata;

    public RegularFileSnapshot(String absolutePath, String name, HashCode contentHash, FileMetadata metadata) {
        super(absolutePath, name);
        this.contentHash = contentHash;
        this.metadata = metadata;
    }

    @Override
    public FileType getType() {
        return FileType.RegularFile;
    }

    @Override
    public HashCode getHash() {
        return contentHash;
    }

    // Used by the Maven caching client. Do not remove
    public FileMetadata getMetadata() {
        return metadata;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(CompleteFileSystemLocationSnapshot other) {
        if (!(other instanceof RegularFileSnapshot)) {
            return false;
        }
        RegularFileSnapshot otherSnapshot = (RegularFileSnapshot) other;
        return metadata.equals(otherSnapshot.metadata) && contentHash.equals(otherSnapshot.contentHash);
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        visitor.visitFile(this);
    }

    @Override
    public void accept(NodeVisitor visitor, boolean parentIsComplete) {
        visitor.visitNode(this, !parentIsComplete);
    }

    @Override
    public Optional<FileSystemNode> invalidate(VfsRelativePath relativePath, CaseSensitivity caseSensitivity) {
        return Optional.empty();
    }
}
