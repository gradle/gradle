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

public class PathUtil {
    private static final Comparator<String> PATH_COMPARATOR = (path1, path2) -> comparePaths(path1, path2, 0, true);

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

    static int comparePaths(String prefix, String path, int offset, boolean caseSensitive) {
        int maxPos = Math.min(prefix.length(), path.length() - offset);
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = prefix.charAt(pos);
            char charInPath2 = path.charAt(pos + offset);
            int comparedChars = compareChars(charInPath1, charInPath2, caseSensitive);
            if (comparedChars != 0) {
                return comparedChars;
            }
        }
        return Integer.compare(prefix.length(), path.length() - offset);
    }

    @VisibleForTesting
    static int compareChars(char char1, char char2, boolean caseSensitive) {
        if (char1 == char2) {
            return 0;
        }
        return isFileSeparator(char1)
            ? isFileSeparator(char2)
                ? 0
                : -1
            : isFileSeparator(char2)
                ? 1
                : compareToDifferentChars(char1, char2, caseSensitive);
    }

    private static int compareToDifferentChars(char char1, char char2, boolean caseSensitive) {
        if (isLowercase(char1)) {
            if (isUppercase(char2)) {
                return compareLowerToUppercase(char1, char2, caseSensitive);
            } else if (isLowercase(char2)) {
                return char1 - char2;
            } else {
                return 1;
            }
        } else if (isUppercase(char1)) {
            if (isUppercase(char2)) {
                return char1 - char2;
            } else if (isLowercase(char2)) {
                return -compareLowerToUppercase(char2, char1, caseSensitive);
            } else {
                return 1;
            }
        } else if (isLowercase(char2) || isUppercase(char2)) {
            return -1;
        } else {
            return char1 - char2;
        }
    }

    private static int compareLowerToUppercase(char lower, char upper, boolean caseSensitive) {
        int lowerDiff = lower - 'a';
        int upperDiff = upper - 'A';
        if (caseSensitive) {
            return lowerDiff == upperDiff
                ? 1
                : lowerDiff - upperDiff;
        } else {
            return lowerDiff - upperDiff;
        }
    }

    private static boolean isUppercase(char character) {
        return 'A' <= character && 'Z' >= character;
    }

    private static boolean isLowercase(char character) {
        return 'a' <= character && 'z' >= character;
    }

    private static boolean isLetter(char character) {
        return isUppercase(character) || isLowercase(character);
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
            int difference = char1 - char2;
            if (Math.abs(difference) != 32) {
                return false;
            }
            return isLetter(char1) && isLetter(char2);
        }
    }

    public static Comparator<String> pathComparator() {
        return PATH_COMPARATOR;
    }

    /**
     * Does not include the separator char.
     */
    public static int sizeOfCommonPrefix(String path1, String path2, int offset, boolean caseSensitive) {
        int pos = 0;
        int lastSeparator = 0;
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

    public static int compareWithCommonPrefix(String path1, String path2, int offset, boolean caseSensitive) {
        int maxPos = Math.min(path1.length(), path2.length() - offset);
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = path1.charAt(pos);
            char charInPath2 = path2.charAt(pos + offset);
            int comparedChars = compareChars(charInPath1, charInPath2, caseSensitive);
            if (comparedChars != 0) {
                return comparedChars;
            }
            if (isFileSeparator(charInPath1)) {
                if (pos > 0) {
                    return 0;
                }
            }
        }
        if (path1.length() == path2.length() - offset) {
            return 0;
        }
        if (path1.length() > path2.length() - offset) {
            return isFileSeparator(path1.charAt(maxPos)) ? 0 : 1;
        }
        return isFileSeparator(path2.charAt(maxPos + offset)) ? 0 : -1;
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
    public static boolean isChildOfOrThis(String filePath, int offset, String prefix, boolean caseSensitive) {
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

    /**
     * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
     * which does not check for negative indices or integer overflow.
     */
    public static int compareToChildOfOrThis(String prefix, String filePath, int offset, boolean caseSensitive) {
        int pathLength = filePath.length();
        int prefixLength = prefix.length();
        int endOfThisSegment = prefixLength + offset;
        if (pathLength < endOfThisSegment) {
            return comparePaths(prefix, filePath, offset, caseSensitive);
        }
        for (int i = 0; i < prefixLength; i++) {
            char prefixChar = prefix.charAt(i);
            char pathChar = filePath.charAt(i + offset);
            int comparedChars = compareChars(prefixChar, pathChar, caseSensitive);
            if (comparedChars != 0) {
                return comparedChars;
            }
        }
        return endOfThisSegment == pathLength || isFileSeparator(filePath.charAt(endOfThisSegment)) ? 0 : -1;
    }

    public static int descendantChildOffset(String childPathToParent) {
        return childPathToParent.isEmpty() ? 0 : 1;
    }
}
