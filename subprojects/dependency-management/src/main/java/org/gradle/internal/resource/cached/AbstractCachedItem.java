/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.resource.cached;

import java.io.File;
import java.io.Serializable;

public abstract class AbstractCachedItem implements CachedItem, Serializable {
    private final File cachedFile;
    private final long cachedAt;
    private final long cachedFileLastModified;
    private final long cachedFileSize;

    public AbstractCachedItem(File cachedFile, long cachedAt,
        long cachedFileLastModified, long cachedFileSize) {
        this.cachedFile = cachedFile;
        this.cachedAt = cachedAt;
        this.cachedFileLastModified = cachedFileLastModified;
        this.cachedFileSize = cachedFileSize;
    }

    public AbstractCachedItem(long cachedAt) {
        this.cachedAt = cachedAt;

        this.cachedFile = null;
        this.cachedFileLastModified = -1;
        this.cachedFileSize = -1;
    }

    public boolean isMissing() {
        return cachedFile == null;
    }

    public File getCachedFile() {
        return cachedFile;
    }

    public long getCachedAt() {
        return cachedAt;
    }

    public long getCachedFileLastModified() {
        return cachedFileLastModified;
    }

    public long getCachedFileSize() {
        return cachedFileSize;
    }

    public boolean isLocalFileUnchanged() {
        if (isMissing()) {
            return getCachedFile() == null
                && getCachedFileLastModified() == -1
                && getCachedFileSize() == -1;
        }

        return getCachedFile() != null
            && getCachedFileLastModified() == getCachedFile().lastModified()
            && getCachedFileSize() == getCachedFile().length();
    }

}
