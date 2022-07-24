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

import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;

import java.util.Optional;

/**
 * A snapshot of a missing file or a broken symbolic link or a named pipe.
 */
public class MissingFileSnapshot extends AbstractFileSystemLocationSnapshot implements FileSystemLeafSnapshot {
    private static final HashCode SIGNATURE = Hashing.signature(MissingFileSnapshot.class);

    public MissingFileSnapshot(String absolutePath, String name, AccessType accessType) {
        super(absolutePath, name, accessType);
    }

    public MissingFileSnapshot(String absolutePath, AccessType accessType) {
        this(absolutePath, PathUtil.getFileName(absolutePath), accessType);
    }

    @Override
    public FileType getType() {
        return FileType.Missing;
    }

    @Override
    public HashCode getHash() {
        return SIGNATURE;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(FileSystemLocationSnapshot other) {
        return isContentUpToDate(other);
    }

    @Override
    public boolean isContentUpToDate(FileSystemLocationSnapshot other) {
        return other instanceof MissingFileSnapshot;
    }

    @Override
    public void accept(FileSystemLocationSnapshotVisitor visitor) {
        visitor.visitMissing(this);
    }

    @Override
    public <T> T accept(FileSystemLocationSnapshotTransformer<T> transformer) {
        return transformer.visitMissing(this);
    }

    public Optional<FileSystemNode> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        diffListener.nodeRemoved(this);
        return Optional.empty();
    }

    @Override
    public String toString() {
        return String.format("%s/%s", super.toString(), getName());
    }
}
