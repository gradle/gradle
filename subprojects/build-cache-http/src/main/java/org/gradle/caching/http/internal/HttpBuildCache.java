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

package org.gradle.caching.http.internal;

import org.apache.commons.lang.IncompleteArgumentException;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.gradle.caching.BuildCache;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * Build cache implementation that delegates to a service accessible via HTTP.
 *
 * <p>Cache entries are loaded via {@literal GET} and stored via {@literal PUT} requests.</p>
 */
public class HttpBuildCache implements BuildCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpBuildCache.class);

    private final URI root;
    private final CloseableHttpClient httpClient;

    public HttpBuildCache(URI root) {
        if (!root.getPath().endsWith("/")) {
            throw new IncompleteArgumentException("HTTP cache root URI must end with '/'");
        }
        this.root = root;
        this.httpClient = HttpClients.createDefault();
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        final URI uri = root.resolve("./" + key.getHashCode());
        HttpGet httpGet = new HttpGet(uri);
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(httpGet);
            StatusLine statusLine = response.getStatusLine();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for GET {}: {}", uri, statusLine);
            }
            int statusCode = statusLine.getStatusCode();
            if (statusCode >= 200 && statusCode < 300) {
                reader.readFrom(response.getEntity().getContent());
                return true;
            } else if (statusCode == 404) {
                return false;
            } else {
                throw new BuildCacheException(String.format("HTTP cache returned status %d: %s for key '%s' from %s", statusCode, statusLine.getReasonPhrase(), key, getDescription()));
            }
        } catch (IOException e) {
            // TODO: We should consider different types of exceptions as fatal/recoverable.
            // Right now, everything is considered recoverable.
            throw new BuildCacheException(String.format("loading key '%s' from %s", key, getDescription()), e);
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    @Override
    public void store(BuildCacheKey key, final BuildCacheEntryWriter output) throws BuildCacheException {
        final URI uri = root.resolve(key.getHashCode());
        HttpPut httpPut = new HttpPut(uri);
        httpPut.setEntity(new AbstractHttpEntity() {
            @Override
            public boolean isRepeatable() {
                return true;
            }

            @Override
            public long getContentLength() {
                return -1;
            }

            @Override
            public InputStream getContent() throws IOException, UnsupportedOperationException {
                throw new UnsupportedOperationException();
            }

            @Override
            public void writeTo(OutputStream outstream) throws IOException {
                output.writeTo(outstream);
            }

            @Override
            public boolean isStreaming() {
                return false;
            }
        });
        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(httpPut);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for PUT {}: {}", uri, response.getStatusLine());
            }
        } catch (IOException e) {
            // TODO: We should consider different types of exceptions as fatal/recoverable.
            // Right now, everything is considered recoverable.
            throw new BuildCacheException(String.format("storing key '%s' in %s", key, getDescription()), e);
        } finally {
            HttpClientUtils.closeQuietly(response);
        }
    }

    @Override
    public String getDescription() {
        return "HTTP cache at " + root;
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }
}
