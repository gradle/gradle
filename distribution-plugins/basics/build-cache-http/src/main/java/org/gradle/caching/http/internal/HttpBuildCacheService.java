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

import com.google.common.collect.ImmutableSet;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.NonRepeatableRequestException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.AbstractHttpEntity;
import org.gradle.api.UncheckedIOException;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.resource.transport.http.HttpClientResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Build cache implementation that delegates to a service accessible via HTTP.
 */
public class HttpBuildCacheService implements BuildCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpBuildCacheService.class);
    static final String BUILD_CACHE_CONTENT_TYPE = "application/vnd.gradle.build-cache-artifact.v1";

    private static final Set<Integer> FATAL_HTTP_ERROR_CODES = ImmutableSet.of(
        HttpStatus.SC_USE_PROXY,
        HttpStatus.SC_BAD_REQUEST,
        HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
        HttpStatus.SC_METHOD_NOT_ALLOWED,
        HttpStatus.SC_NOT_ACCEPTABLE, HttpStatus.SC_LENGTH_REQUIRED, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, HttpStatus.SC_EXPECTATION_FAILED,
        426, // Upgrade required
        HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED,
        511 // network authentication required
    );

    private final URI root;
    private final HttpClientHelper httpClientHelper;
    private final HttpBuildCacheRequestCustomizer requestCustomizer;

    public HttpBuildCacheService(HttpClientHelper httpClientHelper, URI url, HttpBuildCacheRequestCustomizer requestCustomizer) {
        this.requestCustomizer = requestCustomizer;
        if (!url.getPath().endsWith("/")) {
            throw new IllegalArgumentException("HTTP cache root URI must end with '/'");
        }
        this.root = url;
        this.httpClientHelper = httpClientHelper;
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        final URI uri = root.resolve("./" + key.getHashCode());
        HttpGet httpGet = new HttpGet(uri);
        httpGet.addHeader(HttpHeaders.ACCEPT, BUILD_CACHE_CONTENT_TYPE + ", */*");
        requestCustomizer.customize(httpGet);

        try (HttpClientResponse response = httpClientHelper.performHttpRequest(httpGet)) {
            StatusLine statusLine = response.getStatusLine();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for GET {}: {}", safeUri(uri), statusLine);
            }
            int statusCode = statusLine.getStatusCode();
            if (isHttpSuccess(statusCode)) {
                reader.readFrom(response.getContent());
                return true;
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                return false;
            } else {
                String defaultMessage = String.format("Loading entry from '%s' response status %d: %s", safeUri(uri), statusCode, statusLine.getReasonPhrase());
                if (isRedirect(statusCode)) {
                    return handleRedirect(uri, response, statusCode, defaultMessage, "loading entry from");
                } else {
                    return throwHttpStatusCodeException(statusCode, defaultMessage);
                }
            }
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    private boolean handleRedirect(URI uri, HttpClientResponse response, int statusCode, String defaultMessage, String action) {
        String locationHeader = response.getHeader(HttpHeaders.LOCATION);
        if (locationHeader == null) {
            return throwHttpStatusCodeException(statusCode, defaultMessage);
        }
        try {
            throw new BuildCacheException(String.format("Received unexpected redirect (HTTP %d) to %s when " + action + " '%s'. "
                + "Ensure the configured URL for the remote build cache is correct.", statusCode, safeUri(new URI(locationHeader)), safeUri(uri)));
        } catch (URISyntaxException e) {
            return throwHttpStatusCodeException(statusCode, defaultMessage);
        }
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == HttpStatus.SC_MOVED_PERMANENTLY || statusCode == HttpStatus.SC_MOVED_TEMPORARILY || statusCode == HttpStatus.SC_TEMPORARY_REDIRECT;
    }

    @Override
    public void store(BuildCacheKey key, final BuildCacheEntryWriter output) throws BuildCacheException {
        final URI uri = root.resolve(key.getHashCode());
        HttpPut httpPut = new HttpPut(uri);
        httpPut.addHeader(HttpHeaders.CONTENT_TYPE, BUILD_CACHE_CONTENT_TYPE);
        requestCustomizer.customize(httpPut);

        httpPut.setEntity(new AbstractHttpEntity() {
            @Override
            public boolean isRepeatable() {
                return false;
            }

            @Override
            public long getContentLength() {
                return output.getSize();
            }

            @Override
            public InputStream getContent() {
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
        try (HttpClientResponse response = httpClientHelper.performHttpRequest(httpPut)) {
            StatusLine statusLine = response.getStatusLine();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for PUT {}: {}", safeUri(uri), statusLine);
            }
            int statusCode = statusLine.getStatusCode();
            if (!isHttpSuccess(statusCode)) {
                String defaultMessage = String.format("Storing entry at '%s' response status %d: %s", safeUri(uri), statusCode, statusLine.getReasonPhrase());
                if (isRedirect(statusCode)) {
                    handleRedirect(uri, response, statusCode, defaultMessage, "storing entry at");
                } else {
                    throwHttpStatusCodeException(statusCode, defaultMessage);
                }
            }
        } catch (ClientProtocolException e) {
            Throwable cause = e.getCause();
            if (cause instanceof NonRepeatableRequestException) {
                throw wrap(cause.getCause());
            } else {
                throw wrap(cause);
            }
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    private static BuildCacheException wrap(Throwable e) {
        if (e instanceof Error) {
            throw (Error) e;
        }

        throw new BuildCacheException(e.getMessage(), e);
    }

    private boolean isHttpSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean throwHttpStatusCodeException(int statusCode, String message) {
        if (FATAL_HTTP_ERROR_CODES.contains(statusCode)) {
            throw new UncheckedIOException(message);
        } else {
            throw new BuildCacheException(message);
        }
    }

    @Override
    public void close() throws IOException {
        httpClientHelper.close();
    }

    /**
     * Create a safe URI from the given one by stripping out user info.
     *
     * @param uri Original URI
     * @return a new URI with no user info
     */
    private static URI safeUri(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
