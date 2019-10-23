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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.file.FileType;
import org.gradle.internal.vfs.impl.AbstractFileSystemNode;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SnapshotFileSystemNode extends AbstractFileSystemNode {
    private final FileSystemLocationSnapshot snapshot;

    public SnapshotFileSystemNode(String prefix, FileSystemLocationSnapshot snapshot) {
        super(prefix);
        this.snapshot = snapshot;
    }

    @Override
    public Optional<FileSystemNode> invalidate(String path) {
        return handlePrefix(getPrefix(), path, new InvalidateHandler() {
            @Override
            public Optional<FileSystemNode> handleDescendant() {
                if (snapshot.getType() != FileType.Directory) {
                    return Optional.empty();
                }
                DirectorySnapshot directorySnapshot = (DirectorySnapshot) snapshot;
                int startNextSegment = getPrefix().length() + 1;
                List<FileSystemNode> merged = new ArrayList<>(directorySnapshot.getChildren().size());
                boolean matched = false;
                for (FileSystemLocationSnapshot child : directorySnapshot.getChildren()) {
                    SnapshotFileSystemNode childNode = new SnapshotFileSystemNode(child.getName(), child);
                    if (!matched && sizeOfCommonPrefix(child.getName(), path, startNextSegment) > 0) {
                        // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                        childNode.invalidate(path.substring(startNextSegment))
                            .ifPresent(merged::add);
                        matched = true;
                    } else {
                        merged.add(childNode);
                    }
                }
                return merged.isEmpty() ? Optional.empty() : Optional.of(new FileSystemNodeWithChildren(getPrefix(), merged));
            }
        });
    }

    @Override
    public FileSystemNode update(String path, FileSystemLocationSnapshot newSnapshot) {
        return handlePrefix(getPrefix(), path, new DescendantHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleDescendant() {
                return SnapshotFileSystemNode.this;
            }

            @Override
            public FileSystemNode handleParent() {
                return new SnapshotFileSystemNode(path, newSnapshot);
            }

            @Override
            public FileSystemNode handleSame() {
                return SnapshotFileSystemNode.this;
            }

            @Override
            public FileSystemNode handleDifferent(int commonPrefixLength) {
                String commonPrefix = getPrefix().substring(0, commonPrefixLength);
                FileSystemNode newThis = new SnapshotFileSystemNode(getPrefix().substring(commonPrefixLength + 1), snapshot);
                FileSystemNode sibling = new SnapshotFileSystemNode(path.substring(commonPrefixLength + 1), newSnapshot);
                ImmutableList<FileSystemNode> newChildren = pathComparator().compare(newThis.getPrefix(), sibling.getPrefix()) < 0
                    ? ImmutableList.of(newThis, sibling)
                    : ImmutableList.of(sibling, newThis);
                return new FileSystemNodeWithChildren(commonPrefix, newChildren);
            }
        });
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String filePath, int offset) {
        int endOfThisSegment = offset + getPrefix().length();
        if (filePath.length() == endOfThisSegment) {
            return Optional.of(snapshot);
        }
        return findSnapshot(snapshot, filePath, endOfThisSegment + 1);
    }

    private Optional<FileSystemLocationSnapshot> findSnapshot(FileSystemLocationSnapshot snapshot, String filePath, int offset) {
        switch (snapshot.getType()) {
            case RegularFile:
            case Missing:
                return Optional.of(new MissingFileSnapshot(filePath, getFileNameForAbsolutePath(filePath)));
            case Directory:
                return findPathInDirectorySnapshot((DirectorySnapshot) snapshot, filePath, offset);
            default:
                throw new AssertionError("Unknown file type: " + snapshot.getType());
        }
    }

    private Optional<FileSystemLocationSnapshot> findPathInDirectorySnapshot(DirectorySnapshot snapshot, String filePath, int offset) {
        for (FileSystemLocationSnapshot child : snapshot.getChildren()) {
            if (isChildOfOrThis(filePath, offset, child.getName())) {
                int endOfThisSegment = child.getName().length() + offset;
                if (endOfThisSegment == filePath.length()) {
                    return Optional.of(child);
                }
                return findSnapshot(child, filePath, endOfThisSegment + 1);
            }
        }
        return Optional.of(new MissingFileSnapshot(filePath, getFileNameForAbsolutePath(filePath)));
    }

    private static String getFileNameForAbsolutePath(String filePath) {
        return Paths.get(filePath).getFileName().toString();
    }

    @Override
    public void collect(int depth, List<String> prefixes) {
        if (depth == 0) {
            prefixes.add(getPrefix());
        } else {
            prefixes.add(depth + ":" + getPrefix().replace(File.separatorChar, '/'));
        }
    }
}
