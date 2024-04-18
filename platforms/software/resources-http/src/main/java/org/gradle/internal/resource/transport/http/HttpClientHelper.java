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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.Closeable;
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.lang.String.join;
import static org.apache.http.client.protocol.HttpClientContext.REDIRECT_LOCATIONS;

/**
 * Provides some convenience and unified logging.
 */
public class HttpClientHelper implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientHelper.class);
    private CloseableHttpClient client;
    private final DocumentationRegistry documentationRegistry;
    private final HttpSettings settings;

    private Collection<String> supportedTlsVersions;

    /**
     * Maintains a queue of contexts which are shared between threads when authentication
     * is activated. When a request is performed, it will pick a context from the queue,
     * and create a new one whenever it's not available (which either means it's the first request
     * or that other requests are being performed concurrently). The queue will grow as big as
     * the max number of concurrent requests executed.
     */
    private final ConcurrentLinkedQueue<HttpContext> sharedContext;

    /**
     * Use {@link HttpClientHelper.Factory#create(HttpSettings)} to instantiate instances.
     */
    @VisibleForTesting
    HttpClientHelper(DocumentationRegistry documentationRegistry, HttpSettings settings) {
        this.documentationRegistry = documentationRegistry;
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

    @Nonnull
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
            throw createHttpRequestException(method, e.getCause(), e.getLastRedirectLocation());
        } catch (IOException e) {
            throw createHttpRequestException(method, wrapWithExplanation(e), request.getURI());
        }
    }

    @Nonnull
    private static HttpRequestException createHttpRequestException(String method, Throwable cause, URI uri) {
        return new HttpRequestException(String.format("Could not %s '%s'.", method, stripUserCredentials(uri)), cause);
    }

    private Exception wrapWithExplanation(IOException e) {
        if (e instanceof SocketException || (e instanceof SSLException && e.getMessage().contains("readHandshakeRecord"))) {
            return new HttpRequestException("Got socket exception during request. It might be caused by SSL misconfiguration", e);
        }

        if (!(e instanceof SSLHandshakeException)) {
            return e;
        }

        SSLHandshakeException sslException = (SSLHandshakeException) e;
        String message;

        if (e.getMessage().contains("PKIX path building failed") || e.getMessage().contains("certificate_unknown")) {
            message = "Got SSL handshake exception during request. It might be caused by SSL misconfiguration";
        } else {
            message = String.format(
                "The server %s not support the client's requested TLS protocol versions: (%s). " +
                    "You may need to configure the client to allow other protocols to be used. " +
                    "%s",
                getConfidenceNote(sslException),
                join(", ", supportedTlsVersions),
                documentationRegistry.getDocumentationRecommendationFor("on this", "build_environment", "sec:gradle_system_properties")
            );
        }
        return new HttpRequestException(message, e);
    }

    @Nonnull
    private static String getConfidenceNote(SSLHandshakeException sslException) {
        if (sslException.getMessage() != null && sslException.getMessage().contains("protocol_version")) {
            // If we're handling an SSLHandshakeException with the error of 'protocol_version' we know that the server doesn't support this protocol.
            return "does";
        }
        // Sometimes the SSLHandshakeException doesn't include the 'protocol_version', even though this is the cause of the error.
        // Tell the user this but with less confidence.
        return "may";
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
        LOGGER.debug("Performing HTTP {}: {}", request.getMethod(), stripUserCredentials(request.getURI()));

        try {
            CloseableHttpResponse response = getClient().execute(request, httpContext);
            return toHttpClientResponse(request, httpContext, response);
        } catch (IOException e) {
            validateRedirectChain(httpContext);
            URI lastRedirectLocation = stripUserCredentials(getLastRedirectLocation(httpContext));
            throw (lastRedirectLocation == null) ? e : new FailureFromRedirectLocation(lastRedirectLocation, e);
        }
    }

    private HttpClientResponse toHttpClientResponse(HttpRequestBase request, HttpContext httpContext, CloseableHttpResponse response) {
        validateRedirectChain(httpContext);
        URI lastRedirectLocation = getLastRedirectLocation(httpContext);
        URI effectiveUri = lastRedirectLocation == null ? request.getURI() : lastRedirectLocation;
        return new HttpClientResponse(request.getMethod(), effectiveUri, response);
    }

    /**
     * Validates that no redirect used an insecure protocol.
     * Redirecting through an insecure protocol can allow for a MITM redirect to an attacker controlled HTTPS server.
     */
    private void validateRedirectChain(HttpContext httpContext) {
        settings.getRedirectVerifier().validateRedirects(getRedirectLocations(httpContext));
    }

    @Nonnull
    private static List<URI> getRedirectLocations(HttpContext httpContext) {
        @SuppressWarnings("unchecked")
        List<URI> redirects = (List<URI>) httpContext.getAttribute(REDIRECT_LOCATIONS);
        return redirects == null ? Collections.emptyList() : redirects;
    }


    private static URI getLastRedirectLocation(HttpContext httpContext) {
        List<URI> redirectLocations = getRedirectLocations(httpContext);
        return redirectLocations.isEmpty() ? null : Iterables.getLast(redirectLocations);
    }

    @Nonnull
    private HttpClientResponse processResponse(HttpClientResponse response) {
        if (response.wasMissing()) {
            LOGGER.info("Resource missing. [HTTP {}: {}]", response.getMethod(), stripUserCredentials(response.getEffectiveUri()));
            return response;
        }

        if (response.wasSuccessful()) {
            return response;
        }

        URI effectiveUri = stripUserCredentials(response.getEffectiveUri());
        LOGGER.info("Failed to get resource: {}. [HTTP {}: {})]", response.getMethod(), response.getStatusLine(), effectiveUri);
        throw new HttpErrorStatusCodeException(response.getMethod(), effectiveUri.toString(), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
    }

    private synchronized CloseableHttpClient getClient() {
        if (client == null) {
            HttpClientBuilder builder = HttpClientBuilder.create();
            HttpClientConfigurer configurer = new HttpClientConfigurer(settings);
            configurer.configure(builder);
            this.supportedTlsVersions = configurer.supportedTlsVersions();
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

    /**
     * Strips the {@link URI#getUserInfo() user info} from the {@link URI} making it
     * safe to appear in log messages.
     */
    @Nullable
    @VisibleForTesting
    static URI stripUserCredentials(@CheckForNull URI uri) {
        if (uri == null) {
            return null;
        }
        try {
            return new URIBuilder(uri).setUserInfo(null).build();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e, true);
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

    /**
     * Factory for creating the {@link HttpClientHelper}
     */
    @FunctionalInterface
    @ServiceScope(Scope.Global.class)
    public interface Factory {
        HttpClientHelper create(HttpSettings settings);

        /**
         * Method should only be used for DI registry and testing.
         * For other uses of {@link HttpClientHelper}, inject an instance of {@link Factory} to create one.
         */
        static Factory createFactory(DocumentationRegistry documentationRegistry) {
            return settings -> new HttpClientHelper(documentationRegistry, settings);
        }
    }

}
