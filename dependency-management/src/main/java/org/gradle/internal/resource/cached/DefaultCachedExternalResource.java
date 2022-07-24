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

package org.gradle.internal.resource.cached;

import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.File;
import java.io.Serializable;
import java.util.Date;

public class DefaultCachedExternalResource implements CachedExternalResource, Serializable {
    private final File cachedFile;
    private final long cachedAt;
    private final ExternalResourceMetaData externalResourceMetaData;

    public DefaultCachedExternalResource(File cachedFile, long cachedAt, ExternalResourceMetaData externalResourceMetaData) {
        this.cachedFile = cachedFile;
        this.cachedAt = cachedAt;
        this.externalResourceMetaData = externalResourceMetaData;
    }

    public DefaultCachedExternalResource(long cachedAt) {
        this.cachedAt = cachedAt;

        this.cachedFile = null;
        this.externalResourceMetaData = null;
    }

    @Override
    public boolean isMissing() {
        return cachedFile == null;
    }

    @Override
    public File getCachedFile() {
        return cachedFile;
    }

    @Override
    public long getCachedAt() {
        return cachedAt;
    }

    @Override
    public ExternalResourceMetaData getExternalResourceMetaData() {
        return externalResourceMetaData;
    }

    @Override
    public Date getExternalLastModified() {
        return externalResourceMetaData != null ? externalResourceMetaData.getLastModified() : null;
    }

    @Override
    public long getContentLength() {
        return isMissing() ? -1 : cachedFile.length();
    }

}
