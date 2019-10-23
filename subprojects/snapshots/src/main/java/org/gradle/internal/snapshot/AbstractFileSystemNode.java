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

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public abstract class AbstractFileSystemNode implements FileSystemNode {
    private static final Comparator<String> PATH_COMPARATOR = (path1, path2) -> comparePaths(path1, path2, 0);

    private final String prefix;

    public AbstractFileSystemNode(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return prefix;
    }

    /**
     * Does not include the file separator.
     */
    public static int sizeOfCommonPrefix(String path1, String path2, int offset) {
        return sizeOfCommonPrefix(path1, path2, offset, File.separatorChar);
    }

    /**
     * Does not include the separator char.
     */
    public static int sizeOfCommonPrefix(String path1, String path2, int offset, char separatorChar) {
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

    public static int compareWithCommonPrefix(String path1, String path2, int offset, char separatorChar) {
        int maxPos = Math.min(path1.length(), path2.length() - offset);
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = path1.charAt(pos);
            char charInPath2 = path2.charAt(pos + offset);
            if (charInPath1 != charInPath2) {
                return compareChars(charInPath1, charInPath2, separatorChar);
            }
            if (path1.charAt(pos) == separatorChar) {
                if (pos > 0) {
                    return 0;
                }
            }
        }
        if (path1.length() == path2.length() - offset) {
            return 0;
        }
        if (path1.length() > path2.length() - offset) {
            return path1.charAt(maxPos) == File.separatorChar ? 0 : 1;
        }
        return path2.charAt(maxPos + offset) == File.separatorChar ? 0 : -1;
    }

    private static int comparePaths(String prefix, String path, int offset) {
        int maxPos = Math.min(prefix.length(), path.length() - offset);
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = prefix.charAt(pos);
            char charInPath2 = path.charAt(pos + offset);
            if (charInPath1 != charInPath2) {
                return compareChars(charInPath1, charInPath2, File.separatorChar);
            }
        }
        return Integer.compare(prefix.length(), path.length());
    }

    protected static int compareChars(char char1, char char2, char separatorChar) {
        return char1 == separatorChar ? -1
            : char2 == separatorChar ? 1
            : Character.compare(char1, char2);
    }

    public static Comparator<String> pathComparator() {
        return PATH_COMPARATOR;
    }

    /**
     * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
     * which does not check for negative indices or integer overflow.
     */
    public static boolean isChildOfOrThis(String filePath, int offset, String prefix) {
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

    /**
     * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
     * which does not check for negative indices or integer overflow.
     */
    public static int compareToChildOfOrThis(String prefix, String filePath, int offset, char separatorChar) {
        int pathLength = filePath.length();
        int prefixLength = prefix.length();
        int endOfThisSegment = prefixLength + offset;
        if (pathLength < endOfThisSegment) {
            return comparePaths(prefix, filePath, offset);
        }
        for (int i = 0; i < prefixLength; i++) {
            char prefixChar = prefix.charAt(i);
            char pathChar = filePath.charAt(i + offset);
            if (prefixChar != pathChar) {
                return compareChars(prefixChar, pathChar, separatorChar);
            }
        }
        return endOfThisSegment == pathLength || filePath.charAt(endOfThisSegment) == separatorChar ? 0 : -1;
    }

    public static <T> T handleChildren(List<? extends FileSystemNode> children, String path, int offset, ChildHandler<T> childHandler) {
        int childIndex = ListUtils.binarySearch(
            children,
            candidate -> compareWithCommonPrefix(candidate.getPrefix(), path, offset, File.separatorChar)
        );
        if (childIndex >= 0) {
            return childHandler.handleChildOfExisting(offset, childIndex);
        }
        return childHandler.handleNewChild(offset, -childIndex - 1);
    }

    public static FileSystemNode updateSingleChild(FileSystemNode child, String path, FileSystemLocationSnapshot snapshot) {
        return handlePrefix(child.getPrefix(), path, new DescendantHandler<FileSystemNode>() {
            @Override
            public FileSystemNode handleDescendant() {
                return child.update(path.substring(child.getPrefix().length() + 1), snapshot);
            }

            @Override
            public FileSystemNode handleParent() {
                return new SnapshotFileSystemNode(path, snapshot);
            }

            @Override
            public FileSystemNode handleSame() {
                return new SnapshotFileSystemNode(path, snapshot);
            }

            @Override
            public FileSystemNode handleDifferent(int commonPrefixLength) {
                String prefix = child.getPrefix();
                String commonPrefix = prefix.substring(0, commonPrefixLength);
                FileSystemNode newChild = child.withPrefix(prefix.substring(commonPrefixLength + 1));
                FileSystemNode sibling = new SnapshotFileSystemNode(path.substring(commonPrefixLength + 1), snapshot);
                ImmutableList<FileSystemNode> newChildren = pathComparator().compare(newChild.getPrefix(), sibling.getPrefix()) < 0
                    ? ImmutableList.of(newChild, sibling)
                    : ImmutableList.of(sibling, newChild);
                return new FileSystemNodeWithChildren(commonPrefix, newChildren);
            }
        });
    }

    public static Optional<FileSystemNode> invalidateSingleChild(FileSystemNode child, String path) {
        return handlePrefix(child.getPrefix(), path, new DescendantHandler<Optional<FileSystemNode>>() {
            @Override
            public Optional<FileSystemNode> handleDescendant() {
                return child.invalidate(path.substring(child.getPrefix().length() + 1));
            }

            @Override
            public Optional<FileSystemNode> handleParent() {
                return Optional.empty();
            }


            @Override
            public Optional<FileSystemNode> handleSame() {
                return Optional.empty();
            }

            @Override
            public Optional<FileSystemNode> handleDifferent(int commonPrefixLength) {
                return Optional.of(child);
            }
        });
    }

    public static <T> T handlePrefix(String prefix, String path, DescendantHandler<T> descendantHandler) {
        int prefixLength = prefix.length();
        int pathLength = path.length();
        int maxPos = Math.min(prefixLength, pathLength);
        int commonPrefixLength = sizeOfCommonPrefix(prefix, path, 0);
        if (commonPrefixLength == maxPos) {
            if (prefixLength > pathLength) {
                return descendantHandler.handleParent();
            }
            if (prefixLength == pathLength) {
                return descendantHandler.handleSame();
            }
            return descendantHandler.handleDescendant();
        }
        return descendantHandler.handleDifferent(commonPrefixLength);
    }

    public interface DescendantHandler<T> {
        T handleDescendant();
        T handleParent();
        T handleSame();
        T handleDifferent(int commonPrefixLength);
    }

    public interface ChildHandler<T> {
        T handleNewChild(int startNextSegment, int insertBefore);
        T handleChildOfExisting(int startNextSegment, int childIndex);
    }
}
