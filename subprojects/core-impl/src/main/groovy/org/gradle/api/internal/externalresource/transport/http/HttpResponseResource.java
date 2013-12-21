/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.externalresource.transport.http;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.util.EntityUtils;
import org.gradle.api.internal.externalresource.AbstractExternalResource;
import org.gradle.api.internal.externalresource.metadata.DefaultExternalResourceMetaData;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.internal.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

class HttpResponseResource extends AbstractExternalResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResponseResource.class);

    private final String method;
    private final String source;
    private final HttpResponse response;
    private final ExternalResourceMetaData metaData;
    private boolean wasOpened;

    public HttpResponseResource(String method, String source, HttpResponse response) {
        this.method = method;
        this.source = source;
        this.response = response;

        String etag = getEtag(response);
        this.metaData = new DefaultExternalResourceMetaData(source, getLastModified(), getContentLength(), etag, getSha1(response, etag));
    }

    public String getName() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("Http %s Resource: %s", method, source);
    }

    public ExternalResourceMetaData getMetaData() {
        return metaData;
    }

    public long getLastModified() {
        Header responseHeader = response.getFirstHeader("last-modified");
        if (responseHeader == null) {
            return 0;
        }
        try {
            return DateUtils.parseDate(responseHeader.getValue()).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public long getContentLength() {
        Header header = response.getFirstHeader(HttpHeaders.CONTENT_LENGTH);
        if (header == null) {
            return -1;            
        }

        String value = header.getValue();
        if (value == null) {
            return -1;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getContentType() {
        final Header header = response.getFirstHeader(HttpHeaders.CONTENT_TYPE);
        return header == null ? null : header.getValue();
    }

    public boolean exists() {
        return true;
    }

    public boolean isLocal() {
        return false;
    }

    public InputStream openStream() throws IOException {
        if(wasOpened){
            throw new IOException("Unable to open Stream as it was opened before.");
        }
        LOGGER.debug("Attempting to download resource {}.", source);
        this.wasOpened = true;
        return response.getEntity().getContent();
    }

    @Override
    public void close() throws IOException {
        EntityUtils.consume(response.getEntity());
    }

    private static String getEtag(HttpResponse response) {
        Header etagHeader = response.getFirstHeader(HttpHeaders.ETAG);
        return etagHeader == null ? null : etagHeader.getValue();
    }
    
    private static HashValue getSha1(HttpResponse response, String etag) {
        Header sha1Header = response.getFirstHeader("X-Checksum-Sha1");
        if (sha1Header != null) {
            return new HashValue(sha1Header.getValue());    
        }

        // Nexus uses sha1 etags, with a constant prefix
        // e.g {SHA1{b8ad5573a5e9eba7d48ed77a48ad098e3ec2590b}}
        if (etag != null && etag.startsWith("{SHA1{")) {
            String hash = etag.substring(6, etag.length() - 2);
            return new HashValue(hash);
        }

        return null;
    }
}
