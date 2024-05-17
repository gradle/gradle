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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class NonReservedFileFilterTest extends Specification {
    @Rule TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "does not accept reserved files"() {
        given:
        def cacheDir = temporaryFolder.getTestDirectory()
        def filter = new NonReservedFileFilter([cacheDir.file("cache.properties"), cacheDir.file("gc.properties"), cacheDir.file("cache.lock")])

        expect:
        !filter.accept(cacheDir.file("cache.properties"))
        !filter.accept(cacheDir.file("gc.properties"))
        !filter.accept(cacheDir.file("cache.lock"))

        and:
        filter.accept(cacheDir.file("0"*32))
        filter.accept(cacheDir.file("ABCDEFABCDEFABCDEFABCDEFABCDEF00"))
        filter.accept(cacheDir.file("abcdefabcdefabcdefabcdefabcdef00"))
    }
}
