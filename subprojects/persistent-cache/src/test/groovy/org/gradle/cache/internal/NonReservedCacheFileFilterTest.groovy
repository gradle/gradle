/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal

import org.gradle.cache.CleanableStore
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class NonReservedCacheFileFilterTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    def "filters for cache entry files"() {
        given:
        def cacheDir = temporaryFolder.file("cache-dir").createDir()
        def filter = new NonReservedCacheFileFilter(Mock(CleanableStore) {
            getReservedCacheFiles() >> Arrays.asList(cacheDir.file("cache.properties"), cacheDir.file("gc.properties"), cacheDir.file("cache.lock"))
        })

        expect:
        !filter.accept(cacheDir.file("cache.properties").touch())
        !filter.accept(cacheDir.file("gc.properties").touch())
        !filter.accept(cacheDir.file("cache.lock").touch())

        and:
        filter.accept(cacheDir.file("0"*32).touch())
        filter.accept(cacheDir.file("ABCDEFABCDEFABCDEFABCDEFABCDEF00").touch())
        filter.accept(cacheDir.file("abcdefabcdefabcdefabcdefabcdef00").touch())
    }
}
