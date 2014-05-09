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

package org.gradle.api.internal.externalresource.transport.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.*;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.gradle.api.UncheckedIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Provides some convenience and unified logging.
 */
public class HttpClientHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientHelper.class);
    private final HttpClient client;
    private final BasicHttpContext httpContext = new BasicHttpContext();

    public HttpClientHelper(HttpSettings settings) {
        alwaysUseKeepAliveConnections();

        DefaultHttpClient client = new SystemDefaultHttpClient();
        new HttpClientConfigurer(settings).configure(client);
        this.client = new DecompressingHttpClient(client);
    }

    private void alwaysUseKeepAliveConnections() {
        // HttpClient 4.2.2 does not use the correct default value for "http.keepAlive" system property (default is "true").
        // HttpClient NTLM authentication fails badly when this property value is true.
        // So we force it to be true here: effectively, we're ignoring any user-supplied value for our HttpClient configuration.
        System.setProperty("http.keepAlive", "true");
    }

    public HttpResponse performRawHead(String source) {
        return performRequest(new HttpHead(source));        
    }
    
    public HttpResponse performHead(String source) {
        return processResponse(source, "HEAD", performRawHead(source));
    }

    public HttpResponse performRawGet(String source) {
        return performRequest(new HttpGet(source));
    }

    public HttpResponse performGet(String source) {
        return processResponse(source, "GET", performRawGet(source));
    }

    public HttpResponse performRequest(HttpRequestBase request) {
        String method = request.getMethod();

        HttpResponse response;
        try {
            response = executeGetOrHead(request);
        } catch (IOException e) {
            throw new HttpRequestException(String.format("Could not %s '%s'.", method, request.getURI()), e);
        }

        return response;
    }

    protected HttpResponse executeGetOrHead(HttpRequestBase method) throws IOException {
        HttpResponse httpResponse = performHttpRequest(method);
        // Consume content for non-successful, responses. This avoids the connection being left open.
        if (!wasSuccessful(httpResponse)) {
            EntityUtils.consume(httpResponse.getEntity());
            return httpResponse;
        }
        return httpResponse;
    }

    public boolean wasMissing(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode == 404;
    }

    public boolean wasSuccessful(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    public HttpResponse performHttpRequest(HttpRequestBase request) throws IOException {
        // Without this, HTTP Client prohibits multiple redirects to the same location within the same context
        httpContext.removeAttribute(DefaultRedirectStrategy.REDIRECT_LOCATIONS);

        LOGGER.debug("Performing HTTP {}: {}", request.getMethod(), request.getURI());
        return client.execute(request, httpContext);
    }

    private HttpResponse processResponse(String source, String method, HttpResponse response) {
        if (wasMissing(response)) {
            LOGGER.info("Resource missing. [HTTP {}: {}]", method, source);
            return null;
        }
        if (!wasSuccessful(response)) {
            LOGGER.info("Failed to get resource: {}. [HTTP {}: {}]", new Object[]{method, response.getStatusLine(), source});
            throw new UncheckedIOException(String.format("Could not %s '%s'. Received status code %s from server: %s",
                    method, source, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }

        return response;
    }


}
