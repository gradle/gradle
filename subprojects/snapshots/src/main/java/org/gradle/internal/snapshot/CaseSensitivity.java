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
    };

    private final Comparator<String> pathComparator;

    CaseSensitivity() {
        this.pathComparator = (path1, path2) -> comparePaths(path1, path2, 0);
    }

    public abstract boolean equalChars(char char1, char char2);

    public abstract int compareChars(char char1, char char2);

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

    public Comparator<String> getPathComparator() {
        return pathComparator;
    }
}
