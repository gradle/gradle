/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.resource.ReadableContent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

/**
 * A client which can perform HTTP requests.
 * <p>
 * To create instances of this interface, use an {@link HttpClientFactory}.
 * <p>
 * Implementations of this interface must be thread-safe. This interface should
 * remain independent of any specific HTTP client library.
 */
@NullMarked
public interface HttpClient extends Closeable {

    /**
     * Performs a HEAD request to the given URI with the given headers.
     */
    Response performHead(URI uri, ImmutableMap<String, String> headers);

    /**
     * Performs a GET request to the given URI with the given headers.
     */
    Response performGet(URI uri, ImmutableMap<String, String> headers);

    /**
     * Performs a GET request to the given URI with the given headers, avoiding any
     * additional processing of the response like mapping status codes to exceptions
     * or closing the response body when unsuccessful responses are received.
     */
    Response performRawGet(URI uri, ImmutableMap<String, String> headers) throws IOException;

    /**
     * Performs a PUT request to the given URI with the given headers, avoiding any
     * additional processing of the response like mapping status codes to exceptions
     * or closing the response body when unsuccessful responses are received.
     */
    Response performRawPut(URI uri, ReadableContent resource) throws IOException;

    /**
     * Performs a PUT request to the given URI with the given headers, avoiding any
     * additional processing of the response like mapping status codes to exceptions
     * or closing the response body when unsuccessful responses are received.
     */
    Response performRawPut(URI uri, ImmutableMap<String, String> headers, WritableContent resource) throws IOException;

    /**
     * A response to an HTTP request.
     */
    interface Response extends Closeable {

        /**
         * Returns the value of the given response header, or null if not present.
         */
        @Nullable String getHeader(String name);

        /**
         * Returns the response content as an input stream.
         */
        InputStream getContent() throws IOException;

        /**
         * Returns the HTTP status code of the response.
         */
        int getStatusCode();

        /**
         * Returns the HTTP status reason phrase of the response.
         */
        String getStatusReason();

        /**
         * Returns the HTTP method used for the request.
         */
        String getMethod();

        /**
         * Returns the effective URI of the response, after any redirects.
         */
        URI getEffectiveUri();

        /**
         * Returns true if the response status code indicates a successful response.
         */
        default boolean isSuccessful() {
            int statusCode = getStatusCode();
            return statusCode >= 200 && statusCode < 400;
        }

        /**
         * Returns true if the response status code indicates a missing resource (404).
         */
        default boolean isMissing() {
            return getStatusCode() == 404;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        void close();

    }

    /**
     * Content that may be used as the body of a PUT request.
     */
    interface WritableContent {

        /**
         * Writes the content to the given output stream.
         */
        void writeTo(OutputStream outputStream) throws IOException;

        /**
         * Returns the size of the content in bytes.
         */
        long getSize();

    }

}
