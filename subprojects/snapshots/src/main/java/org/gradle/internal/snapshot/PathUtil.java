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
import java.util.function.IntUnaryOperator;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

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

    public static boolean isFileSeparator(char toCheck) {
        return toCheck == SYSTEM_SEPARATOR || toCheck == OTHER_SEPARATOR;
    }

    private static final Comparator<String> CASE_SENSITIVE_COMPARATOR = (path1, path2) -> comparePaths(path1, path2, 0, CASE_SENSITIVE);
    private static final Comparator<String> CASE_INSENSITIVE_COMPARATOR = (path1, path2) -> comparePaths(path1, path2, 0, CASE_INSENSITIVE);

    public static int compareFileNames(String name1, String name2) {
        int caseInsensitiveComparison = name1.compareToIgnoreCase(name2);
        return caseInsensitiveComparison != 0
            ? caseInsensitiveComparison
            : name1.compareTo(name2);
    }

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
                return insensitiveChar1 - insensitiveChar2;
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
                : char1 - char2;
    }

    @VisibleForTesting
    static boolean equalChars(char char1, char char2, boolean caseSensitive) {
        if (char1 == char2) {
            return true;
        }
        if (isFileSeparator(char1) && isFileSeparator(char2)) {
            return true;
        }
        if (caseSensitive) {
            return false;
        } else {
            return Character.toUpperCase(char1) == Character.toUpperCase(char2) ||
                Character.toLowerCase(char1) == Character.toLowerCase(char2);
        }
    }

    /**
     * Does not include the separator char.
     */
    public static int sizeOfCommonPrefix(String path1, String path2, int offset, CaseSensitivity caseSensitivity) {
        int pos = 0;
        int lastSeparator = 0;
        boolean caseSensitive = caseSensitivity == CASE_SENSITIVE;
        int maxPos = Math.min(path1.length(), path2.length() - offset);
        for (; pos < maxPos; pos++) {
            char charInPath1 = path1.charAt(pos);
            char charInPath2 = path2.charAt(pos + offset);
            if (!equalChars(charInPath1, charInPath2, caseSensitive)) {
                break;
            }
            if (isFileSeparator(charInPath1)) {
                lastSeparator = pos;
            }
        }
        if (pos == maxPos) {
            if (path1.length() == path2.length() - offset) {
                return pos;
            }
            if (pos < path1.length() && isFileSeparator(path1.charAt(pos))) {
                return pos;
            }
            if (pos < path2.length() - offset && isFileSeparator(path2.charAt(pos + offset))) {
                return pos;
            }
        }
        return lastSeparator;
    }

    /**
     * Compares based on the first prefix of two paths.
     *
     * The paths must not start with a separator, taking into account the offset
     *
     * @return 0 when the two paths have the same prefix, and the comparison of the first prefix if not.
     */
    public static int compareWithCommonPrefix(String path1, String path2, int offset, CaseSensitivity caseSensitivity) {
        int maxPos = Math.min(path1.length(), path2.length() - offset);
        int accumulatedValue = 0;
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = path1.charAt(pos);
            char charInPath2 = path2.charAt(pos + offset);
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
        if (path1.length() == path2.length() - offset) {
            return accumulatedValue;
        }
        if (path1.length() > path2.length() - offset) {
            return isFileSeparator(path1.charAt(maxPos)) ? accumulatedValue : 1;
        }
        return isFileSeparator(path2.charAt(maxPos + offset)) ? accumulatedValue : -1;
    }

    /**
     * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
     * which does not check for negative indices or integer overflow.
     */
    public static int compareToChildOfOrThis(String prefix, String filePath, int offset, CaseSensitivity caseSensitivity) {
        int pathLength = filePath.length();
        int prefixLength = prefix.length();
        int endOfThisSegment = prefixLength + offset;
        if (pathLength < endOfThisSegment) {
            return comparePaths(prefix, filePath, offset, caseSensitivity);
        }
        return comparePrefixes(prefix, filePath, offset, prefixLength, caseSensitivity == CASE_SENSITIVE,
            accumulatedValue -> endOfThisSegment == pathLength || isFileSeparator(filePath.charAt(endOfThisSegment))
                ? accumulatedValue
                : -1);
    }

    public static int comparePaths(String prefix, String path, int offset, CaseSensitivity caseSensitivity) {
        int maxPos = Math.min(prefix.length(), path.length() - offset);
        return comparePrefixes(prefix, path, offset, maxPos, caseSensitivity == CASE_SENSITIVE,
            accumulatedValue -> {
                int lengthCompare = Integer.compare(prefix.length(), path.length() - offset);
                return lengthCompare != 0
                    ? lengthCompare
                    : accumulatedValue;
            }
        );
    }

    private static int comparePrefixes(String prefix, String path, int offset, int maxPos, boolean caseSensitive, IntUnaryOperator andThenCompare) {
        int accumulatedValue = 0;
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = prefix.charAt(pos);
            char charInPath2 = path.charAt(pos + offset);
            int comparedChars = compareCharsIgnoringCase(charInPath1, charInPath2);
            if (comparedChars != 0) {
                return comparedChars;
            }
            accumulatedValue = computeCombinedCompare(accumulatedValue, charInPath1, charInPath2, caseSensitive);
            if (accumulatedValue != 0 && isFileSeparator(charInPath1)) {
                return accumulatedValue;
            }
        }
        return andThenCompare.applyAsInt(accumulatedValue);
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

    /**
     * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
     * which does not check for negative indices or integer overflow.
     */
    public static boolean isChildOfOrThis(String filePath, int offset, String prefix, CaseSensitivity caseSensitivity) {
        boolean caseSensitive = caseSensitivity == CASE_SENSITIVE;
        int prefixLength = prefix.length();
        if (prefixLength == 0) {
            return true;
        }
        int pathLength = filePath.length();
        int endOfThisSegment = prefixLength + offset;
        if (pathLength < endOfThisSegment) {
            return false;
        }
        for (int i = prefixLength - 1, j = endOfThisSegment - 1; i >= 0; i--, j--) {
            if (!equalChars(prefix.charAt(i), filePath.charAt(j), caseSensitive)) {
                return false;
            }
        }
        return endOfThisSegment == pathLength || isFileSeparator(filePath.charAt(endOfThisSegment));
    }

    public static int descendantChildOffset(String childPathToParent) {
        return childPathToParent.isEmpty() ? 0 : 1;
    }
}
