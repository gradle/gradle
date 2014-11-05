/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.outcome.internal.archive.entry;

import com.google.common.collect.ImmutableSet;
import org.gradle.util.ConfigureUtil;

import java.util.Collection;
import java.util.Map;

public class ArchiveEntry {

    private final String sortPath;
    private final String path;
    private final boolean directory;
    private final long size;
    private final long crc;
    private final ImmutableSet<ArchiveEntry> subEntries;

    private ArchiveEntry(String sortPath, String path, boolean directory, long size, long crc, ImmutableSet<ArchiveEntry> subEntries) {
        this.sortPath = sortPath;
        this.path = path;
        this.directory = directory;
        this.size = size;
        this.crc = crc;
        this.subEntries = subEntries;
    }

    public static ArchiveEntry of(Map<String, ?> map) {
        return ConfigureUtil.configureByMap(map, new Builder()).build();
    }

    public String getSortPath() {
        return sortPath;
    }

    public String getPath() {
        return path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public long getSize() {
        return size;
    }

    public long getCrc() {
        return crc;
    }

    public ImmutableSet<ArchiveEntry> getSubEntries() {
        return subEntries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ArchiveEntry that = (ArchiveEntry) o;

        if (!path.equals(that.path)) {
            return false;
        }

        if (directory != that.directory) {
            return false;
        }

        if (!subEntries.equals(that.subEntries)) {
            return false;
        }

        if (subEntries.isEmpty()) {
            if (crc != that.crc) {
                return false;
            }
            if (size != that.size) {
                return false;
            }
        } else {
            return subEntries.equals(that.subEntries);
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = path != null ? path.hashCode() : 0;
        result = 31 * result + (directory ? 1 : 0);
        if (subEntries.isEmpty()) {
            result = 31 * result + (int) (size ^ (size >>> 32));
            result = 31 * result + (int) (crc ^ (crc >>> 32));
        }
        result = 31 * result + subEntries.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "ArchiveEntry{"
                + "sortPath='" + sortPath + '\''
                + ", path='" + path + '\''
                + ", directory=" + directory
                + ", size=" + size
                + ", crc=" + crc
                + ", subEntries=" + subEntries
                + '}';
    }

    public static class Builder {

        private String sortPath;
        private String path;
        private boolean directory;
        private long size;
        private long crc;
        private ImmutableSet<ArchiveEntry> subEntries = ImmutableSet.of();

        public Builder setSortPath(String sortPath) {
            this.sortPath = sortPath;
            return this;
        }

        public String getSortPath() {
            return sortPath;
        }

        public String getPath() {
            return path;
        }

        public boolean isDirectory() {
            return directory;
        }

        public long getSize() {
            return size;
        }

        public long getCrc() {
            return crc;
        }

        public Collection<ArchiveEntry> getSubEntries() {
            return subEntries;
        }

        public Builder setPath(String path) {
            this.path = path;
            return this;
        }

        public Builder setDirectory(boolean directory) {
            this.directory = directory;
            return this;
        }

        public Builder setSize(long size) {
            this.size = size;
            return this;
        }

        public Builder setCrc(long crc) {
            this.crc = crc;
            return this;
        }

        public Builder setSubEntries(ImmutableSet<ArchiveEntry> subEntries) {
            this.subEntries = subEntries;
            return this;
        }

        public ArchiveEntry build() {
            if (path == null) {
                throw new IllegalStateException("'path' is required");
            }
            if (subEntries == null) {
                throw new IllegalStateException("'subEntries' is required");
            }
            if (directory && !subEntries.isEmpty()) {
                throw new IllegalStateException("directory entry cannot have sub entries");
            }

            if (sortPath == null) {
                sortPath = path;
            }

            return new ArchiveEntry(sortPath, path, directory, size, crc, subEntries);
        }
    }
}
