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

class SnapshotNode implements Node {
    private final String prefix;
    private final FileSystemLocationSnapshot snapshot;

    SnapshotNode(String prefix, FileSystemLocationSnapshot snapshot) {
        this.prefix = prefix;
        this.snapshot = snapshot;
    }

    @Override
    public Optional<Node> invalidate(String path) {
        int maxPos = Math.min(prefix.length(), path.length());
        int prefixLen = sizeOfCommonPrefix(path, 0);
        if (prefixLen == maxPos) {
            if (prefix.length() >= path.length()) {
                return Optional.empty();
            }
            if (this.snapshot.getType() != FileType.Directory) {
                return Optional.empty();
            }
            DirectorySnapshot directorySnapshot = (DirectorySnapshot) snapshot;
            int startNextSegment = prefix.length() + 1;
            List<Node> merged = new ArrayList<>(directorySnapshot.getChildren().size());
            boolean matched = false;
            for (FileSystemLocationSnapshot child : directorySnapshot.getChildren()) {
                SnapshotNode childNode = new SnapshotNode(child.getName(), child);
                if (!matched && Node.sizeOfCommonPrefix(child.getName(), path, startNextSegment) > 0) {
                    // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                    childNode.invalidate(path.substring(startNextSegment))
                        .ifPresent(merged::add);
                    matched = true;
                } else {
                    merged.add(childNode);
                }
            }
            return merged.isEmpty() ? Optional.empty() : Optional.of(new NodeWithChildren(prefix, merged));

        }
        return Optional.of(this);
    }

    @Override
    public Node update(String path, FileSystemLocationSnapshot snapshot) {
        int maxPos = Math.min(prefix.length(), path.length());
        int prefixLen = sizeOfCommonPrefix(path, 0);
        if (prefixLen == maxPos) {
            if (prefix.length() == path.length()) {
                return this.snapshot.getHash().equals(snapshot.getHash())
                    ? this
                    : new SnapshotNode(path, snapshot);
            }
            if (prefix.length() < path.length()) {
                int startNextSegment = prefix.length() + 1;
                if (this.snapshot.getType() != FileType.Directory) {
                    return new SnapshotNode(path.substring(startNextSegment), snapshot);
                }
                DirectorySnapshot directorySnapshot = (DirectorySnapshot) this.snapshot;
                List<Node> merged = new ArrayList<>(directorySnapshot.getChildren().size() + 1);
                boolean matched = false;
                for (FileSystemLocationSnapshot child : directorySnapshot.getChildren()) {
                    SnapshotNode childNode = new SnapshotNode(child.getName(), child);
                    if (Node.sizeOfCommonPrefix(child.getName(), path, startNextSegment) > 0) {
                        // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                        merged.add(childNode.update(path.substring(startNextSegment), snapshot));
                        matched = true;
                    } else {
                        merged.add(childNode);
                    }
                }
                if (!matched) {
                    merged.add(new SnapshotNode(path.substring(startNextSegment), snapshot));
                }
                return new NodeWithChildren(prefix, merged);
            } else {
                // Path is an ancestor of this
                return new SnapshotNode(path, snapshot);
            }
        }
        String commonPrefix = prefix.substring(0, prefixLen);
        Node newThis = new SnapshotNode(prefix.substring(prefixLen + 1), this.snapshot);
        Node sibling = new SnapshotNode(path.substring(prefixLen + 1), snapshot);
        return new NodeWithChildren(commonPrefix, ImmutableList.of(newThis, sibling));
    }

    @Override
    public int sizeOfCommonPrefix(String path, int offset) {
        return Node.sizeOfCommonPrefix(prefix, path, offset);
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String filePath, int offset) {
        if (!Node.isChildOfOrThis(filePath, offset, prefix)) {
            return Optional.empty();
        }
        int endOfThisSegment = prefix.length() + offset;
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
            if (Node.isChildOfOrThis(filePath, offset, child.getName())) {
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
            prefixes.add(prefix);
        } else {
            prefixes.add(depth + ":" + prefix.replace(File.separatorChar, '/'));
        }
    }
}
