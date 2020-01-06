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

import com.google.common.annotations.VisibleForTesting;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;
import static org.gradle.internal.snapshot.PathUtil.compareChars;
import static org.gradle.internal.snapshot.PathUtil.compareCharsIgnoringCase;
import static org.gradle.internal.snapshot.PathUtil.equalChars;
import static org.gradle.internal.snapshot.PathUtil.isFileSeparator;

/**
 * A relative path represented by a path suffix of an absolute path.
 *
 * The use of this class is to improve performance by avoiding to call {@link String#substring(int)}.
 * The class represents the relative path of absolutePath.substring(offset).
 *
 * A relative path does not start or end with a slash.
 */
public class VfsRelativePath {
    private final String absolutePath;
    private final int offset;

    /**
     * The relative path from the root of the file system for the given absolute path.
     *
     * E.g.:
     *    'C:/' -> 'C:'
     *    '/home/user/project' -> 'home/user/project'
     *    '/' -> ''
     *    '//uncpath/relative/path' -> 'uncpath/relative/path'
     *    'C:/Users/user/project' -> 'C:/Users/user/project'
     */
    public static VfsRelativePath of(String absolutePath) {
        String normalizedRoot = normalizeRoot(absolutePath);
        return VfsRelativePath.of(normalizedRoot, determineOffset(normalizedRoot));
    }

    @VisibleForTesting
    static VfsRelativePath of(String absolutePath, int offset) {
        return new VfsRelativePath(absolutePath, offset);
    }

    private static String normalizeRoot(String absolutePath) {
        if (absolutePath.equals("/")) {
            return absolutePath;
        }
        return isFileSeparator(absolutePath.charAt(absolutePath.length() - 1))
            ? absolutePath.substring(0, absolutePath.length() - 1)
            : absolutePath;
    }

    private static int determineOffset(String absolutePath) {
        for (int i = 0; i < absolutePath.length(); i++) {
            if (!isFileSeparator(absolutePath.charAt(i))) {
                return i;
            }
        }
        return absolutePath.length();
    }

    private VfsRelativePath(String absolutePath, int offset) {
        this.absolutePath = absolutePath;
        this.offset = offset;
    }

    /**
     * Returns a new relative path starting from the given start index.
     *
     * E.g.
     *   (some/path, 5) -> path
     */
    public VfsRelativePath suffixStartingFrom(int startIndex) {
        return new VfsRelativePath(absolutePath, offset + startIndex);
    }

    /**
     * Returns a new relative path starting from the child.
     *
     * E.g.
     *   (some/path, some) -> path
     *   (some/path/other, some) -> path/other
     *   (C:, '') -> C:
     */
    public VfsRelativePath fromChild(String relativeChildPath) {
        return relativeChildPath.isEmpty()
            ? this
            : suffixStartingFrom(relativeChildPath.length() + 1);
    }

    public int length() {
        return absolutePath.length() - offset;
    }

    /**
     * The relative path represented by this suffix as a String.
     */
    public String getAsString() {
        return absolutePath.substring(offset);
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    /**
     * Returns the length of the common prefix of this with a relative path.
     *
     * The length of the common prefix does not include the last line separator.
     *
     * Examples:
     * lengthOfCommonPrefix("some/path", "some/other") == 4
     * lengthOfCommonPrefix("some/path", "some1/other") == 0
     * lengthOfCommonPrefix("some/longer/path", "some/longer/other") == 11
     * lengthOfCommonPrefix("some/longer", "some/longer/path") == 11
     */
    public int lengthOfCommonPrefix(String relativePath, CaseSensitivity caseSensitivity) {
        int pos = 0;
        int lastSeparator = 0;
        int maxPos = Math.min(relativePath.length(), absolutePath.length() - offset);
        for (; pos < maxPos; pos++) {
            char charInPath1 = relativePath.charAt(pos);
            char charInPath2 = absolutePath.charAt(pos + offset);
            if (!equalChars(charInPath1, charInPath2, caseSensitivity)) {
                break;
            }
            if (isFileSeparator(charInPath1)) {
                lastSeparator = pos;
            }
        }
        if (pos == maxPos) {
            if (relativePath.length() == absolutePath.length() - offset) {
                return pos;
            }
            if (pos < relativePath.length() && isFileSeparator(relativePath.charAt(pos))) {
                return pos;
            }
            if (pos < absolutePath.length() - offset && isFileSeparator(absolutePath.charAt(pos + offset))) {
                return pos;
            }
        }
        return lastSeparator;
    }

    /**
     * Compares to the first segment of a relative path.
     *
     * A segment of a path is the part between two file separators.
     * For example, the path some/long/path has the segments some, long and path.
     *
     * Similar to {@link #lengthOfCommonPrefix(String, CaseSensitivity)},
     * only that this method compares to the first segment of the path if there is no common prefix.
     *
     * The path must not start with a separator.
     *
     * For example, this method returns:
     *     some/path     == some/other
     *     some1/path    <  some2/other
     *     some/path     >  some1/other
     *     some/same     == some/same/more
     *     some/one/alma == some/two/bela
     *     a/some        <  b/other
     *
     * @return 0 if the two paths have a common prefix, and the comparison of the first segment of each path if not.
     */
    public int compareToFirstSegment(String relativePath, CaseSensitivity caseSensitivity) {
        int maxPos = Math.min(relativePath.length(), absolutePath.length() - offset);
        int accumulatedValue = 0;
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = absolutePath.charAt(pos + offset);
            char charInPath2 = relativePath.charAt(pos);
            int comparedChars = compareCharsIgnoringCase(charInPath1, charInPath2);
            if (comparedChars != 0) {
                return comparedChars;
            }
            accumulatedValue = computeCombinedCompare(accumulatedValue, charInPath1, charInPath2, caseSensitivity == CASE_SENSITIVE);
            if (isFileSeparator(charInPath1)) {
                if (pos > 0) {
                    return accumulatedValue;
                }
            }
        }
        if (absolutePath.length() - offset == relativePath.length()) {
            return accumulatedValue;
        }
        if (absolutePath.length() - offset > relativePath.length()) {
            return isFileSeparator(absolutePath.charAt(maxPos + offset)) ? accumulatedValue : 1;
        }
        return isFileSeparator(relativePath.charAt(maxPos)) ? accumulatedValue : -1;
    }

    /**
     * Checks whether this path has the prefix.
     */
    public boolean hasPrefix(String prefix, CaseSensitivity caseSensitivity) {
        int prefixLength = prefix.length();
        if (prefixLength == 0) {
            return true;
        }
        int pathLength = absolutePath.length();
        int endOfThisSegment = prefixLength + offset;
        if (pathLength < endOfThisSegment) {
            return false;
        }
        for (int i = prefixLength - 1, j = endOfThisSegment - 1; i >= 0; i--, j--) {
            if (!equalChars(prefix.charAt(i), absolutePath.charAt(j), caseSensitivity)) {
                return false;
            }
        }
        return endOfThisSegment == pathLength || isFileSeparator(absolutePath.charAt(endOfThisSegment));
    }

    private static int computeCombinedCompare(int previousCombinedValue, char charInPath1, char charInPath2, boolean caseSensitive) {
        if (!caseSensitive) {
            return 0;
        }
        return previousCombinedValue == 0
            ? compareChars(charInPath1, charInPath2)
            : previousCombinedValue;
    }

    @Override
    public String toString() {
        return getAsString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        VfsRelativePath that = (VfsRelativePath) o;

        if (offset != that.offset) {
            return false;
        }
        return absolutePath.equals(that.absolutePath);
    }

    @Override
    public int hashCode() {
        int result = absolutePath.hashCode();
        result = 31 * result + offset;
        return result;
    }
}
