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

import java.util.Collection;
import java.util.Collections;

public class ArchiveEntry {

    private String sortPath;
    private String path;
    private boolean directory;
    private long size = -1;
    private long crc = -1;
    private Collection<ArchiveEntry> subEntries;

    public String getSortPath() {
        return sortPath;
    }

    public void setSortPath(String sortPath) {
        this.sortPath = sortPath;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isDirectory() {
        return directory;
    }

    public void setDirectory(boolean directory) {
        this.directory = directory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getCrc() {
        return crc;
    }

    public void setCrc(long crc) {
        this.crc = crc;
    }

    public Collection<ArchiveEntry> getSubEntries() {
        return subEntries == null ? Collections.<ArchiveEntry>emptyList() : subEntries;
    }

    public void setSubEntries(Collection<ArchiveEntry> subEntries) {
        this.subEntries = subEntries;
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

        if (subEntries != null ? !subEntries.equals(that.subEntries) : that.subEntries != null) {
            return false;
        }
        // if there are subEntries and they are equal, ignore the crc
        if ((crc != that.crc) && (subEntries == null)) {
            return false;
        }
        if (directory != that.directory) {
            return false;
        }
        // if there are subEntries and they are equal, ignore the size
        if ((size != that.size) && (subEntries == null)) {
            return false;
        }
        //noinspection RedundantIfStatement
        if (path != null ? !path.equals(that.path) : that.path != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = path != null ? path.hashCode() : 0;
        result = 31 * result + (directory ? 1 : 0);
        // if there are subEntries and they are equal, ignore the size and crc
        if (subEntries == null) {
            result = 31 * result + (int) (size ^ (size >>> 32));
            result = 31 * result + (int) (crc ^ (crc >>> 32));
        }
        result = 31 * result + (subEntries != null ? subEntries.hashCode() : 0);
        return result;
    }
}
