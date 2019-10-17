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

import java.io.File;
import java.util.Optional;

public abstract class AbstractNode implements Node {
    private final String prefix;

    public AbstractNode(String prefix) {
        this.prefix = prefix;
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

    @Override
    public String getPrefix() {
        return prefix;
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

    interface DescendantHandler<T> {
        T handleDescendant();
        T handleParent();
        T handleSame();
        T handleDifferent(int commonPrefixLength);
    }

    protected abstract class InvalidateHandler implements DescendantHandler<Optional<Node>> {
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
            return Optional.of(AbstractNode.this);
        }
    }
}
