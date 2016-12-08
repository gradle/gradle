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

import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule
import spock.lang.Specification

class HttpBuildCacheTest extends Specification {
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

    def "fails on other error"() {
        server.expectGetBroken("/cache/${key.hashCode}")

        when:
        cache.load(key) { input ->
            throw new RuntimeException("That should never be called")
        }

        then:
        BuildCacheException exception = thrown()

        exception.message == "For key '${key.hashCode}', using HTTP cache at ${server.uri}/cache/ response status 500: broken"
    }
}
