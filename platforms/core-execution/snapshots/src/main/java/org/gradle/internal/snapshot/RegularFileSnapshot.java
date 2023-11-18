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

import com.google.common.collect.Interner;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;

import java.util.Optional;

/**
 * A snapshot of a regular file.
 *
 * The snapshot includes the content hash of the file and its metadata.
 */
public class RegularFileSnapshot extends AbstractFileSystemLocationSnapshot implements FileSystemLeafSnapshot {
    private final HashCode contentHash;
    private final FileMetadata metadata;

    public RegularFileSnapshot(String absolutePath, String name, HashCode contentHash, FileMetadata metadata) {
        super(absolutePath, name, metadata.getAccessType());
        this.contentHash = contentHash;
        this.metadata = metadata;
    }

    @Override
    protected RegularFileSnapshot doRelocate(String targetPath, String name, Interner<String> interner) {
        return new RegularFileSnapshot(targetPath, name, contentHash, metadata);
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
    public boolean isContentAndMetadataUpToDate(FileSystemLocationSnapshot other) {
        return isContentUpToDate(other) && metadata.equals(((RegularFileSnapshot) other).metadata);
    }

    @Override
    public boolean isContentUpToDate(FileSystemLocationSnapshot other) {
        if (!(other instanceof RegularFileSnapshot)) {
            return false;
        }
        return contentHash.equals(((RegularFileSnapshot) other).contentHash);
    }

    @Override
    public void accept(FileSystemLocationSnapshotVisitor visitor) {
        visitor.visitRegularFile(this);
    }

    @Override
    public <T> T accept(FileSystemLocationSnapshotTransformer<T> transformer) {
        return transformer.visitRegularFile(this);
    }

    @Override
    public Optional<FileSystemNode> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        diffListener.nodeRemoved(this);
        return Optional.empty();
    }

    @Override
    public String toString() {
        return String.format("%s@%s/%s", super.toString(), getHash(), getName());
    }
}
