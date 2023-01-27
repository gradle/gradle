/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal

import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheKey
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class H2LocalCacheServiceTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    String hashCode = "1234abcd"
    BuildCacheKey key = Mock(BuildCacheKey) {
        getHashCode() >> hashCode
    }

    def "h2 database is initialized"() {
        def dbDir = temporaryFolder.createDir("h2db")

        expect:
        new H2LocalCacheService(dbDir.toPath())
    }

    def "can write to h2"() {
        def dbDir = temporaryFolder.createDir("h2db")
        def service = new H2LocalCacheService(dbDir.toPath())

        expect:
        service.store(key, new BuildCacheEntryWriter() {
            @Override
            void writeTo(OutputStream output) throws IOException {
                output << "Hello world"
            }

            @Override
            long getSize() {
                return 100
            }
        })
    }

    def "can write and read from h2"() {
        def dbDir = temporaryFolder.createDir("h2db")
        def service = new H2LocalCacheService(dbDir.toPath())
        service.store(key, new BuildCacheEntryWriter() {
            @Override
            void writeTo(OutputStream output) throws IOException {
                output << "Hello world"
            }

            @Override
            long getSize() {
                return 100
            }
        })

        expect:
        service.load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert input.text == "Hello world"
            }
        })
    }
}
