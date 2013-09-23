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

package org.gradle.api.internal.externalresource.cached;

import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;

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

    public boolean isMissing() {
        return cachedFile == null;
    }

    public File getCachedFile() {
        return cachedFile;
    }

    public long getCachedAt() {
        return cachedAt;
    }

    public ExternalResourceMetaData getExternalResourceMetaData() {
        return externalResourceMetaData;
    }

    public Date getExternalLastModified() {
        return externalResourceMetaData != null ? externalResourceMetaData.getLastModified() : null;
    }

    public long getExternalLastModifiedAsTimestamp() {
        Date externalLastModified = getExternalLastModified();
        return externalLastModified == null ? -1 : externalLastModified.getTime();
    }

    public long getContentLength() {
        return isMissing() ? -1 : cachedFile.length();
    }

}
