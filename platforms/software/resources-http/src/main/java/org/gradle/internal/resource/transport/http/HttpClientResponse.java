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

package org.gradle.internal.resource.transport.http;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.HttpClientUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

public class HttpClientResponse implements Closeable {

    private final String method;
    private final URI effectiveUri;
    private final CloseableHttpResponse httpResponse;
    private boolean closed;

    HttpClientResponse(String method, URI effectiveUri, CloseableHttpResponse httpResponse) {
        this.method = method;
        this.effectiveUri = effectiveUri;
        this.httpResponse = httpResponse;
    }

    public String getHeader(String name) {
        Header header = httpResponse.getFirstHeader(name);
        return header == null ? null : header.getValue();
    }

    public InputStream getContent() throws IOException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) {
            throw new IOException(String.format("Response %d: %s has no content!", getStatusLine().getStatusCode(), getStatusLine().getReasonPhrase()));
        }
        return entity.getContent();
    }

    public StatusLine getStatusLine() {
        return httpResponse.getStatusLine();
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            HttpClientUtils.closeQuietly(httpResponse);
        }
    }

    String getMethod() {
        return method;
    }

    URI getEffectiveUri() {
        return effectiveUri;
    }

    boolean wasSuccessful() {
        int statusCode = getStatusLine().getStatusCode();
        return statusCode >= 200 && statusCode < 400;
    }

    boolean wasMissing() {
        int statusCode = getStatusLine().getStatusCode();
        return statusCode == 404;
    }
}
