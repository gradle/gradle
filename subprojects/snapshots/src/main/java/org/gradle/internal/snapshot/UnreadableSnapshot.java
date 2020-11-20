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

import java.io.IOException;
import java.util.Optional;

/**
 * A snapshot of an unreadable file system object (like a named pipe).
 */
public class UnreadableSnapshot extends AbstractCompleteFileSystemLocationSnapshot implements FileSystemLeafSnapshot {
    private static final HashCode SIGNATURE = Hashing.signature(UnreadableSnapshot.class);

    private final IOException error;

    public UnreadableSnapshot(String absolutePath, String name, AccessType accessType, IOException error) {
        super(absolutePath, name, accessType);
        this.error = error;
    }

    public IOException getError() {
        return error;
    }

    @Override
    public FileType getType() {
        throw rethrowOnAttemptedRead();
    }

    @Override
    public HashCode getHash() {
        return SIGNATURE;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(CompleteFileSystemLocationSnapshot other) {
        return other instanceof UnreadableSnapshot;
    }

    @Override
    public boolean isContentUpToDate(CompleteFileSystemLocationSnapshot other) {
        return other instanceof UnreadableSnapshot;
    }

    @Override
    public SnapshotVisitResult accept(FileSystemSnapshotHierarchyVisitor visitor) {
        return visitor.visitEntry(this);
    }

    @Override
    public void accept(FileSystemLocationSnapshotVisitor visitor) {
        visitor.visitUnreadable(this);
    }

    @Override
    public <T> T accept(FileSystemLocationSnapshotTransformer<T> transformer) {
        return transformer.visitUnreadable(this);
    }

    public Optional<FileSystemNode> invalidate(VfsRelativePath targetPath, CaseSensitivity caseSensitivity, SnapshotHierarchy.NodeDiffListener diffListener) {
        diffListener.nodeRemoved(this);
        return Optional.empty();
    }

    public RuntimeException rethrowOnAttemptedRead() {
        throw new RuntimeException(String.format("Couldn't read file contents: '%s'.", getAbsolutePath()), error);
    }
}
