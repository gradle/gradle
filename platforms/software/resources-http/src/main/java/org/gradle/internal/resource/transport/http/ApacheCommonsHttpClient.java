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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.ReadableContent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

import static java.lang.String.join;
import static org.apache.http.client.protocol.HttpClientContext.REDIRECT_LOCATIONS;

/**
 * Implementation of {@link HttpClient} backed by Apache Commons HttpClient.
 */
@NullMarked
public class ApacheCommonsHttpClient implements HttpClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApacheCommonsHttpClient.class);

    /**
     * Shared ObjectMapper instance for parsing RFC9457 Problem Details responses.
     * Thread-safe after configuration.
     */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private final DocumentationRegistry documentationRegistry;
    private final HttpSettings settings;
    private final Supplier<HttpClientBuilder> clientBuilderFactory;

    private Collection<String> supportedTlsVersions;
    private @Nullable CloseableHttpClient client;

    /**
     * Maintains a queue of contexts which are shared between threads when authentication
     * is activated. When a request is performed, it will pick a context from the queue,
     * and create a new one whenever it's not available (which either means it's the first request
     * or that other requests are being performed concurrently). The queue will grow as big as
     * the max number of concurrent requests executed.
     */
    private final @Nullable ConcurrentLinkedQueue<HttpContext> sharedContext;

    /**
     * Use {@link ApacheCommonsHttpClientFactory} to instantiate instances.
     */
    ApacheCommonsHttpClient(DocumentationRegistry documentationRegistry, HttpSettings settings) {
        this(documentationRegistry, settings, HttpClientBuilder::create);
    }

    /**
     * Overload intended specifically for unit testing, allowing injection of mocked HttpClientBuilder.
     */
    @VisibleForTesting
    ApacheCommonsHttpClient(
        DocumentationRegistry documentationRegistry,
        HttpSettings settings,
        Supplier<HttpClientBuilder> clientBuilderFactory
    ) {
        this.documentationRegistry = documentationRegistry;
        this.settings = settings;
        this.clientBuilderFactory = clientBuilderFactory;
        if (!settings.getAuthenticationSettings().isEmpty()) {
            sharedContext = new ConcurrentLinkedQueue<>();
        } else {
            sharedContext = null;
        }
    }

    @Override
    public HttpClient.Response performHead(URI uri, ImmutableMap<String, String> headers) {
        HttpRequestBase request = new HttpHead(uri);
        addHeaders(request, headers);
        return processResponse(performRequest(request));
    }

    @Override
    public HttpClient.Response performGet(URI uri, ImmutableMap<String, String> headers) {
        HttpRequestBase request = new HttpGet(uri);
        addHeaders(request, headers);
        return processResponse(performRequest(request));
    }

    @Override
    public Response performRawGet(URI uri, ImmutableMap<String, String> headers) throws IOException {
        HttpGet httpGet = new HttpGet(uri);
        addHeaders(httpGet, headers);
        return performRawRequest(httpGet);
    }

    @Override
    public Response performRawPut(URI uri, ImmutableMap<String, String> headers, ReadableContent resource) throws IOException {
        HttpPut method = new HttpPut(uri);
        addHeaders(method, headers);
        final RepeatableInputStreamEntity entity = new RepeatableInputStreamEntity(resource, ContentType.APPLICATION_OCTET_STREAM);
        method.setEntity(entity);
        return performRawRequest(method);
    }

    private Response performRequest(HttpRequestBase request) {
        try {
            return performRawRequest(request);
        } catch (FailureFromRedirectLocation e) {
            throw createHttpRequestException(request.getMethod(), e.getCause(), e.getLastRedirectLocation());
        } catch (IOException e) {
            throw createHttpRequestException(request.getMethod(), wrapWithExplanation(e), request.getURI());
        }
    }

    private static void addHeaders(HttpRequestBase request, ImmutableMap<String, String> headers) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.addHeader(entry.getKey(), entry.getValue());
        }
    }

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

    private static String getConfidenceNote(SSLHandshakeException sslException) {
        if (sslException.getMessage() != null && sslException.getMessage().contains("protocol_version")) {
            // If we're handling an SSLHandshakeException with the error of 'protocol_version' we know that the server doesn't support this protocol.
            return "does";
        }
        // Sometimes the SSLHandshakeException doesn't include the 'protocol_version', even though this is the cause of the error.
        // Tell the user this but with less confidence.
        return "may";
    }

    private HttpClient.Response performRawRequest(HttpRequestBase request) throws IOException {
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

    private HttpClient.Response performHttpRequest(HttpRequestBase request, HttpContext httpContext) throws IOException {
        // Without this, HTTP Client prohibits multiple redirects to the same location within the same context
        httpContext.removeAttribute(REDIRECT_LOCATIONS);
        LOGGER.debug("Performing HTTP {}: {}", request.getMethod(), stripUserCredentials(request.getURI()));

        try {
            CloseableHttpResponse response = getClient().execute(request, httpContext);
            return toHttpClientResponse(request, httpContext, response);
        } catch (IOException e) {
            validateRedirectChain(httpContext);
            URI lastRedirectLocation = stripUserCredentials(getLastRedirectLocation(httpContext));
            if (lastRedirectLocation == null) {
                throw e;
            }
            throw new FailureFromRedirectLocation(lastRedirectLocation, e);
        }
    }

    private HttpClient.Response toHttpClientResponse(HttpRequestBase request, HttpContext httpContext, CloseableHttpResponse response) {
        validateRedirectChain(httpContext);
        URI lastRedirectLocation = getLastRedirectLocation(httpContext);
        URI effectiveUri = lastRedirectLocation == null ? request.getURI() : lastRedirectLocation;
        return new ApacheHttpResponse(request.getMethod(), effectiveUri, response);
    }

    /**
     * Validates that no redirect used an insecure protocol.
     * Redirecting through an insecure protocol can allow for a MITM redirect to an attacker controlled HTTPS server.
     */
    private void validateRedirectChain(HttpContext httpContext) {
        settings.getRedirectVerifier().validateRedirects(getRedirectLocations(httpContext));
    }

    private static List<URI> getRedirectLocations(HttpContext httpContext) {
        @SuppressWarnings("unchecked")
        List<URI> redirects = (List<URI>) httpContext.getAttribute(REDIRECT_LOCATIONS);
        return redirects == null ? Collections.emptyList() : redirects;
    }

    private static @Nullable URI getLastRedirectLocation(HttpContext httpContext) {
        List<URI> redirectLocations = getRedirectLocations(httpContext);
        return redirectLocations.isEmpty() ? null : Iterables.getLast(redirectLocations);
    }

    private Response processResponse(Response response) {
        if (response.isSuccessful()) {
            return response;
        } else if (response.isMissing()) {
            // Consume content to avoid leaving the connection open.
            response.close();
            LOGGER.info("Resource missing. [HTTP {}: {}]", response.getMethod(), stripUserCredentials(response.getEffectiveUri()));
            return response;
        } else {
            URI effectiveUri = stripUserCredentials(response.getEffectiveUri());
            LOGGER.info("Failed to get resource: {}. [HTTP {}: {})]", response.getMethod(), response.getStatusCode(), effectiveUri);

            // Extract detailed error message (must be done before closing response)
            String errorDetail = extractErrorDetail(response).orElse("");

            // Capture response state before closing, as reading a closed response is fragile
            String method = response.getMethod();
            int statusCode = response.getStatusCode();

            // Close the response to avoid leaving connections open
            response.close();

            HttpErrorStatusCodeException statusCause = new HttpErrorStatusCodeException(statusCode, errorDetail);
            throw new HttpRequestException(String.format("Could not %s '%s'.", method, effectiveUri), statusCause);
        }
    }

    /**
     * Extracts error detail from the HTTP response.
     * Supports RFC9457 (Problem Details for HTTP APIs) format.
     * Falls back to the HTTP reason phrase when RFC9457 is not available.
     *
     * @param response the HTTP response
     * @return the error detail message, or empty if none is available
     */
    @VisibleForTesting
    Optional<String> extractErrorDetail(HttpClient.Response response) {
        String contentType = response.getHeader("Content-Type");
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/problem+json")) {
            LOGGER.debug("RFC9457 content type detected: {}", contentType);
            Optional<String> rfc9457Detail = parseRFC9457Response(response);
            if (rfc9457Detail.isPresent()) {
                return rfc9457Detail;
            }
            LOGGER.debug("RFC9457 parsing failed or produced no detail, falling back to reason phrase");
        }

        // Fallback to reason phrase (empty in HTTP/2)
        return Optional.ofNullable(response.getStatusReason()).filter(s -> !s.isEmpty());
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
     * @return the detail field (or title as fallback) from the RFC9457 response, or empty if parsing fails or yields no usable field
     */
    @VisibleForTesting
    Optional<String> parseRFC9457Response(HttpClient.Response response) {
        try {
            InputStream content = response.getContent();
            Rfc9457Problem problem = OBJECT_MAPPER.readValue(content, Rfc9457Problem.class);

            LOGGER.trace("RFC9457 parsed successfully - type: {}, title: {}, status: {}, detail: {}, instance: {}",
                problem.getType(), problem.getTitle(), problem.getStatus(), problem.getDetail(), problem.getInstance());

            // Prefer "detail" field, fall back to "title" if detail is absent or empty
            String detail = problem.getDetail();
            if (detail != null && !detail.isEmpty()) {
                return Optional.of(detail);
            }
            String title = problem.getTitle();
            if (title != null && !title.isEmpty()) {
                return Optional.of(title);
            }
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warn("Failed to parse RFC9457 response", e);
            return Optional.empty();
        }
    }

    private synchronized CloseableHttpClient getClient() {
        if (client == null) {
            HttpClientBuilder builder = clientBuilderFactory.get();
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
    @VisibleForTesting
    static @Nullable URI stripUserCredentials(@Nullable URI uri) {
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
     * Represents RFC9457 Problem Details for HTTP APIs.
     * See: https://www.rfc-editor.org/rfc/rfc9457.html
     */
    @VisibleForTesting
    static class Rfc9457Problem {
        @Nullable private String type;
        @Nullable private String title;
        @Nullable private Integer status;
        @Nullable private String detail;
        @Nullable private String instance;

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

}
