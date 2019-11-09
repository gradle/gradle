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
        int comparePaths(String prefix, String path, int offset) {
            int maxPos = Math.min(prefix.length(), path.length() - offset);
            int caseSensitiveCompare = 0;
            for (int pos = 0; pos < maxPos; pos++) {
                char charInPath1 = prefix.charAt(pos);
                char charInPath2 = path.charAt(pos + offset);
                int comparedChars = CASE_INSENSITIVE.compareChars(charInPath1, charInPath2);
                if (comparedChars != 0) {
                    return comparedChars;
                }
                if (caseSensitiveCompare == 0) {
                    caseSensitiveCompare = compareChars(charInPath1, charInPath2);
                }
            }
            int lengthCompare = Integer.compare(prefix.length(), path.length() - offset);
            return lengthCompare != 0
                ? lengthCompare
                : caseSensitiveCompare;
        }

        @Override
        public int compareToChildOfOrThis(String prefix, String filePath, int offset) {
            int pathLength = filePath.length();
            int prefixLength = prefix.length();
            int endOfThisSegment = prefixLength + offset;
            if (pathLength < endOfThisSegment) {
                return comparePaths(prefix, filePath, offset);
            }
            int pathSensitiveCompare = 0;
            for (int i = 0; i < prefixLength; i++) {
                char prefixChar = prefix.charAt(i);
                char pathChar = filePath.charAt(i + offset);
                int comparedChars = CASE_INSENSITIVE.compareChars(prefixChar, pathChar);
                if (comparedChars != 0) {
                    return comparedChars;
                }
                if (pathSensitiveCompare == 0) {
                    pathSensitiveCompare = compareChars(prefixChar, pathChar);
                }
            }
            return endOfThisSegment == pathLength || isFileSeparator(filePath.charAt(endOfThisSegment))
                ? pathSensitiveCompare
                : -1;
        }

        @Override
        public int compareWithCommonPrefix(String path1, String path2, int offset) {
            int maxPos = Math.min(path1.length(), path2.length() - offset);
            int caseSensitiveCompare = 0;
            for (int pos = 0; pos < maxPos; pos++) {
                char charInPath1 = path1.charAt(pos);
                char charInPath2 = path2.charAt(pos + offset);
                int comparedChars = CASE_INSENSITIVE.compareChars(charInPath1, charInPath2);
                if (comparedChars != 0) {
                    return comparedChars;
                }
                if (caseSensitiveCompare == 0) {
                    caseSensitiveCompare = compareChars(charInPath1, charInPath2);
                }
                if (isFileSeparator(charInPath1)) {
                    if (pos > 0) {
                        return caseSensitiveCompare;
                    }
                }
            }
            if (path1.length() == path2.length() - offset) {
                return caseSensitiveCompare;
            }
            if (path1.length() > path2.length() - offset) {
                return isFileSeparator(path1.charAt(maxPos)) ? caseSensitiveCompare : 1;
            }
            return isFileSeparator(path2.charAt(maxPos + offset)) ? caseSensitiveCompare : -1;
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
        int comparePaths(String prefix, String path, int offset) {
            int maxPos = Math.min(prefix.length(), path.length() - offset);
            for (int pos = 0; pos < maxPos; pos++) {
                char charInPath1 = prefix.charAt(pos);
                char charInPath2 = path.charAt(pos + offset);
                int comparedChars = compareChars(charInPath1, charInPath2);
                if (comparedChars != 0) {
                    return comparedChars;
                }
            }
            return Integer.compare(prefix.length(), path.length() - offset);
        }

        @Override
        public int compareToChildOfOrThis(String prefix, String filePath, int offset) {
            int pathLength = filePath.length();
            int prefixLength = prefix.length();
            int endOfThisSegment = prefixLength + offset;
            if (pathLength < endOfThisSegment) {
                return comparePaths(prefix, filePath, offset);
            }
            for (int i = 0; i < prefixLength; i++) {
                char prefixChar = prefix.charAt(i);
                char pathChar = filePath.charAt(i + offset);
                int comparedChars = compareChars(prefixChar, pathChar);
                if (comparedChars != 0) {
                    return comparedChars;
                }
            }
            return endOfThisSegment == pathLength || isFileSeparator(filePath.charAt(endOfThisSegment)) ? 0 : -1;
        }

        @Override
        public int compareWithCommonPrefix(String path1, String path2, int offset) {
            int maxPos = Math.min(path1.length(), path2.length() - offset);
            for (int pos = 0; pos < maxPos; pos++) {
                char charInPath1 = path1.charAt(pos);
                char charInPath2 = path2.charAt(pos + offset);
                int comparedChars = compareChars(charInPath1, charInPath2);
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
    };

    private final Comparator<String> pathComparator;

    CaseSensitivity() {
        this.pathComparator = (path1, path2) -> comparePaths(path1, path2, 0);
    }

    public abstract boolean equalChars(char char1, char char2);

    public abstract int compareChars(char char1, char char2);

    abstract int comparePaths(String prefix, String path, int offset);

    public Comparator<String> getPathComparator() {
        return pathComparator;
    }

    public abstract int compareWithCommonPrefix(String path1, String path2, int offset);

    /**
     * This uses an optimized version of {@link String#regionMatches(int, String, int, int)}
     * which does not check for negative indices or integer overflow.
     */
    public abstract int compareToChildOfOrThis(String prefix, String filePath, int offset);
}
