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

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.nativeintegration.filesystem.FileType;

public enum TaskFilePropertySnapshotNormalizationStrategy implements SnapshotNormalizationStrategy {
    /**
     * Use the absolute path of the files.
     */
    ABSOLUTE {
        @Override
        public boolean isPathAbsolute() {
            return true;
        }

        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileDetails fileDetails, StringInterner stringInterner) {
            return new NonNormalizedFileSnapshot(fileDetails.getPath(), fileDetails.getContent());
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

        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileDetails fileDetails, StringInterner stringInterner) {
            // Ignore path of root directories
            if (fileDetails.isRoot() && fileDetails.getType() == FileType.Directory) {
                return new IgnoredPathFileSnapshot(fileDetails.getContent());
            }
            return getRelativeSnapshot(fileDetails, fileDetails.getContent(), stringInterner);
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

        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileDetails fileDetails, StringInterner stringInterner) {
            // Ignore path of root directories
            if (fileDetails.isRoot() && fileDetails.getType() == FileType.Directory) {
                return new IgnoredPathFileSnapshot(fileDetails.getContent());
            }
            return getRelativeSnapshot(fileDetails, fileDetails.getName(), fileDetails.getContent(), stringInterner);
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
        public NormalizedFileSnapshot getNormalizedSnapshot(FileDetails fileDetails, StringInterner stringInterner) {
            if (fileDetails.getType() == FileType.Directory) {
                return null;
            }
            return new IgnoredPathFileSnapshot(fileDetails.getContent());
        }
    };

    public static TaskFilePropertySnapshotNormalizationStrategy valueOf(PathSensitivity pathSensitivity) {
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

    public static NormalizedFileSnapshot getRelativeSnapshot(FileDetails fileDetails, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
        String[] segments = fileDetails.getRelativePath().getSegments();
        StringBuilder builder = new StringBuilder();
        for (int i = 0, len = segments.length; i < len; i++) {
            if (i != 0) {
                builder.append('/');
            }
            builder.append(segments[i]);
        }
        return getRelativeSnapshot(fileDetails, builder.toString(), snapshot, stringInterner);
    }

    public static NormalizedFileSnapshot getRelativeSnapshot(FileDetails fileDetails, String normalizedPath, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
        String absolutePath = fileDetails.getPath();
        if (absolutePath.endsWith(normalizedPath)) {
            return new IndexedNormalizedFileSnapshot(absolutePath, absolutePath.length() - normalizedPath.length(), snapshot);
        } else {
            return new DefaultNormalizedFileSnapshot(stringInterner.intern(normalizedPath), snapshot);
        }
    }

}
