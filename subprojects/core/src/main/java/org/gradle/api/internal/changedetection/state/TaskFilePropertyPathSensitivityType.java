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

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

public enum TaskFilePropertyPathSensitivityType {
    /**
     * Use the absolute path of the files.
     */
    ABSOLUTE {
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileTreeElement fileDetails, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
            final String absolutePath = stringInterner.intern(fileDetails.getFile().getAbsolutePath());
            return new NonNormalizedFileSnapshot(absolutePath, snapshot);
        }
    },

    /**
     * Use the location of the file related to a hierarchy.
     */
    RELATIVE {
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileTreeElement fileDetails, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
            return getRelativeSnapshot(fileDetails, fileDetails.getPath(), snapshot, stringInterner);
        }
    },

    /**
     * Use the file name only.
     */
    NAME_ONLY {
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileTreeElement fileDetails, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
            return getRelativeSnapshot(fileDetails, fileDetails.getName(), snapshot, stringInterner);
        }
    },

    /**
     * Ignore the file path completely.
     */
    NONE {
        @Override
        public NormalizedFileSnapshot getNormalizedSnapshot(FileTreeElement fileDetails, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
            return new IgnoredPathFileSnapshot(snapshot);
        }
    };

    private static final String EMPTY_STRING = "";

    public abstract NormalizedFileSnapshot getNormalizedSnapshot(FileTreeElement fileDetails, IncrementalFileSnapshot snapshot, StringInterner stringInterner);

    public static TaskFilePropertyPathSensitivityType valueOf(PathSensitivity pathSensitivity) {
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

    private static NormalizedFileSnapshot getRelativeSnapshot(FileTreeElement fileDetails, String normalizedPath, IncrementalFileSnapshot snapshot, StringInterner stringInterner) {
        String absolutePath = stringInterner.intern(fileDetails.getFile().getAbsolutePath());
        if (absolutePath.endsWith(normalizedPath)) {
            return new IndexedNormalizedFileSnapshot(absolutePath, absolutePath.length() - normalizedPath.length(), snapshot);
        } else {
            return new DefaultNormalizedFileSnapshot(stringInterner.intern(normalizedPath), snapshot);
        }
    }

    public static class NonNormalizedFileSnapshot extends AbstractNormalizedFileSnapshot {
        private final String absolutePath;

        public NonNormalizedFileSnapshot(String absolutePath, IncrementalFileSnapshot snapshot) {
            super(snapshot);
            this.absolutePath = absolutePath;
        }

        @Override
        public String getNormalizedPath() {
            return absolutePath;
        }
    }

    public static class IndexedNormalizedFileSnapshot extends AbstractNormalizedFileSnapshot {
        private final String absolutePath;
        private final int index;

        public IndexedNormalizedFileSnapshot(String absolutePath, int index, IncrementalFileSnapshot snapshot) {
            super(snapshot);
            this.absolutePath = absolutePath;
            this.index = index;
        }

        @Override
        public String getNormalizedPath() {
            return absolutePath.substring(index);
        }

        public String getAbsolutePath() {
            return absolutePath;
        }

        public int getIndex() {
            return index;
        }
    }

    public static class DefaultNormalizedFileSnapshot extends AbstractNormalizedFileSnapshot {
        private final String normalizedPath;

        public DefaultNormalizedFileSnapshot(String normalizedPath, IncrementalFileSnapshot snapshot) {
            super(snapshot);
            this.normalizedPath = normalizedPath;
        }

        @Override
        public String getNormalizedPath() {
            return normalizedPath;
        }
    }

    public static class IgnoredPathFileSnapshot implements NormalizedFileSnapshot {
        private final IncrementalFileSnapshot snapshot;

        public IgnoredPathFileSnapshot(IncrementalFileSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public String getNormalizedPath() {
            return EMPTY_STRING;
        }

        @Override
        public IncrementalFileSnapshot getSnapshot() {
            return snapshot;
        }

        @Override
        public void appendToCacheKey(TaskCacheKeyBuilder builder) {
            builder.putBytes(snapshot.getHash().asBytes());
        }

        @Override
        public int compareTo(NormalizedFileSnapshot o) {
            if (!(o instanceof IgnoredPathFileSnapshot)) {
                return -1;
            }
            return AbstractNormalizedFileSnapshot.compareHashCodes(this, o);
        }
    }
}
