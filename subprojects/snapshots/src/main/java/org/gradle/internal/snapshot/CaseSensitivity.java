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

import java.util.Comparator;

import static org.gradle.internal.snapshot.PathUtil.isFileSeparator;

public enum CaseSensitivity {
    CASE_SENSITIVE {
        @Override
        public boolean equalChars(char char1, char char2) {
            return PathUtil.equalChars(char1, char2, true);
        }

        @Override
        public int compareChars(char char1, char char2) {
            return PathUtil.compareChars(char1, char2, true);
        }

        @Override
        protected int computeCombinedCompare(int previousCombinedValue, char charInPath1, char charInPath2, PathSeparatorCompareResult pathSeparatorCompareResult) {
            return previousCombinedValue == 0
                ? (
                pathSeparatorCompareResult == PathSeparatorCompareResult.NONE_IS_SEPARATOR
                    ? charInPath1 - charInPath2
                    : pathSeparatorCompareResult.getCompareValue())
                : previousCombinedValue;
        }
    },
    CASE_INSENSITIVE {
        @Override
        public boolean equalChars(char char1, char char2) {
            return PathUtil.equalChars(char1, char2, false);
        }

        @Override
        public int compareChars(char char1, char char2) {
            return PathUtil.compareChars(char1, char2, false);
        }

        @Override
        protected int computeCombinedCompare(int previousCombinedValue, char charInPath1, char charInPath2, PathSeparatorCompareResult pathSeparatorCompareResult) {
            return 0;
        }
    };

    private final Comparator<String> pathComparator;

    CaseSensitivity() {
        this.pathComparator = (path1, path2) -> comparePaths(path1, path2, 0);
    }

    public abstract boolean equalChars(char char1, char char2);

    public abstract int compareChars(char char1, char char2);

    public Comparator<String> getPathComparator() {
        return pathComparator;
    }

    /**
     * Compares based on the first prefix of two paths.
     *
     * The paths must not start with a separator, taking into account the offset
     *
     * @return 0 when the two paths have the same prefix, and the comparison of the first prefix if not.
     */
    public int compareWithCommonPrefix(String path1, String path2, int offset) {
        int maxPos = Math.min(path1.length(), path2.length() - offset);
        int accumulatedValue = 0;
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = path1.charAt(pos);
            char charInPath2 = path2.charAt(pos + offset);
            PathSeparatorCompareResult pathSeparatorCompareResult = compareSeparators(charInPath1, charInPath2);
            int comparedChars = pathSeparatorCompareResult == PathSeparatorCompareResult.NONE_IS_SEPARATOR
                ? compareIgnoringCase(charInPath1, charInPath2)
                : pathSeparatorCompareResult.getCompareValue();
            if (comparedChars != 0) {
                return comparedChars;
            }
            accumulatedValue = computeCombinedCompare(accumulatedValue, charInPath1, charInPath2, pathSeparatorCompareResult);
            if (pathSeparatorCompareResult == PathSeparatorCompareResult.BOTH_ARE_SEPARATORS) {
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

    public int comparePaths(String prefix, String path, int offset) {
        int maxPos = Math.min(prefix.length(), path.length() - offset);
        int accumulatedValue = 0;
        for (int pos = 0; pos < maxPos; pos++) {
            char charInPath1 = prefix.charAt(pos);
            char charInPath2 = path.charAt(pos + offset);
            PathSeparatorCompareResult pathSeparatorCompareResult = compareSeparators(charInPath1, charInPath2);
            int comparedChars = pathSeparatorCompareResult == PathSeparatorCompareResult.NONE_IS_SEPARATOR
                ? compareIgnoringCase(charInPath1, charInPath2)
                : pathSeparatorCompareResult.getCompareValue();
            if (comparedChars != 0) {
                return comparedChars;
            }
            accumulatedValue = computeCombinedCompare(accumulatedValue, charInPath1, charInPath2, pathSeparatorCompareResult);
            if (accumulatedValue != 0 && pathSeparatorCompareResult == PathSeparatorCompareResult.BOTH_ARE_SEPARATORS) {
                return accumulatedValue;
            }
        }
        int lengthCompare = Integer.compare(prefix.length(), path.length() - offset);
        return lengthCompare != 0
            ? lengthCompare
            : accumulatedValue;
    }

    /**
     * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
     * which does not check for negative indices or integer overflow.
     */
    public int compareToChildOfOrThis(String prefix, String filePath, int offset) {
        int pathLength = filePath.length();
        int prefixLength = prefix.length();
        int endOfThisSegment = prefixLength + offset;
        if (pathLength < endOfThisSegment) {
            return comparePaths(prefix, filePath, offset);
        }
        int accumulatedValue = 0;
        for (int pos = 0; pos < prefixLength; pos++) {
            char charInPath1 = prefix.charAt(pos);
            char charInPath2 = filePath.charAt(pos + offset);
            PathSeparatorCompareResult pathSeparatorCompareResult = compareSeparators(charInPath1, charInPath2);
            int comparedChars = pathSeparatorCompareResult == PathSeparatorCompareResult.NONE_IS_SEPARATOR
                ? compareIgnoringCase(charInPath1, charInPath2)
                : pathSeparatorCompareResult.getCompareValue();
            if (comparedChars != 0) {
                return comparedChars;
            }
            accumulatedValue = computeCombinedCompare(accumulatedValue, charInPath1, charInPath2, pathSeparatorCompareResult);
            if (accumulatedValue != 0 && pathSeparatorCompareResult == PathSeparatorCompareResult.BOTH_ARE_SEPARATORS) {
                return accumulatedValue;
            }
        }
        return endOfThisSegment == pathLength || isFileSeparator(filePath.charAt(endOfThisSegment))
            ? accumulatedValue
            : -1;
    }

    protected abstract int computeCombinedCompare(int previousCombinedValue, char charInPath1, char charInPath2, PathSeparatorCompareResult pathSeparatorCompareResult);

    private static int compareIgnoringCase(char char1, char char2) {
        if (char1 == char2) {
            return 0;
        }
        int uppercaseCompare = Character.toUpperCase(char1) - Character.toUpperCase(char2);
        if (uppercaseCompare != 0) {
            return uppercaseCompare;
        }
        return Character.toLowerCase(char1) - Character.toLowerCase(char2);
    }

    private static PathSeparatorCompareResult compareSeparators(char left, char right) {
        if (isFileSeparator(left)) {
            return isFileSeparator(right)
                ? PathSeparatorCompareResult.BOTH_ARE_SEPARATORS
                : PathSeparatorCompareResult.LEFT_IS_SEPARATOR;
        } else {
            return isFileSeparator(right)
                ? PathSeparatorCompareResult.RIGHT_IS_SEPARATOR
                : PathSeparatorCompareResult.NONE_IS_SEPARATOR;
        }
    }

    private enum PathSeparatorCompareResult {
        LEFT_IS_SEPARATOR(-1),
        RIGHT_IS_SEPARATOR(1),
        BOTH_ARE_SEPARATORS(0),
        NONE_IS_SEPARATOR(Integer.MAX_VALUE);

        private final int compareValue;

        PathSeparatorCompareResult(int compareValue) {
            this.compareValue = compareValue;
        }

        public int getCompareValue() {
            return compareValue;
        }
    }
}
