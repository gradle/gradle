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

import org.apache.http.HttpHeaders
import org.apache.http.HttpStatus
import org.gradle.api.UncheckedIOException
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.http.HttpBuildCache
import org.gradle.caching.internal.DefaultBuildCacheKey
import org.gradle.caching.internal.NextGenBuildCacheService
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.transport.http.DefaultSslContextFactory
import org.gradle.internal.resource.transport.http.HttpClientHelper
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.http.AuthScheme
import org.gradle.test.fixtures.server.http.HttpResourceInteraction
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class HttpBuildCacheServiceTest extends Specification {
    public static final List<Integer> FATAL_HTTP_ERROR_CODES = [
        HttpStatus.SC_USE_PROXY,
        HttpStatus.SC_BAD_REQUEST,
        HttpStatus.SC_UNAUTHORIZED, HttpStatus.SC_FORBIDDEN, HttpStatus.SC_PROXY_AUTHENTICATION_REQUIRED,
        HttpStatus.SC_METHOD_NOT_ALLOWED,
        HttpStatus.SC_NOT_ACCEPTABLE, HttpStatus.SC_LENGTH_REQUIRED, HttpStatus.SC_UNSUPPORTED_MEDIA_TYPE, HttpStatus.SC_EXPECTATION_FAILED,
        426, // Upgrade required
        HttpStatus.SC_HTTP_VERSION_NOT_SUPPORTED,
        511 // network authentication required
    ]

    @Rule
    HttpServer server = new HttpServer()
    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(getClass())

    BuildCacheServiceFactory.Describer buildCacheDescriber
    HttpClientHelper.Factory httpClientHelperFactory = HttpClientHelper.Factory.createFactory(new DocumentationRegistry())

    def key = new DefaultBuildCacheKey(HashCode.fromString("01234567abcdef"))
    private config = TestUtil.newInstance(HttpBuildCache.class)

    HttpBuildCacheService cacheRef

    HttpBuildCacheService getCache() {
        if (cacheRef == null) {
            buildCacheDescriber = new NoopBuildCacheDescriber()
            cacheRef = new DefaultHttpBuildCacheServiceFactory(new DefaultSslContextFactory(), { it.addHeader("X-Gradle-Version", "3.0") }, httpClientHelperFactory)
                .createBuildCacheService(this.config, buildCacheDescriber) as HttpBuildCacheService
        }
        cacheRef
    }

    def setup() {
        server.start()
        config.url = server.uri.resolve("/cache/")
    }

    def "can cache artifact"() {
        def destFile = tempDir.file("cached.zip")
        def content = "Data".bytes
        server.expectPut("/cache/${key.hashCode}", destFile, HttpStatus.SC_OK, null, content.length)

        when:
        cache.store(key, writer(content))
        then:
        destFile.bytes == content
    }

    def "storing to cache can follow non method preserving redirects"() {
        def content = "Data".bytes
        server.expectPutRedirected("/cache/${key.hashCode}", "/redirect/cache/${key.hashCode}")
        server.expectGetEmptyOk("/redirect/cache/${key.hashCode}")

        when:
        def writer = writer(content)
        cache.store(key, writer)

        then:
        noExceptionThrown()
        writer.writeCount == 1
    }

    def "storing to cache can follow method preserving redirects"() {
        def destFile = tempDir.file("cached.zip")
        def content = "Data".bytes
        server.expectPutRedirected("/cache/${key.hashCode}", "/redirect/cache/${key.hashCode}", null, HttpServer.RedirectType.TEMP_307)
        server.expectPut("/redirect/cache/${key.hashCode}", destFile, HttpStatus.SC_OK, null, content.length)

        when:
        def writer = writer(content)
        cache.store(key, writer)
        then:
        destFile.bytes == content
        writer.writeCount == 2
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

    def "loading from cache does follow redirects"() {
        def srcFile = tempDir.file("cached.zip")
        srcFile.text = "Data"
        server.expectGetRedirected("/cache/${key.hashCode}", "/redirect/cache/${key.hashCode}")
        server.expectGet("/redirect/cache/${key.hashCode}", srcFile)

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
        !fromCache
    }

    def "load reports recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'GET')

        when:
        cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        BuildCacheException exception = thrown()

        exception.message == "Loading entry from '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << [HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_SERVICE_UNAVAILABLE]
    }

    def "load reports non-recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'GET')

        when:
        cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        UncheckedIOException exception = thrown()

        exception.message == "Loading entry from '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << FATAL_HTTP_ERROR_CODES
    }

    def "store reports non-recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'PUT')

        when:
        cache.store(key, writer("".bytes))

        then:
        UncheckedIOException exception = thrown()

        exception.message == "Storing entry at '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << FATAL_HTTP_ERROR_CODES
    }

    def "store reports recoverable error on http code #httpCode"(int httpCode) {
        expectError(httpCode, 'PUT')

        when:
        cache.store(key, writer("".bytes))

        then:
        BuildCacheException exception = thrown()

        exception.message == "Storing entry at '${server.uri}/cache/${key.hashCode}' response status ${httpCode}: broken"

        where:
        httpCode << [HttpStatus.SC_INTERNAL_SERVER_ERROR, HttpStatus.SC_SERVICE_UNAVAILABLE]
    }

    def "does not transmit body when using expect continue that returns error"() {
        given:
        config.useExpectContinue = true
        expectError(413, 'PUT')
        def writer = writer("abc".bytes)

        when:
        cache.store(key, writer)

        then:
        thrown BuildCacheException
        writer.writeCount == 0
    }

    def "does transmit body when using expect continue that continues"() {
        given:
        config.useExpectContinue = true

        and:
        def content = "abc".bytes
        def writer = writer(content)
        def destFile = tempDir.file("cached.zip")
        server.expectPut("/cache/${key.hashCode}", destFile, HttpStatus.SC_OK, null, content.length)

        when:
        cache.store(key, writer)

        then:
        destFile.bytes == content
        writer.writeCount == 1
    }

    def "does not transmit body when using expect continue for redirected request"() {
        given:
        config.useExpectContinue = true

        and:
        def content = "abc".bytes
        def writer = writer(content)
        def destFile = tempDir.file("cached.zip")
        server.expectPutRedirected("/cache/${key.hashCode}", "/redirected/${key.hashCode}", null, HttpServer.RedirectType.TEMP_307)
        server.expectPut("/redirected/${key.hashCode}", destFile)

        when:
        cache.store(key, writer)

        then:
        destFile.bytes == content
        writer.writeCount == 1
    }

    def "sends X-Gradle-Version and Content-Type headers on GET"() {
        server.expect("/cache/${key.hashCode}", ["GET"], new HttpServer.ActionSupport("get has appropriate headers") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                request.getHeader("X-Gradle-Version") == "3.0"

                def accept = request.getHeader(HttpHeaders.ACCEPT).split(", ")
                assert accept.length == 2
                assert accept[0] == HttpBuildCacheService.BUILD_CACHE_CONTENT_TYPE
                assert accept[1] == "*/*"

                response.setStatus(200)
            }
        })

        expect:
        cache.load(key) { input -> }
    }

    def "sends X-Gradle-Version and Content-Type headers on PUT"() {
        server.expect("/cache/${key.hashCode}", ["PUT"], new HttpServer.ActionSupport("put has appropriate headers") {
            void handle(HttpServletRequest request, HttpServletResponse response) {
                request.getHeader("X-Gradle-Version") == "3.0"

                assert request.getHeader(HttpHeaders.CONTENT_TYPE) == HttpBuildCacheService.BUILD_CACHE_CONTENT_TYPE

                response.setStatus(200)
            }
        })

        expect:
        cache.store(key, writer("".bytes))
    }

    def "does preemptive authentication"() {
        config.credentials.username = 'user'
        config.credentials.password = 'password'

        server.authenticationScheme = AuthScheme.BASIC

        def destFile = tempDir.file("cached.zip")
        destFile.text = 'Old'
        when:
        server.expectGet("/cache/${key.hashCode}", config.credentials.username, config.credentials.password, destFile)
        def result = null
        cache.load(key) { input ->
            result = input.text
        }
        then:
        result == 'Old'
        server.authenticationAttempts == ['Basic'] as Set

        server.expectPut("/cache/${key.hashCode}", config.credentials.username, config.credentials.password, destFile)

        when:
        def content = "Data".bytes
        cache.store(key, writer(content))
        then:
        destFile.bytes == content
        server.authenticationAttempts == ['Basic'] as Set
    }

    private HttpResourceInteraction expectError(int httpCode, String method) {
        server.expect("/cache/${key.hashCode}", false, [method], new HttpServer.ActionSupport("return ${httpCode} broken") {
            @Override
            void handle(HttpServletRequest request, HttpServletResponse response) {
                //noinspection GrDeprecatedAPIUsage
                response.setStatus(httpCode, "broken")
            }
        })
    }

    private class NoopBuildCacheDescriber implements BuildCacheServiceFactory.Describer {

        @Override
        BuildCacheServiceFactory.Describer type(String type) { this }

        @Override
        BuildCacheServiceFactory.Describer config(String name, String value) { this }

    }

    static class Writer implements NextGenBuildCacheService.NextGenWriter {
        private final byte[] content
        private int writeCount = 0

        Writer(byte[] content) {
            this.content = content
        }

        @Override
        InputStream openStream() throws IOException {
            ++writeCount
            return new ByteArrayInputStream(content)
        }

        @Override
        void writeTo(OutputStream output) throws IOException {
            ++writeCount
            output << content
        }

        @Override
        long getSize() {
            return content.length
        }
    }

    private static Writer writer(byte[] content) {
        new Writer(content)
    }

}
