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
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.controller.service.StoreTarget
import org.gradle.internal.time.Clock
import org.gradle.internal.time.Time
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.concurrent.TimeUnit

class H2BuildCacheServiceTest extends Specification {

    private static final int REMOVE_UNUSED_ENTRIES_AFTER_7_DAYS = 7

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def dbDir = temporaryFolder.createDir("h2db")
    H2BuildCacheService service

    def setup() {
        openServiceWithClock(Time.clock())
    }

    def cleanup() {
        service.close()
    }

    BuildCacheKey key = Mock(BuildCacheKey) {
        getHashCode() >> "1234abcd"
    }
    BuildCacheKey otherKey = Mock(BuildCacheKey) {
        getHashCode() >> "12345abcde"
    }

    def "can write to h2"() {
        given:
        def tmpFile = temporaryFolder.createFile("test")
        tmpFile << "Hello world"

        expect:
        service.store(key, new StoreTarget(tmpFile))
    }

    def "can write and read from h2"() {
        given:
        def tmpFile = temporaryFolder.createFile("test")
        tmpFile << "Hello world"

        service.store(key, new StoreTarget(tmpFile))

        expect:
        service.load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert input.text == "Hello world"
            }
        })
    }

    def "can write to h2 and stop the service and read from h2 with a new service"() {
        given:
        def tmpFile = temporaryFolder.createFile("test")
        tmpFile << "Hello world"

        service.store(key, new StoreTarget(tmpFile))
        service.close()

        expect:
        def newService = new H2BuildCacheService(dbDir.toPath(), 20, 7, Time.clock())
        newService.open()
        newService.load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert input.text == "Hello world"
            }
        })

        cleanup:
        newService.close()
    }

    def "doesn't write to h2 if the entry with the same key already exists"() {
        given:
        def firstFile = temporaryFolder.createFile("first")
        firstFile << "Hello world"
        def secondFile = temporaryFolder.createFile("second")
        secondFile << "Hello Bob"

        when:
        service.store(key, new StoreTarget(firstFile))
        service.store(key, new StoreTarget(secondFile))

        then:
        service.load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert input.text == "Hello world"
            }
        })
    }

    def "does a cleanup of old entries over a moving period of time"() {
        given:
        def firstFile = temporaryFolder.createFile("first")
        firstFile << "Hello world"
        def secondFile = temporaryFolder.createFile("second")
        secondFile << "Hello Bob"
        def now = Time.currentTimeMillis()
        openServiceWithClock({ now - TimeUnit.DAYS.toMillis(6) })
        service.store(key, new StoreTarget(secondFile))
        openServiceWithClock({ now })
        service.store(otherKey, new StoreTarget(firstFile))

        when:
        service.cleanup()

        then:
        service.open()
        service.contains(key)
        service.contains(otherKey)
        dbDir.listFiles().collect { it.name } == ["filestore.mv.db"]

        when:
        openServiceWithClock({ now + TimeUnit.DAYS.toMillis(6) })
        service.cleanup()

        then:
        service.open()
        !service.contains(key)
        service.contains(otherKey)
        dbDir.listFiles().collect { it.name } == ["filestore.mv.db"]

        when:
        openServiceWithClock({ now + TimeUnit.DAYS.toMillis(8) })
        service.cleanup()

        then:
        service.open()
        !service.contains(key)
        !service.contains(otherKey)
        dbDir.listFiles().collect { it.name } == ["filestore.mv.db"]
    }

    private void openServiceWithClock(Clock clock) {
        if (service != null) {
            service.close()
        }
        service = new H2BuildCacheService(dbDir.toPath(), 20, REMOVE_UNUSED_ENTRIES_AFTER_7_DAYS, clock)
        service.open()
    }
}
