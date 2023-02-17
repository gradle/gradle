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

import org.apache.http.HttpStatus
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheServiceFactory
import org.gradle.caching.http.HttpBuildCache
import org.gradle.caching.internal.DefaultBuildCacheKey
import org.gradle.internal.hash.HashCode
import org.gradle.internal.resource.transport.http.DefaultSslContextFactory
import org.gradle.internal.resource.transport.http.HttpClientHelper
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class NextGenHttpBuildCacheServiceTest extends Specification {

    @Rule
    HttpServer server = new HttpServer()
    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(getClass())

    BuildCacheServiceFactory.Describer buildCacheDescriber
    HttpClientHelper.Factory httpClientHelperFactory = HttpClientHelper.Factory.createFactory(new DocumentationRegistry())

    def key = new DefaultBuildCacheKey(HashCode.fromString("01234567abcdef"))
    private config = TestUtil.newInstance(HttpBuildCache.class)

    NextGenHttpBuildCacheService cacheRef

    NextGenHttpBuildCacheService getCache() {
        if (cacheRef == null) {
            buildCacheDescriber = new NoopBuildCacheDescriber()
            cacheRef = new NextGenHttpBuildCacheServiceFactory(new DefaultSslContextFactory(), { it.addHeader("X-Gradle-Version", "3.0") }, httpClientHelperFactory)
                .createBuildCacheService(this.config, buildCacheDescriber) as NextGenHttpBuildCacheService
        }
        cacheRef
    }

    def setup() {
        server.start()
        config.url = server.uri.resolve("/cache/")
    }

    def "stores cache artifact with nextGen key prefix"() {
        def destFile = tempDir.file("cached.zip")
        def content = "Data".bytes
        server.expectPut("/cache/nextGen-${key.hashCode}", destFile, HttpStatus.SC_OK, null, content.length)

        when:
        cache.store(key, writer(content))

        then:
        destFile.bytes == content
    }

    def "loads artifact from cache with nextGen key prefix"() {
        def srcFile = tempDir.file("cached.zip")
        srcFile.text = "Data"
        server.expectGet("/cache/nextGen-${key.hashCode}", srcFile)

        when:
        def receivedInput = null
        cache.load(key) { input ->
            receivedInput = input.text
        }

        then:
        receivedInput == "Data"
    }

    def "checks if artifact exists in cache with nextGen key prefix"() {
        def destFile = tempDir.file("cached.zip")
        def content = "Data".bytes
        server.expectPut("/cache/nextGen-${key.hashCode}", destFile, HttpStatus.SC_OK, null, content.length)
        server.expectHead("/cache/nextGen-${key.hashCode}", destFile)
        cache.store(key, writer(content))

        when:
        boolean exists = cache.contains(key)

        then:
        exists
    }

    private class NoopBuildCacheDescriber implements BuildCacheServiceFactory.Describer {

        @Override
        BuildCacheServiceFactory.Describer type(String type) { this }

        @Override
        BuildCacheServiceFactory.Describer config(String name, String value) { this }

    }

    static class Writer implements BuildCacheEntryWriter {
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
