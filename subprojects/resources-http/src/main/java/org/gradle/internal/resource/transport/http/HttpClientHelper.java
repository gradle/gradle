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

package org.gradle.internal.resource.transport.http;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.gradle.api.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.Locale;

/**
 * Provides some convenience and unified logging.
 */
public class HttpClientHelper implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientHelper.class);
    private CloseableHttpClient client;
    private final BasicHttpContext httpContext = new BasicHttpContext();
    private final HttpSettings settings;

    public HttpClientHelper(HttpSettings settings) {
        this.settings = settings;
    }

    public CloseableHttpResponse performRawHead(String source, boolean revalidate) {
        return performRequest(new HttpHead(source), revalidate);
    }

    public CloseableHttpResponse performHead(String source, boolean revalidate) {
        return processResponse(source, "HEAD", performRawHead(source, revalidate));
    }

    public CloseableHttpResponse performRawGet(String source, boolean revalidate) {
        return performRequest(new HttpGet(source), revalidate);
    }

    public CloseableHttpResponse performGet(String source, boolean revalidate) {
        return processResponse(source, "GET", performRawGet(source, revalidate));
    }

    public CloseableHttpResponse performRequest(HttpRequestBase request, boolean revalidate) {
        String method = request.getMethod();
        if (revalidate) {
            request.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0");
        }
        CloseableHttpResponse response;
        try {
            response = executeGetOrHead(request);
        } catch (IOException e) {
            throw new HttpRequestException(String.format("Could not %s '%s'.", method, request.getURI()), e);
        }

        return response;
    }

    protected CloseableHttpResponse executeGetOrHead(HttpRequestBase method) throws IOException {
        final CloseableHttpResponse httpResponse = performHttpRequest(method);
        // Consume content for non-successful, responses. This avoids the connection being left open.
        if (!wasSuccessful(httpResponse)) {
            CloseableHttpResponse response = new AutoClosedHttpResponse(httpResponse);
            HttpClientUtils.closeQuietly(httpResponse);
            return response;
        }
        return httpResponse;
    }

    public boolean wasMissing(CloseableHttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode == 404;
    }

    public boolean wasSuccessful(CloseableHttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode >= 200 && statusCode < 400;
    }

    public CloseableHttpResponse performHttpRequest(HttpRequestBase request) throws IOException {
        // Without this, HTTP Client prohibits multiple redirects to the same location within the same context
        httpContext.removeAttribute(HttpClientContext.REDIRECT_LOCATIONS);
        LOGGER.debug("Performing HTTP {}: {}", request.getMethod(), request.getURI());
        return getClient().execute(request, httpContext);
    }

    private CloseableHttpResponse processResponse(String source, String method, CloseableHttpResponse response) {
        if (wasMissing(response)) {
            LOGGER.info("Resource missing. [HTTP {}: {}]", method, source);
            return null;
        }
        if (!wasSuccessful(response)) {
            LOGGER.info("Failed to get resource: {}. [HTTP {}: {}]", method, response.getStatusLine(), source);
            throw new UncheckedIOException(String.format("Could not %s '%s'. Received status code %s from server: %s",
                method, source, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }

        return response;
    }

    private synchronized CloseableHttpClient getClient() {
        if (client == null) {
            HttpClientBuilder builder = HttpClientBuilder.create();
            builder.setRedirectStrategy(new AlwaysRedirectRedirectStrategy());
            new HttpClientConfigurer(settings).configure(builder);
            this.client = builder.build();
        }
        return client;
    }

    @Override
    public synchronized void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

    private static class AutoClosedHttpResponse implements CloseableHttpResponse {
        private final HttpEntity entity;
        private final CloseableHttpResponse httpResponse;

        public AutoClosedHttpResponse(CloseableHttpResponse httpResponse) throws IOException {
            this.httpResponse = httpResponse;
            HttpEntity entity = httpResponse.getEntity();
            this.entity = entity !=null ? new BufferedHttpEntity(entity) : null;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public StatusLine getStatusLine() {
            return httpResponse.getStatusLine();
        }

        @Override
        public void setStatusLine(StatusLine statusline) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setStatusLine(ProtocolVersion ver, int code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setStatusLine(ProtocolVersion ver, int code, String reason) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setStatusCode(int code) throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setReasonPhrase(String reason) throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpEntity getEntity() {
            return entity;
        }

        @Override
        public void setEntity(HttpEntity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Locale getLocale() {
            return httpResponse.getLocale();
        }

        @Override
        public void setLocale(Locale loc) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return httpResponse.getProtocolVersion();
        }

        @Override
        public boolean containsHeader(String name) {
            return httpResponse.containsHeader(name);
        }

        @Override
        public Header[] getHeaders(String name) {
            return httpResponse.getHeaders(name);
        }

        @Override
        public Header getFirstHeader(String name) {
            return httpResponse.getFirstHeader(name);
        }

        @Override
        public Header getLastHeader(String name) {
            return httpResponse.getLastHeader(name);
        }

        @Override
        public Header[] getAllHeaders() {
            return httpResponse.getAllHeaders();
        }

        @Override
        public void addHeader(Header header) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addHeader(String name, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setHeader(Header header) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setHeader(String name, String value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setHeaders(Header[] headers) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeHeader(Header header) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeHeaders(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HeaderIterator headerIterator() {
            return httpResponse.headerIterator();
        }

        @Override
        public HeaderIterator headerIterator(String name) {
            return httpResponse.headerIterator(name);
        }

        @Override
        @SuppressWarnings("deprecation")
        public org.apache.http.params.HttpParams getParams() {
            return httpResponse.getParams();
        }

        @Override
        @SuppressWarnings("deprecation")
        public void setParams(org.apache.http.params.HttpParams params) {
            throw new UnsupportedOperationException();
        }
    }
}
