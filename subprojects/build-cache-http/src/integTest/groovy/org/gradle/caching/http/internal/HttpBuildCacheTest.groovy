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

package org.gradle.caching.http.internal

import org.gradle.api.UncheckedIOException
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.http.HttpResourceInteraction
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpBuildCacheTest extends Specification {
    public static final List<Integer> FATAL_HTTP_ERROR_CODES = [
        305, // Use proxy
        400, // Bad request
        401, 403, 407, // Authentication problems
        405, // Method not allowed
        406, 411, 415, 417, // Problems with request caused by client, e.g. not acceptable or unsupported media type
        426, // Update required
        505, // HTTP version not supported
        511 // network authentication required
    ]

    @Rule HttpServer server = new HttpServer()
    @Rule TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()

    HttpBuildCache cache
    def key = new BuildCacheKey() {
        @Override
        String getHashCode() {
            return '0123456abcdef'
        }

        @Override
        String toString() {
            return getHashCode()
        }
    }

    def setup() {
        server.start()
        cache = new HttpBuildCache(server.uri.resolve("/cache/"))
    }

    def "can cache artifact"() {
        def destFile = tempDir.file("cached.zip")
        server.expectPut("/cache/${key.hashCode}", destFile)

        when:
        cache.store(key) { output ->
            output << "Data"
        }
        then:
        destFile.text == "Data"
    }

    def "can load artifact from cache"() {
        def srcFile = tempDir.file("cached.zip")
        srcFile.text = "Data"
        server.expectGet("/cache/${key.hashCode}", srcFile)

        when:
        def receivedInput = null
        cache.load(key) { input ->
            receivedInput = input.text
        }

        then:
        receivedInput == "Data"
    }

    def "reports cache miss on 404"() {
        server.expectGetMissing("/cache/${key.hashCode}")

        when:
        def fromCache = cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        ! fromCache
    }

    def "load reports recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'GET')

        when:
        cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        BuildCacheException exception = thrown()

        exception.message == "Loading key '${key.hashCode}' from an HTTP build cache (${server.uri}/cache/) response status ${httpCode}: broken"

        where:
        httpCode << [500, 501]
    }

    def "load reports non-recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'GET')

        when:
        cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        UncheckedIOException exception = thrown()

        exception.message == "Loading key '${key.hashCode}' from an HTTP build cache (${server.uri}/cache/) response status ${httpCode}: broken"

        where:
        httpCode << FATAL_HTTP_ERROR_CODES
    }

    def "store reports non-recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'PUT')

        when:
        cache.store(key) { output -> }

        then:
        UncheckedIOException exception = thrown()

        exception.message == "Storing key '${key.hashCode}' in an HTTP build cache (${server.uri}/cache/) response status ${httpCode}: broken"

        where:
        httpCode << FATAL_HTTP_ERROR_CODES
    }

    def "store reports recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'PUT')

        when:
        cache.store(key) { output -> }

        then:
        BuildCacheException exception = thrown()

        exception.message == "Storing key '${key.hashCode}' in an HTTP build cache (${server.uri}/cache/) response status ${httpCode}: broken"

        where:
        httpCode << [500, 501]
    }

    private HttpResourceInteraction expectError(int httpCode, String method) {
        server.expect("/cache/${key.hashCode}", false, [method], new HttpServer.ActionSupport("return ${httpCode} broken") {
            @Override
            void handle(HttpServletRequest request, HttpServletResponse response) {
                response.sendError(httpCode, "broken")
            }
        })
    }
}
