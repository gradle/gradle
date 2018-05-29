/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.file.FileType;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;
import java.nio.file.Path;

@SuppressWarnings("Since15")
public enum InputPathNormalizationStrategy implements PathNormalizationStrategy {
    /**
     * Use the absolute path of the files.
     */
    ABSOLUTE {
        @Override
        public boolean isPathAbsolute() {
            return true;
        }

        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(Path path, Iterable<String> relativePath, FileContentSnapshot content, StringInterner stringInterner) {
            return getNormalizedSnapshot(path, content);
        }

        @Override
        public NormalizedFileSnapshot getNormalizedRootSnapshot(Path path, String name, FileContentSnapshot content, StringInterner stringInterner) {
            return getNormalizedSnapshot(path, content);
        }

        private NormalizedFileSnapshot getNormalizedSnapshot(Path path, FileContentSnapshot content) {
            return new NonNormalizedFileSnapshot(path.toString(), content);
        }
    },

    /**
     * Use the location of the file related to a hierarchy.
     */
    RELATIVE {
        @Override
        public boolean isPathAbsolute() {
            return false;
        }

        @Nullable
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(Path path, Iterable<String> relativePath, FileContentSnapshot content, StringInterner stringInterner) {
            String relativePathString = getRelativePathString(relativePath);
            return getRelativeSnapshot(path, content, relativePathString, stringInterner);
        }

        @Nullable
        @Override
        public NormalizedFileSnapshot getNormalizedRootSnapshot(Path path, String name, FileContentSnapshot content, StringInterner stringInterner) {
            if (content.getType() == FileType.Directory) {
                return new IgnoredPathFileSnapshot(content);
            }
            return getNormalizedSnapshot(path, ImmutableList.of(name), content, stringInterner);
        }
    },

    /**
     * Use the file name only.
     */
    NAME_ONLY {
        @Override
        public boolean isPathAbsolute() {
            return false;
        }

        @Nullable
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(Path path, Iterable<String> relativePath, FileContentSnapshot content, StringInterner stringInterner) {
            return getRelativeSnapshot(path, content, Iterables.getLast(relativePath), stringInterner);
        }

        @Nullable
        @Override
        public NormalizedFileSnapshot getNormalizedRootSnapshot(Path path, String name, FileContentSnapshot content, StringInterner stringInterner) {
            if (content.getType() == FileType.Directory) {
                return new IgnoredPathFileSnapshot(content);
            }
            return getNormalizedSnapshot(path, ImmutableList.of(name), content, stringInterner);
        }
    },

    /**
     * Ignore the file path completely.
     */
    NONE {
        @Override
        public boolean isPathAbsolute() {
            return false;
        }

        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(Path path, Iterable<String> relativePath, FileContentSnapshot content, StringInterner stringInterner) {
            return getNormalizedSnapshot(content);
        }

        @Nullable
        @Override
        public NormalizedFileSnapshot getNormalizedRootSnapshot(Path path, String name, FileContentSnapshot content, StringInterner stringInterner) {
            return getNormalizedSnapshot(content);
        }

        private NormalizedFileSnapshot getNormalizedSnapshot(FileContentSnapshot content) {
            return (content.getType() == FileType.Directory) ? null : new IgnoredPathFileSnapshot(content);
        }
    };

    private static String getRelativePathString(Iterable<String> relativePath) {
        if (Iterables.isEmpty(relativePath)) {
            return "";
        }
        StringBuilder builder = new StringBuilder(256);
        int i = 0;
        for (String segment : relativePath) {
            if (i != 0) {
                builder.append('/');
            }
            builder.append(segment);
            i++;
        }
        return builder.toString();
    }

    public static InputPathNormalizationStrategy valueOf(PathSensitivity pathSensitivity) {
        switch (pathSensitivity) {
            case ABSOLUTE:
                return ABSOLUTE;
            case RELATIVE:
                return RELATIVE;
            case NAME_ONLY:
                return NAME_ONLY;
            case NONE:
                return NONE;
            default:
                throw new IllegalArgumentException("Unknown path usage: " + pathSensitivity);
        }
    }

    /**
     * Creates a relative path while using as little additional memory as possible. If the absolute path and normalized path use the same
     * line separators in their area of overlap, the normalized path is created by remembering the absolute path and an index. Otherwise the
     * normalized path is converted to a String, which takes additional memory.
     */
    static NormalizedFileSnapshot getRelativeSnapshot(Path path, FileContentSnapshot content, CharSequence normalizedPath, StringInterner stringInterner) {
        String absolutePath = path.toString();
        if (lineSeparatorsMatch(absolutePath, normalizedPath)) {
            return new IndexedNormalizedFileSnapshot(absolutePath, absolutePath.length() - normalizedPath.length(), content);
        } else {
            return new DefaultNormalizedFileSnapshot(stringInterner.intern(normalizedPath.toString()), content);
        }
    }

    private static boolean lineSeparatorsMatch(String absolutePath, CharSequence normalizedPath) {
        return GUtil.endsWith(absolutePath, normalizedPath);
    }
}
