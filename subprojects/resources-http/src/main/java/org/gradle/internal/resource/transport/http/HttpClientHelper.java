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

import com.google.common.collect.Iterables;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.apache.http.client.protocol.HttpClientContext.REDIRECT_LOCATIONS;

/**
 * Provides some convenience and unified logging.
 */
public class HttpClientHelper implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientHelper.class);
    private CloseableHttpClient client;
    private final HttpSettings settings;

    /**
     * Maintains a queue of contexts which are shared between threads when authentication
     * is activated. When a request is performed, it will pick a context from the queue,
     * and create a new one whenever it's not available (which either means it's the first request
     * or that other requests are being performed concurrently). The queue will grow as big as
     * the max number of concurrent requests executed.
     */
    private final ConcurrentLinkedQueue<HttpContext> sharedContext;

    public HttpClientHelper(HttpSettings settings) {
        this.settings = settings;
        if (!settings.getAuthenticationSettings().isEmpty()) {
            sharedContext = new ConcurrentLinkedQueue<HttpContext>();
        } else {
            sharedContext = null;
        }
    }

    private HttpClientResponse performRawHead(String source, boolean revalidate) {
        return performRequest(new HttpHead(source), revalidate);
    }

    public HttpClientResponse performHead(String source, boolean revalidate) {
        return processResponse(performRawHead(source, revalidate));
    }

    HttpClientResponse performRawGet(String source, boolean revalidate) {
        return performRequest(new HttpGet(source), revalidate);
    }

    public HttpClientResponse performGet(String source, boolean revalidate) {
        return processResponse(performRawGet(source, revalidate));
    }

    public HttpClientResponse performRequest(HttpRequestBase request, boolean revalidate) {
        String method = request.getMethod();
        if (revalidate) {
            request.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0");
        }
        try {
            return executeGetOrHead(request);
        } catch (FailureFromRedirectLocation e) {
            throw new HttpRequestException(String.format("Could not %s '%s'.", method, e.getLastRedirectLocation()), e.getCause());
        } catch (IOException e) {
            throw new HttpRequestException(String.format("Could not %s '%s'.", method, request.getURI()), e);
        }
    }

    protected HttpClientResponse executeGetOrHead(HttpRequestBase method) throws IOException {
        HttpClientResponse response = performHttpRequest(method);
        // Consume content for non-successful, responses. This avoids the connection being left open.
        if (!response.wasSuccessful()) {
            response.close();
        }
        return response;
    }

    public HttpClientResponse performHttpRequest(HttpRequestBase request) throws IOException {
        if (sharedContext == null) {
            // There's no authentication involved, requests can be done concurrently
            return performHttpRequest(request, new BasicHttpContext());
        }
        HttpContext httpContext = nextAvailableSharedContext();
        try {
            return performHttpRequest(request, httpContext);
        } finally {
            sharedContext.add(httpContext);
        }
    }

    private HttpContext nextAvailableSharedContext() {
        HttpContext context = sharedContext.poll();
        if (context == null) {
            return new BasicHttpContext();
        }
        return context;
    }

    private HttpClientResponse performHttpRequest(HttpRequestBase request, HttpContext httpContext) throws IOException {
        // Without this, HTTP Client prohibits multiple redirects to the same location within the same context
        httpContext.removeAttribute(REDIRECT_LOCATIONS);
        LOGGER.debug("Performing HTTP {}: {}", request.getMethod(), request.getURI());
        try {
            CloseableHttpResponse response = getClient().execute(request, httpContext);
            return toHttpClientResponse(request, httpContext, response);
        } catch (IOException e) {
            URI lastRedirectLocation = getLastRedirectLocation(httpContext);
            throw (lastRedirectLocation == null) ? e : new FailureFromRedirectLocation(lastRedirectLocation, e);
        }
    }

    private HttpClientResponse toHttpClientResponse(HttpRequestBase request, HttpContext httpContext, CloseableHttpResponse response) {
        URI lastRedirectLocation = getLastRedirectLocation(httpContext);
        URI effectiveUri = lastRedirectLocation == null ? request.getURI() : lastRedirectLocation;
        return new HttpClientResponse(request.getMethod(), effectiveUri, response);
    }

    @SuppressWarnings("unchecked")
    private URI getLastRedirectLocation(HttpContext httpContext) {
        List<URI> redirectLocations = (List<URI>) httpContext.getAttribute(REDIRECT_LOCATIONS);
        return (redirectLocations == null || redirectLocations.isEmpty()) ? null : Iterables.getLast(redirectLocations);
    }

    private HttpClientResponse processResponse(HttpClientResponse response) {
        if (response.wasMissing()) {
            LOGGER.info("Resource missing. [HTTP {}: {}]", response.getMethod(), response.getEffectiveUri());
            return null;
        }
        if (!response.wasSuccessful()) {
            URI effectiveUri = response.getEffectiveUri();
            LOGGER.info("Failed to get resource: {}. [HTTP {}: {})]", response.getMethod(), response.getStatusLine(), effectiveUri);
            throw new HttpErrorStatusCodeException(response.getMethod(), effectiveUri.toString(), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
        }

        return response;
    }

    private synchronized CloseableHttpClient getClient() {
        if (client == null) {
            HttpClientBuilder builder = HttpClientBuilder.create();
            new HttpClientConfigurer(settings).configure(builder);
            this.client = builder.build();
        }
        return client;
    }

    @Override
    public synchronized void close() throws IOException {
        if (client != null) {
            client.close();
            if (sharedContext != null) {
                sharedContext.clear();
            }
        }
    }

    private static class FailureFromRedirectLocation extends IOException {
        private final URI lastRedirectLocation;

        private FailureFromRedirectLocation(URI lastRedirectLocation, Throwable cause) {
            super(cause);
            this.lastRedirectLocation = lastRedirectLocation;
        }

        private URI getLastRedirectLocation() {
            return lastRedirectLocation;
        }
    }

}
