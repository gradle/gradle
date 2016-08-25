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

package org.gradle.internal.file

import org.gradle.internal.Factory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class JarCacheTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def baseDirFactory = Mock(Factory)
    def original = tmpDir.createFile("original.txt")
    def cacheDir = tmpDir.createDir("cache")
    def cache = new JarCache()

    def "copies file into cache when it has not been seen before"() {
        given:
        baseDirFactory.create() >> cacheDir

        expect:
        def cached = cache.getCachedJar(original, baseDirFactory)
        cached != original
        cached.text == original.text
        cached.name == original.name
    }

    def "reuses cached file when it has not changed"() {
        given:
        def copy = cache(original)

        when:
        def result = cache.getCachedJar(original, baseDirFactory)

        then:
        result == copy

        and:
        0 * _
    }

    def "reuses cached file when its content has not changed"() {
        given:
        def copy = cache(original)
        original.lastModified = original.lastModified() + 2000

        when:
        def result = cache.getCachedJar(original, baseDirFactory)

        then:
        result == copy

        and:
        0 * _
    }

    def "copies file into cache when its content has changed"() {
        def originalLastModified = original.lastModified()

        given:
        def copy = cache(original)
        original.text = "this is some new content"
        original.lastModified = originalLastModified

        when:
        def result = cache.getCachedJar(original, baseDirFactory)

        then:
        result != copy
        result != original
        result.text == original.text

        and:
        1 * baseDirFactory.create() >> cacheDir
        0 * _
    }

    def "does not copy file into cache when it already exists in the cache directory"() {
        given:
        def copy = new JarCache().getCachedJar(original, { cacheDir} as Factory)
        original.lastModified = original.lastModified() + 2000

        when:
        def result = cache.getCachedJar(original, baseDirFactory)

        then:
        result == copy

        and:
        1 * baseDirFactory.create() >> cacheDir
        0 * _
    }

    def "copies file again when it has been deleted from the cache directory"() {
        given:
        def copy = cache(original)
        copy.delete()

        when:
        def result = cache.getCachedJar(original, baseDirFactory)

        then:
        result == copy
        copy.text == original.text

        and:
        1 * baseDirFactory.create() >> cacheDir
        0 * _
    }

    def cache(TestFile original)  {
        return cache.getCachedJar(original, { cacheDir } as Factory)
    }
}
