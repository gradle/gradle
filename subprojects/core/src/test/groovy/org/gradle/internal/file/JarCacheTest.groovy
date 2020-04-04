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

import org.gradle.internal.hash.FileHasher
import org.gradle.internal.hash.HashCode
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class JarCacheTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
    def original = tmpDir.createFile("original.txt")
    def cacheDir = tmpDir.createDir("cache")
    def fileHasher = Stub(FileHasher)
    def cache = new JarCache(fileHasher)

    def "copies file into cache when it has not been seen before"() {
        given:
        fileHasher.hash(original) >> HashCode.fromInt(123)

        expect:
        def cached = cache.getCachedJar(original, cacheDir)
        cached != original
        cached.text == original.text
        cached.name == original.name
        cached.parentFile.parentFile == cacheDir
    }

    def "reuses cached file when it has not changed"() {
        given:
        def copy = cache(original)

        when:
        def result = cache.getCachedJar(original, cacheDir)

        then:
        result == copy
    }

    def "copies file into cache when its content has changed"() {
        given:
        fileHasher.hash(original) >>> [HashCode.fromInt(123), HashCode.fromInt(234)]
        def copy = cache(original)
        original.text = "this is some new content"

        when:
        def result = cache.getCachedJar(original, cacheDir)

        then:
        result != copy
        result != original
        result.text == original.text
    }

    def "does not copy file into cache when it already exists in the cache directory"() {
        given:
        def copy = new JarCache(fileHasher).getCachedJar(original, cacheDir)

        when:
        def result = cache.getCachedJar(original, cacheDir)

        then:
        result == copy
    }

    def "copies file again when it has been deleted from the cache directory"() {
        given:
        def copy = cache(original)
        copy.delete()

        when:
        def result = cache.getCachedJar(original, cacheDir)

        then:
        result == copy
        copy.text == original.text
    }

    def cache(TestFile original)  {
        return cache.getCachedJar(original, cacheDir)
    }
}
