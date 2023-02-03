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

package org.gradle.internal.resource.metadata;

import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Date;

public class DefaultExternalResourceMetaData implements ExternalResourceMetaData {
    private final URI location;
    private final Date lastModified;
    private final long contentLength;
    private final String etag;
    private final HashCode sha1;
    private final String contentType;

    private final String fileName;
    private final boolean wasMissing;

    public DefaultExternalResourceMetaData(URI location, long lastModified, long contentLength) {
        this(location, lastModified > 0 ? new Date(lastModified) : null, contentLength, null, null, null, null, false);
    }

    public DefaultExternalResourceMetaData(URI location, long lastModified, long contentLength, @Nullable String contentType, @Nullable String etag, @Nullable HashCode sha1) {
        this(location, lastModified > 0 ? new Date(lastModified) : null, contentLength, contentType, etag, sha1, null, false);
    }

    public DefaultExternalResourceMetaData(URI location, @Nullable Date lastModified, long contentLength, @Nullable String contentType, @Nullable String etag, @Nullable HashCode sha1, @Nullable String fileName, boolean wasMissing) {
        this.location = location;
        this.lastModified = lastModified;
        this.contentLength = contentLength;
        this.contentType = contentType;
        this.etag = etag;
        this.sha1 = sha1;
        this.fileName = fileName;
        this.wasMissing = wasMissing;
    }

    @Override
    public URI getLocation() {
        return location;
    }

    @Nullable
    @Override
    public Date getLastModified() {
        return lastModified;
    }

    @Override
    public long getContentLength() {
        return contentLength;
    }

    @Nullable
    @Override
    public String getContentType() {
        return contentType;
    }

    @Nullable
    @Override
    public String getEtag() {
        return etag;
    }

    @Nullable
    @Override
    public HashCode getSha1() {
        return sha1;
    }

    @Override
    public boolean wasMissing() {
        return wasMissing;
    }

    @Nullable
    @Override
    public String getFilename() {
        return fileName;
    }
}
