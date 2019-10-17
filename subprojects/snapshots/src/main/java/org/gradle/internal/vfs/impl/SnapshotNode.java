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

package org.gradle.internal.vfs.impl;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

class SnapshotNode extends AbstractNode {
    private final FileSystemLocationSnapshot snapshot;

    SnapshotNode(String prefix, FileSystemLocationSnapshot snapshot) {
        super(prefix);
        this.snapshot = snapshot;
    }

    @Override
    public Optional<Node> invalidate(String path) {
        return handlePrefix(getPrefix(), path, new DescendantHandler<Optional<Node>>() {
            @Override
            public Optional<Node> handleDescendant() {
                if (snapshot.getType() != FileType.Directory) {
                    return Optional.empty();
                }
                DirectorySnapshot directorySnapshot = (DirectorySnapshot) snapshot;
                int startNextSegment = getPrefix().length() + 1;
                List<Node> merged = new ArrayList<>(directorySnapshot.getChildren().size());
                boolean matched = false;
                for (FileSystemLocationSnapshot child : directorySnapshot.getChildren()) {
                    SnapshotNode childNode = new SnapshotNode(child.getName(), child);
                    if (!matched && sizeOfCommonPrefix(child.getName(), path, startNextSegment) > 0) {
                        // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                        childNode.invalidate(path.substring(startNextSegment))
                            .ifPresent(merged::add);
                        matched = true;
                    } else {
                        merged.add(childNode);
                    }
                }
                return merged.isEmpty() ? Optional.empty() : Optional.of(new NodeWithChildren(getPrefix(), merged));
            }

            @Override
            public Optional<Node> handleParent() {
                return Optional.empty();
            }

            @Override
            public Optional<Node> handleSame() {
                return Optional.empty();
            }

            @Override
            public Optional<Node> handleDifferent(int commonPrefixLength) {
                return Optional.of(SnapshotNode.this);
            }
        });
    }

    @Override
    public Node update(String path, FileSystemLocationSnapshot newSnapshot) {
        return handlePrefix(getPrefix(), path, new DescendantHandler<Node>() {
            @Override
            public Node handleDescendant() {
                int startNextSegment = getPrefix().length() + 1;
                if (snapshot.getType() != FileType.Directory) {
                    return new SnapshotNode(path, newSnapshot);
                }
                DirectorySnapshot directorySnapshot = (DirectorySnapshot) snapshot;
                List<Node> merged = new ArrayList<>(directorySnapshot.getChildren().size() + 1);
                boolean matched = false;
                for (FileSystemLocationSnapshot child : directorySnapshot.getChildren()) {
                    SnapshotNode childNode = new SnapshotNode(child.getName(), child);
                    if (sizeOfCommonPrefix(child.getName(), path, startNextSegment) > 0) {
                        // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                        merged.add(childNode.update(path.substring(startNextSegment), newSnapshot));
                        matched = true;
                    } else {
                        merged.add(childNode);
                    }
                }
                if (!matched) {
                    merged.add(new SnapshotNode(path.substring(startNextSegment), newSnapshot));
                }
                return new NodeWithChildren(getPrefix(), merged);
            }

            @Override
            public Node handleParent() {
                return new SnapshotNode(path, newSnapshot);
            }

            @Override
            public Node handleSame() {
                return snapshot.getHash().equals(newSnapshot.getHash())
                    ? SnapshotNode.this
                    : new SnapshotNode(path, newSnapshot);
            }

            @Override
            public Node handleDifferent(int commonPrefixLength) {
                String commonPrefix = getPrefix().substring(0, commonPrefixLength);
                Node newThis = new SnapshotNode(getPrefix().substring(commonPrefixLength + 1), snapshot);
                Node sibling = new SnapshotNode(path.substring(commonPrefixLength + 1), newSnapshot);
                return new NodeWithChildren(commonPrefix, ImmutableList.of(newThis, sibling));
            }
        });
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String filePath, int offset) {
        if (!isChildOfOrThis(filePath, offset, getPrefix())) {
            return Optional.empty();
        }
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
