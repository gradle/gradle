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
package org.gradle.internal.resource.transport.http;

import org.apache.http.HttpHeaders;
import org.apache.http.client.utils.DateUtils;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class HttpResponseResource implements ExternalResourceReadResponse {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResponseResource.class);

    private final String method;
    private final URI source;
    private final HttpClientResponse response;
    private final ExternalResourceMetaData metaData;
    private boolean wasOpened;

    public HttpResponseResource(String method, URI source, HttpClientResponse response) {
        this.method = method;
        this.source = source;
        this.response = response;

        String etag = getEtag(response);
        this.metaData = new DefaultExternalResourceMetaData(source, getLastModified(), getContentLength(), getContentType(), etag, getSha1(response, etag));
    }

    public URI getURI() {
        return source;
    }

    @Override
    public String toString() {
        return "Http " + method + " Resource: " + source;
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        return metaData;
    }

    public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
    }

    public long getLastModified() {
        String responseHeader = response.getHeader(HttpHeaders.LAST_MODIFIED);
        if (responseHeader == null) {
            return 0;
        }
        try {
            return DateUtils.parseDate(responseHeader).getTime();
        } catch (Exception e) {
            return 0;
        }
    }

    public long getContentLength() {
        String header = response.getHeader(HttpHeaders.CONTENT_LENGTH);
        if (header == null) {
            return -1;
        }

        try {
            return Long.parseLong(header);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getHeaderValue(String name) {
        return response.getHeader(name);
    }

    public String getContentType() {
        return response.getHeader(HttpHeaders.CONTENT_TYPE);
    }

    public boolean isLocal() {
        return false;
    }

    @Override
    public InputStream openStream() throws IOException {
        if (wasOpened) {
            throw new IOException("Unable to open Stream as it was opened before.");
        }
        LOGGER.debug("Attempting to download resource {}.", source);
        this.wasOpened = true;
        return response.getContent();
    }

    @Override
    public void close() {
        response.close();
    }

    private static String getEtag(HttpClientResponse response) {
        return response.getHeader(HttpHeaders.ETAG);
    }

    private static HashCode getSha1(HttpClientResponse response, String etag) {
        String sha1Header = response.getHeader("X-Checksum-Sha1");
        if (sha1Header != null) {
            return HashCode.fromString(sha1Header);
        }

        // Nexus uses sha1 etags, with a constant prefix
        // e.g {SHA1{b8ad5573a5e9eba7d48ed77a48ad098e3ec2590b}}
        if (etag != null && etag.startsWith("{SHA1{")) {
            String hash = etag.substring(6, etag.length() - 2);
            return HashCode.fromString(hash);
        }

        return null;
    }
}
