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
import org.gradle.internal.vfs.impl.AbstractFileSystemNode;

import java.util.List;
import java.util.Optional;

/**
 * A file snapshot which can have children (i.e. a directory).
 */
public class DirectorySnapshot extends AbstractFileSystemLocationSnapshot implements FileSystemLocationSnapshot {
    private final List<FileSystemLocationSnapshot> children;
    private final HashCode contentHash;

    public DirectorySnapshot(String absolutePath, String name, List<FileSystemLocationSnapshot> children, HashCode contentHash) {
        super(absolutePath, name);
        this.children = children;
        this.contentHash = contentHash;
    }

    @Override
    public HashCode getHash() {
        return contentHash;
    }

    @Override
    public FileType getType() {
        return FileType.Directory;
    }

    @Override
    public boolean isContentAndMetadataUpToDate(FileSystemLocationSnapshot other) {
        return other instanceof DirectorySnapshot;
    }

    @Override
    public void accept(FileSystemSnapshotVisitor visitor) {
        if (!visitor.preVisitDirectory(this)) {
            return;
        }
        for (FileSystemLocationSnapshot child : children) {
            child.accept(visitor);
        }
        visitor.postVisitDirectory(this);
    }

    public List<FileSystemLocationSnapshot> getChildren() {
        return children;
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String filePath, int offset) {
        for (FileSystemLocationSnapshot child : getChildren()) {
            if (AbstractFileSystemNode.isChildOfOrThis(filePath, offset, child.getName())) {
                int endOfThisSegment = child.getName().length() + offset;
                if (endOfThisSegment == filePath.length()) {
                    return Optional.of(child);
                }
                return child.getSnapshot(filePath, endOfThisSegment + 1);
            }
        }
        return Optional.of(missingSnapshotForAbsolutePath(filePath));
    }

    @Override
    public FileSystemNode update(String path, FileSystemLocationSnapshot snapshot) {
        return this;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String path) {
        return Optional.empty();
    }
}
