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

package org.gradle.cache.tasks.http;

import org.apache.commons.lang.IncompleteArgumentException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.gradle.api.internal.tasks.cache.TaskCacheKey;
import org.gradle.api.internal.tasks.cache.TaskOutputCache;
import org.gradle.api.internal.tasks.cache.TaskOutputReader;
import org.gradle.api.internal.tasks.cache.TaskOutputWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public class HttpTaskOutputCache implements TaskOutputCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpTaskOutputCache.class);

    private final URI root;

    public HttpTaskOutputCache(URI root) {
        if (!root.getPath().endsWith("/")) {
            throw new IncompleteArgumentException("HTTP cache root URI must end with '/'");
        }
        this.root = root;
    }

    @Override
    public boolean load(TaskCacheKey key, TaskOutputReader reader) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            final URI uri = root.resolve("./" + key.getHashCode());
            HttpGet httpGet = new HttpGet(uri);
            final CloseableHttpResponse response = httpClient.execute(httpGet);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for GET {}: {}", uri, response.getStatusLine());
            }
            try {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    reader.readFrom(response.getEntity().getContent());
                    return true;
                } else {
                    return false;
                }
            } finally {
                response.close();
            }
        } finally {
            httpClient.close();
        }
    }

    @Override
    public void store(TaskCacheKey key, final TaskOutputWriter output) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
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
            CloseableHttpResponse response = httpClient.execute(httpPut);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for PUT {}: {}", uri, response.getStatusLine());
            }
        } finally {
            httpClient.close();
        }
    }

    @Override
    public String getDescription() {
        return "HTTP cache at " + root;
    }
}
