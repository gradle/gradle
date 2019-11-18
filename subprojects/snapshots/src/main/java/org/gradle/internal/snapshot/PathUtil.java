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

import java.io.File;
import java.util.Comparator;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

/**
 * Methods for dealing with paths on the file system.
 *
 * There are methods for checking equality and for comparing two paths.
 * All methods for equality and comparing need to be called with the correct case-sensitivity according to the underlying file system.
 *
 * A segment of a path is the part between two file separators.
 * For example, the path some/long/path has the segments some, long and path.
 *
 * For comparing, a list of paths is sorted in the same order on a case-insensitive and a case-sensitive file system.
 * We do this so the order of the children of directory snapshots is stable across builds.
 *
 * The order is as follows:
 * - The comparison is per segment of the path.
 * - If the segments are different with respect to case-insensitive comparison, the result from case-insensitive comparison is used.
 * - If one segment starts with the other segment comparing case-insensitive, then the shorter segment is smaller.
 * - Finally, if both segments are the same ignoring case and have the same length, the case-sensitive comparison is used.
 *
 * For all methods operating on a list of paths, the paths must not start with a common segment.
 * For example, ["some", "some1/other", "other/foo"] is allowed, but ["some/foo", "some/bar", "other/foo"] is not.
 *
 * Most of the methods take an absolutePath and an offset within the path.
 * The offset should always point to the first character of a segment of the absolute path, i.e. not including the file separator.
 * This is a performance optimization so that we don't need to call {@link String#substring(int)} to obtain a relative path from an absolute path.
 * The absolutePath argument of the method is supposed to be an absolute path and the offset determines the relative path to the examined location.
 * For example, filePath = /some/foo/bar with offset 6 determines the relative path foo/bar from the location /some.
 * TODO: There should be a separate type for capturing the relative path defined by an absolute path and offset.
 */
public class PathUtil {

    /**
     * The Unix separator character.
     */
    private static final char UNIX_SEPARATOR = '/';

    /**
     * The Windows separator character.
     */
    private static final char WINDOWS_SEPARATOR = '\\';

    /**
     * The system separator character.
     */
    private static final char SYSTEM_SEPARATOR = File.separatorChar;

    private static final boolean IS_WINDOWS_SEPARATOR = SYSTEM_SEPARATOR == WINDOWS_SEPARATOR;

    /**
     * The separator character that is the opposite of the system separator.
     */
    private static final char OTHER_SEPARATOR = IS_WINDOWS_SEPARATOR ? UNIX_SEPARATOR : WINDOWS_SEPARATOR;

    private static final Comparator<String> CASE_SENSITIVE_COMPARATOR = (path1, path2) -> comparePaths(path1, path2, CASE_SENSITIVE);
    private static final Comparator<String> CASE_INSENSITIVE_COMPARATOR = (path1, path2) -> comparePaths(path1, path2, CASE_INSENSITIVE);

    /**
     * Whether the given char is a file separator.
     * Both Unix and Windows file separators are detected, no matter the current operating system.
     */
    public static boolean isFileSeparator(char toCheck) {
        return toCheck == SYSTEM_SEPARATOR || toCheck == OTHER_SEPARATOR;
    }

    /**
     * Compares two file names with the order defined here: {@link PathUtil}.
     *
     * File names do not contain file separators, so the methods on {@link String} can be used for the comparison.
     */
    public static int compareFileNames(String name1, String name2) {
        int caseInsensitiveComparison = name1.compareToIgnoreCase(name2);
        return caseInsensitiveComparison != 0
            ? caseInsensitiveComparison
            : name1.compareTo(name2);
    }

    /**
     * Returns a comparator for paths for the given case sensitivity.
     *
     * When the two paths are different ignoring the case, then the result of the comparison is the same for both comparators.
     */
    public static Comparator<String> getPathComparator(CaseSensitivity caseSensitivity) {
        switch (caseSensitivity) {
            case CASE_SENSITIVE:
                return CASE_SENSITIVE_COMPARATOR;
            case CASE_INSENSITIVE:
                return CASE_INSENSITIVE_COMPARATOR;
            default:
                throw new AssertionError();
        }
    }

    @VisibleForTesting
    static int compareCharsIgnoringCase(char char1, char char2) {
        if (char1 == char2) {
            return 0;
        }
        return isFileSeparator(char1)
            ? isFileSeparator(char2)
                ? 0
                : -1
            : isFileSeparator(char2)
                ? 1
                : compareDifferentCharsIgnoringCase(char1, char2);
    }

    private static int compareDifferentCharsIgnoringCase(char char1, char char2) {
        char insensitiveChar1 = Character.toUpperCase(char1);
        char insensitiveChar2 = Character.toUpperCase(char2);
        if (insensitiveChar1 != insensitiveChar2) {
            insensitiveChar1 = Character.toLowerCase(insensitiveChar1);
            insensitiveChar2 = Character.toLowerCase(insensitiveChar2);
            if (insensitiveChar1 != insensitiveChar2) {
                return Character.compare(insensitiveChar1, insensitiveChar2);
            }
        }
        return 0;
    }

    @VisibleForTesting
    static int compareChars(char char1, char char2) {
        if (char1 == char2) {
            return 0;
        }
        return isFileSeparator(char1)
            ? isFileSeparator(char2)
                ? 0
                : -1
            : isFileSeparator(char2)
                ? 1
                : Character.compare(char1, char2);
    }

    @VisibleForTesting
    static boolean equalChars(char char1, char char2, CaseSensitivity caseSensitivity) {
        if (char1 == char2) {
            return true;
        }
        if (isFileSeparator(char1) && isFileSeparator(char2)) {
            return true;
        }
        if (caseSensitivity == CASE_SENSITIVE) {
            return false;
        } else {
            return Character.toUpperCase(char1) == Character.toUpperCase(char2) ||
                Character.toLowerCase(char1) == Character.toLowerCase(char2);
        }
    }

    /**
     * Returns the length of the common prefix of a path and a sub-path of another path starting at on offset.
     *
     * The length of the common prefix does not include the last line separator.
     *
     * Examples:
     * lengthOfCommonPrefix("some/path", "some/other") == 4
     * lengthOfCommonPrefix("some/path", "some1/other") == 0
     * lengthOfCommonPrefix("some/longer/path", "some/longer/other") == 11
     * lengthOfCommonPrefix("some/longer", "some/longer/path") == 11
     */
    public static int lengthOfCommonPrefix(String relativePath, String absolutePath, int offset, CaseSensitivity caseSensitivity) {
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
     * Compares based on the first segment of two paths.
     *
     * A segment of a path is the part between two file separators.
     * For example, the path some/long/path has the segments some, long and path.
     *
     * Similar to {@link #lengthOfCommonPrefix(String, String, int, CaseSensitivity)},
     * only that this method compares the first segment of the paths if there is no common prefix.
     *
     * The paths must not start with a separator, taking into account the offset
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
    public static int compareFirstSegment(String absolutePath, int offset, String relativePath, CaseSensitivity caseSensitivity) {
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
     * Checks whether the relative path given by the absolute path and the offset has the given prefix.
     */
    public static boolean hasPrefix(String prefix, String absolutePath, int offset, CaseSensitivity caseSensitivity) {
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

    private static int comparePaths(String relativePath1, String relativePath2, CaseSensitivity caseSensitivity) {
        int maxPos = Math.min(relativePath1.length(), relativePath2.length());
        int accumulatedValue = 0;
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = relativePath1.charAt(pos);
            char charInPath2 = relativePath2.charAt(pos);
            int comparedChars = compareCharsIgnoringCase(charInPath1, charInPath2);
            if (comparedChars != 0) {
                return comparedChars;
            }
            accumulatedValue = computeCombinedCompare(accumulatedValue, charInPath1, charInPath2, caseSensitivity == CASE_SENSITIVE);
            if (accumulatedValue != 0 && isFileSeparator(charInPath1)) {
                return accumulatedValue;
            }
        }
        int lengthCompare = Integer.compare(relativePath1.length(), relativePath2.length());
        return lengthCompare != 0
            ? lengthCompare
            : accumulatedValue;
    }

    private static int computeCombinedCompare(int previousCombinedValue, char charInPath1, char charInPath2, boolean caseSensitive) {
        if (!caseSensitive) {
            return 0;
        }
        return previousCombinedValue == 0
            ? compareChars(charInPath1, charInPath2)
            : previousCombinedValue;
    }

    public static String getFileName(String absolutePath) {
        int lastSeparator = lastIndexOfSeparator(absolutePath);
        return lastSeparator < 0
            ? absolutePath
            : absolutePath.substring(lastSeparator + 1);
    }

    private static int lastIndexOfSeparator(String absolutePath) {
        for (int i = absolutePath.length() - 1; i >= 0; i--) {
            char currentChar = absolutePath.charAt(i);
            if (isFileSeparator(currentChar)) {
                return i;
            }
        }
        return -1;
    }

    public static int descendantChildOffset(String childPathToParent) {
        return childPathToParent.isEmpty() ? 0 : 1;
    }
}
