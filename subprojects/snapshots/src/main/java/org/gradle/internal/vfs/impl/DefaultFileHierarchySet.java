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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultFileHierarchySet {
    private static final EmptyFileHierarchySet EMPTY = new EmptyFileHierarchySet();

    /**
     * Creates an empty set.
     */
    public static FileHierarchySet of() {
        return EMPTY;
    }

    /**
     * Creates a set containing the given directory and all its descendants
     */
    public static FileHierarchySet of(FileSystemLocationSnapshot snapshot) {
        return new PrefixFileSet(snapshot.getAbsolutePath(), snapshot);
    }

    /**
     * Creates a set containing the given directory and all its descendants
     */
    public static FileHierarchySet of(Iterable<FileSystemLocationSnapshot> snapshots) {
        FileHierarchySet set = EMPTY;
        for (FileSystemLocationSnapshot snapshot : snapshots) {
            set = set.update(snapshot);
        }
        return set;
    }

    private static class EmptyFileHierarchySet implements FileHierarchySet {

        @Nullable
        @Override
        public FileSystemLocationSnapshot getSnapshot(String path) {
            return null;
        }

        @Override
        public FileHierarchySet update(FileSystemLocationSnapshot snapshot) {
            return new PrefixFileSet(snapshot.getAbsolutePath(), snapshot);
        }

        @Override
        public FileHierarchySet invalidate(String path) {
            return this;
        }
    }

    private static class PrefixFileSet implements FileHierarchySet {
        private final Node rootNode;

        PrefixFileSet(String rootDir, FileSystemLocationSnapshot snapshot) {
            String path = toPath(rootDir);
            this.rootNode = new SnapshotNode(path, snapshot);
        }

        PrefixFileSet(Node rootNode) {
            this.rootNode = rootNode;
        }

        @VisibleForTesting
        List<String> flatten() {
            List<String> prefixes = new ArrayList<>();
            rootNode.collect(0, prefixes);
            return prefixes;
        }

        @Nullable
        @Override
        public FileSystemLocationSnapshot getSnapshot(String path) {
            return rootNode.getSnapshot(path, 0);
        }

        @Override
        public FileHierarchySet update(FileSystemLocationSnapshot snapshot) {
            return new PrefixFileSet(rootNode.update(snapshot.getAbsolutePath(), snapshot));
        }

        @Override
        public FileHierarchySet invalidate(String path) {
            return rootNode.invalidate(path)
                .<FileHierarchySet>map(PrefixFileSet::new)
                .orElse(EMPTY);
        }

        private String toPath(String absolutePath) {
            if (absolutePath.equals("/")) {
                absolutePath = "";
            } else if (absolutePath.endsWith(File.separator)) {
                absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
            }
            return absolutePath;
        }
    }

    private interface Node {

        Node update(String path, FileSystemLocationSnapshot snapshot);

        Optional<Node> invalidate(String path);

        int sizeOfCommonPrefix(String path, int offset);

        @Nullable
        FileSystemLocationSnapshot getSnapshot(String filePath, int offset);

        void collect(int depth, List<String> prefixes);

        /**
         * Does not include the file separator.
         */
        static int sizeOfCommonPrefix(String path1, String path2, int offset) {
            return sizeOfCommonPrefix(path1, path2, offset, File.separatorChar);
        }

        /**
         * Does not include the separator char.
         */
        static int sizeOfCommonPrefix(String path1, String path2, int offset, char separatorChar) {
            int pos = 0;
            int lastSeparator = 0;
            int maxPos = Math.min(path1.length(), path2.length() - offset);
            for (; pos < maxPos; pos++) {
                if (path1.charAt(pos) != path2.charAt(pos + offset)) {
                    break;
                }
                if (path1.charAt(pos) == separatorChar) {
                    lastSeparator = pos;
                }
            }
            if (pos == maxPos) {
                if (path1.length() == path2.length() - offset) {
                    return pos;
                }
                if (pos < path1.length() && path1.charAt(pos) == separatorChar) {
                    return pos;
                }
                if (pos < path2.length() - offset && path2.charAt(pos + offset) == separatorChar) {
                    return pos;
                }
            }
            return lastSeparator;
        }

        /**
         * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
         * which does not check for negative indices or integer overflow.
         */
        static boolean isChildOfOrThis(String filePath, int offset, String prefix) {
            int pathLength = filePath.length();
            int prefixLength = prefix.length();
            int endOfThisSegment = prefixLength + offset;
            if (pathLength < endOfThisSegment) {
                return false;
            }
            for (int i = prefixLength - 1, j = endOfThisSegment - 1; i >= 0; i--, j--) {
                if (prefix.charAt(i) != filePath.charAt(j)) {
                    return false;
                }
            }
            return endOfThisSegment == pathLength || filePath.charAt(endOfThisSegment) == File.separatorChar;
        }
    }

    private static class NodeWithChildren implements Node {
        private final String prefix;
        private final List<Node> children;

        public NodeWithChildren(String prefix, List<Node> children) {
            assert !children.isEmpty();
            this.prefix = prefix;
            this.children = children;
        }

        @Override
        public Optional<Node> invalidate(String path) {
            int maxPos = Math.min(prefix.length(), path.length());
            int prefixLen = sizeOfCommonPrefix(path, 0);
            if (prefixLen == maxPos) {
                if (prefix.length() >= path.length()) {
                    return Optional.empty();
                }
                int startNextSegment = prefix.length() + 1;
                List<Node> merged = new ArrayList<>(children.size() + 1);
                boolean matched = false;
                for (Node child : children) {
                    if (!matched && child.sizeOfCommonPrefix(path, startNextSegment) > 0) {
                        // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                        child.invalidate(path.substring(startNextSegment)).ifPresent(merged::add);
                        matched = true;
                    } else {
                        merged.add(child);
                    }
                }
                if (!matched) {
                    return Optional.of(this);
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
                    // Path == prefix
                    return new SnapshotNode(path, snapshot);
                }
                if (prefix.length() < path.length()) {
                    // Path is a descendant of this
                    int startNextSegment = prefix.length() + 1;
                    List<Node> merged = new ArrayList<>(children.size() + 1);
                    boolean matched = false;
                    for (Node child : children) {
                        if (!matched && child.sizeOfCommonPrefix(path, startNextSegment) > 0) {
                            // TODO - we've already calculated the common prefix and calling plus() will calculate it again
                            merged.add(child.update(path.substring(startNextSegment), snapshot));
                            matched = true;
                        } else {
                            merged.add(child);
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
            Node newThis = new NodeWithChildren(prefix.substring(prefixLen + 1), children);
            Node sibling = new SnapshotNode(path.substring(prefixLen + 1), snapshot);
            return new NodeWithChildren(commonPrefix, ImmutableList.of(newThis, sibling));
        }

        @Override
        public int sizeOfCommonPrefix(String path, int offset) {
            return Node.sizeOfCommonPrefix(prefix, path, offset);
        }

        @Nullable
        @Override
        public FileSystemLocationSnapshot getSnapshot(String filePath, int offset) {
            if (!Node.isChildOfOrThis(filePath, offset, prefix)) {
                return null;
            }
            int startNextSegment = offset + prefix.length() + 1;
            for (Node child : children) {
                FileSystemLocationSnapshot childSnapshot = child.getSnapshot(filePath, startNextSegment);
                if (childSnapshot != null) {
                    return childSnapshot;
                }
            }
            return null;
        }

        @Override
        public void collect(int depth, List<String> prefixes) {
            if (depth == 0) {
                prefixes.add(prefix);
            } else {
                prefixes.add(depth + ":" + prefix.replace(File.separatorChar, '/'));
            }
            for (Node child : children) {
                child.collect(depth + 1, prefixes);
            }
        }
    }

    private static class SnapshotNode implements Node {
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

        @Nullable
        @Override
        public FileSystemLocationSnapshot getSnapshot(String filePath, int offset) {
            if (!Node.isChildOfOrThis(filePath, offset, prefix)) {
                return null;
            }
            int endOfThisSegment = prefix.length() + offset;
            if (filePath.length() == endOfThisSegment) {
                return snapshot;
            }
            return findSnapshot(snapshot, filePath, endOfThisSegment + 1);
        }

        private FileSystemLocationSnapshot findSnapshot(FileSystemLocationSnapshot snapshot, String filePath, int offset) {
            switch (snapshot.getType()) {
                case RegularFile:
                case Missing:
                    return new MissingFileSnapshot(filePath, getFileNameForAbsolutePath(filePath));
                case Directory:
                    return findPathInDirectorySnapshot((DirectorySnapshot) snapshot, filePath, offset);
                default:
                    throw new AssertionError("Unknown file type: " + snapshot.getType());
            }
        }

        private FileSystemLocationSnapshot findPathInDirectorySnapshot(DirectorySnapshot snapshot, String filePath, int offset) {
            for (FileSystemLocationSnapshot child : snapshot.getChildren()) {
                if (Node.isChildOfOrThis(filePath, offset, child.getName())) {
                    int endOfThisSegment = child.getName().length() + offset;
                    if (endOfThisSegment == filePath.length()) {
                        return child;
                    }
                    return findSnapshot(child, filePath, endOfThisSegment + 1);
                }
            }
            return new MissingFileSnapshot(filePath, getFileNameForAbsolutePath(filePath));
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
}
