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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * Shared ObjectMapper instance for parsing RFC9457 Problem Details responses.
     * Thread-safe after configuration.
     */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

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
            sharedContext = new ConcurrentLinkedQueue<>();
        } else {
            sharedContext = null;
        }
    }

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
    }

    private HttpClientResponse performRawHead(String source, boolean revalidate) {
        return performRequest(new HttpHead(source), revalidate, false);
    }

    public HttpClientResponse performHead(String source, boolean revalidate) {
        return processResponse(performRawHead(source, revalidate));
    }

    HttpClientResponse performRawGet(String source, boolean revalidate) {
        return performRequest(new HttpGet(source), revalidate, false);
    }

    @NonNull
    public HttpClientResponse performGet(String source, boolean revalidate) {
        return processResponse(performRawGet(source, revalidate));
    }

    public HttpClientResponse performRequest(HttpRequestBase request, boolean revalidate) {
        return performRequest(request, revalidate, true);
    }

    public HttpClientResponse performRequest(HttpRequestBase request, boolean revalidate, boolean closeOnError) {
        String method = request.getMethod();
        if (revalidate) {
            request.addHeader(HttpHeaders.CACHE_CONTROL, "max-age=0");
        }
        try {
            return executeGetOrHead(request, closeOnError);
        } catch (FailureFromRedirectLocation e) {
            throw createHttpRequestException(method, e.getCause(), e.getLastRedirectLocation());
        } catch (IOException e) {
            throw createHttpRequestException(method, wrapWithExplanation(e), request.getURI());
        }
    }

    @NonNull
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

    @NonNull
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
        return executeGetOrHead(method, true);
    }

    protected HttpClientResponse executeGetOrHead(HttpRequestBase method, boolean closeOnError) throws IOException {
        HttpClientResponse response = performHttpRequest(method);
        // Consume content for non-successful responses to avoid leaving the connection open.
        // However, if closeOnError is false, keep it open so we can read RFC9457 error details.
        if (closeOnError && !response.wasSuccessful()) {
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

    @NonNull
    private static List<URI> getRedirectLocations(HttpContext httpContext) {
        @SuppressWarnings("unchecked")
        List<URI> redirects = (List<URI>) httpContext.getAttribute(REDIRECT_LOCATIONS);
        return redirects == null ? Collections.emptyList() : redirects;
    }


    private static URI getLastRedirectLocation(HttpContext httpContext) {
        List<URI> redirectLocations = getRedirectLocations(httpContext);
        return redirectLocations.isEmpty() ? null : Iterables.getLast(redirectLocations);
    }

    @NonNull
    private HttpClientResponse processResponse(HttpClientResponse response) {
        if (response.wasMissing()) {
            LOGGER.info("Resource missing. [HTTP {}: {}]", response.getMethod(), stripUserCredentials(response.getEffectiveUri()));
            return response;
        }

        if (response.wasSuccessful()) {
            return response;
        }

        URI effectiveUri = stripUserCredentials(response.getEffectiveUri());

        // Extract detailed error message (must be done before closing response)
        String errorDetail = extractErrorDetail(response);

        // Close the response to avoid leaving connections open
        response.close();

        throw new HttpErrorStatusCodeException(response.getMethod(), effectiveUri.toString(), response.getStatusLine().getStatusCode(), errorDetail);
    }

    /**
     * Extracts error detail from the HTTP response.
     * Supports RFC9457 (Problem Details for HTTP APIs) format.
     * Falls back to reason phrase if RFC9457 is not available.
     *
     * @param response the HTTP response
     * @return the error detail message
     */
    @VisibleForTesting
    String extractErrorDetail(HttpClientResponse response) {
        // Try RFC9457 first
        String contentType = response.getHeader("Content-Type");
        if (contentType != null && contentType.contains("application/problem+json")) {
            LOGGER.debug("RFC9457 content type detected: {}", contentType);
            String rfc9457Detail = parseRFC9457Response(response);
            if (rfc9457Detail != null) {
                LOGGER.debug("RFC9457 error detail extracted: {}", rfc9457Detail);
                return rfc9457Detail;
            }
            LOGGER.debug("RFC9457 parsing failed or returned null, falling back to reason phrase");
        }

        // Fallback to reason phrase (empty in HTTP/2)
        String reasonPhrase = response.getStatusLine().getReasonPhrase();
        String result = reasonPhrase != null ? reasonPhrase : "";
        LOGGER.debug("Using fallback error detail: '{}'", result);
        return result;
    }

    /**
     * Parses RFC9457 (Problem Details for HTTP APIs) response.
     * RFC9457 defines a JSON format for error responses with fields like:
     * - type: URI reference for the problem type
     * - title: Short, human-readable summary
     * - status: HTTP status code
     * - detail: Human-readable explanation specific to this occurrence
     * - instance: URI reference identifying the specific occurrence
     *
     * @param response the HTTP response
     * @return the detail field from the RFC9457 response, or null if parsing fails
     */
    @VisibleForTesting
    @Nullable
    String parseRFC9457Response(HttpClientResponse response) {
        try {
            java.io.InputStream content = response.getContent();
            if (content == null) {
                LOGGER.debug("RFC9457 response has no content");
                return null;
            }

            Rfc9457Problem problem = OBJECT_MAPPER.readValue(content, Rfc9457Problem.class);

            LOGGER.trace("RFC9457 parsed successfully - type: {}, title: {}, status: {}, detail: {}, instance: {}",
                problem.getType(), problem.getTitle(), problem.getStatus(), problem.getDetail(), problem.getInstance());

            // Prefer "detail" field as it contains the specific explanation
            String detail = problem.getDetail();
            if (detail != null && !detail.isEmpty()) {
                LOGGER.trace("Using RFC9457 'detail' field: {}", detail);
                return detail;
            }

            // Fallback to "title" field if "detail" is not present
            String title = problem.getTitle();
            if (title != null && !title.isEmpty()) {
                LOGGER.trace("RFC9457 'detail' field empty, using 'title' field: {}", title);
                return title;
            }

            LOGGER.trace("RFC9457 response has neither 'detail' nor 'title' fields");
            return null;
        } catch (Exception e) {
            LOGGER.warn("Failed to parse RFC9457 response", e);
            return null;
        }
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
    static URI stripUserCredentials(URI uri) {
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
     * POJO representing RFC9457 Problem Details for HTTP APIs.
     * See: https://www.rfc-editor.org/rfc/rfc9457.html
     */
    @NullMarked
    @VisibleForTesting
    static class Rfc9457Problem {
        @Nullable
        private String type;
        @Nullable
        private String title;
        @Nullable
        private Integer status;
        @Nullable
        private String detail;
        @Nullable
        private String instance;

        // Default constructor required by Jackson
        public Rfc9457Problem() {
        }

        @Nullable
        public String getType() {
            return type;
        }

        public void setType(@Nullable String type) {
            this.type = type;
        }

        @Nullable
        public String getTitle() {
            return title;
        }

        public void setTitle(@Nullable String title) {
            this.title = title;
        }

        @Nullable
        public Integer getStatus() {
            return status;
        }

        public void setStatus(@Nullable Integer status) {
            this.status = status;
        }

        @Nullable
        public String getDetail() {
            return detail;
        }

        public void setDetail(@Nullable String detail) {
            this.detail = detail;
        }

        @Nullable
        public String getInstance() {
            return instance;
        }

        public void setInstance(@Nullable String instance) {
            this.instance = instance;
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
