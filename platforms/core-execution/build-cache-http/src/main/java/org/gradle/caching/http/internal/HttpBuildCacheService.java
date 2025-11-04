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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.protocol.HTTP;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.transport.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Build cache implementation that delegates to a service accessible via HTTP.
 */
public class HttpBuildCacheService implements BuildCacheService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpBuildCacheService.class);
    static final String BUILD_CACHE_CONTENT_TYPE = "application/vnd.gradle.build-cache-artifact.v2";

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
    private final HttpClient client;

    private final ImmutableMap<String, String> defaultLoadHeaders;
    private final ImmutableMap<String, String> defaultStoreHeaders;

    public HttpBuildCacheService(HttpClient client, URI url, HttpBuildCacheRequestCustomizer requestCustomizer, boolean useExpectContinue) {
        this.root = withTrailingSlash(url);
        this.client = client;

        this.defaultLoadHeaders = getDefaultLoadHeaders(requestCustomizer);
        this.defaultStoreHeaders = getDefaultStoreHeaders(requestCustomizer, useExpectContinue);
    }

    /**
     * Compute the headers to use when loading from the build cache.
     */
    private static ImmutableMap<String, String> getDefaultLoadHeaders(HttpBuildCacheRequestCustomizer requestCustomizer) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        builder.put(HttpHeaders.ACCEPT, BUILD_CACHE_CONTENT_TYPE + ", */*");
        requestCustomizer.visitHeaders(builder::put);
        return builder.build();
    }

    /**
     * Compute the headers to use when storing to the build cache.
     */
    private static ImmutableMap<String, String> getDefaultStoreHeaders(HttpBuildCacheRequestCustomizer requestCustomizer, boolean useExpectContinue) {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        if (useExpectContinue) {
            builder.put(HTTP.EXPECT_DIRECTIVE, HTTP.EXPECT_CONTINUE);
        }
        builder.put(HttpHeaders.CONTENT_TYPE, BUILD_CACHE_CONTENT_TYPE);
        requestCustomizer.visitHeaders(builder::put);
        return builder.build();
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        final URI uri = root.resolve("./" + key.getHashCode());
        try (HttpClient.Response response = client.performRawGet(uri, defaultLoadHeaders)) {
            int statusCode = response.getStatusCode();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for GET {}: {}", safeUri(uri), statusCode);
            }
            if (isHttpSuccess(statusCode)) {
                reader.readFrom(response.getContent());
                return true;
            } else if (response.isMissing()) {
                return false;
            } else {
                String defaultMessage = String.format("Loading entry from '%s' response status %d: %s", safeUri(uri), statusCode, response.getStatusReason());
                return throwHttpStatusCodeException(statusCode, defaultMessage);
            }
        } catch (IOException e) {
            throw wrap(e);
        }
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        final URI uri = root.resolve(key.getHashCode());
        HttpClient.WritableContent putResource = new HttpClient.WritableContent() {
            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
                writer.writeTo(outputStream);
            }

            @Override
            public long getSize() {
                return writer.getSize();
            }
        };

        try (HttpClient.Response response = client.performRawPut(uri, defaultStoreHeaders, putResource)) {
            int statusCode = response.getStatusCode();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Response for PUT {}: {}", safeUri(uri), statusCode);
            }
            if (!isHttpSuccess(statusCode)) {
                String defaultMessage = String.format("Storing entry at '%s' response status %d: %s", safeUri(uri), statusCode, response.getStatusReason());
                throwHttpStatusCodeException(statusCode, defaultMessage);
            }
        } catch (ClientProtocolException e) {
            throw wrap(e.getCause());
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

    private boolean throwHttpStatusCodeException(int statusCode, String message) {
        if (FATAL_HTTP_ERROR_CODES.contains(statusCode)) {
            throw UncheckedException.throwAsUncheckedException(new IOException(message), true);
        } else {
            throw new BuildCacheException(message);
        }
    }

    private boolean isHttpSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public void close() throws IOException {
        client.close();
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

    /**
     * Add a trailing slash to the given URI's path if necessary.
     *
     * @param uri the original URI
     * @return a URI guaranteed to have a trailing slash in the path
     */
    private static URI withTrailingSlash(URI uri) {
        if (uri.getPath().endsWith("/")) {
            return uri;
        }
        try {
            return new URIBuilder(uri).setPath(uri.getPath() + "/").build();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
