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

import com.zaxxer.hikari.HikariDataSource
import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.internal.controller.service.StoreTarget
import org.gradle.internal.time.MockClock
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class H2BuildCacheServiceTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def dbDir = temporaryFolder.createDir("h2db")
    def service = new H2BuildCacheService(dbDir.toPath(), 20)

    def cleanup() {
        service.close()
    }

    BuildCacheKey key = Mock(BuildCacheKey) {
        getHashCode() >> "1234abcd"
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
        new H2BuildCacheService(dbDir.toPath(), 20).load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert input.text == "Hello world"
            }
        })
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

    def "updates last access timestamp on read"() {
        given:
        def tmpFile = temporaryFolder.createFile("test")
        tmpFile << "Hello world"
        def mockClock = new MockClock(0)
        def service = new H2BuildCacheService(dbDir.toPath(), 20, mockClock)
        def dataSource = service.dataSource

        when:
        service.store(key, new StoreTarget(tmpFile))
        def afterStoreTimestamp = readTimestamp(dataSource, key)
        service.load(key, new BuildCacheEntryReader() {
            @Override
            void readFrom(InputStream input) throws IOException {
                assert input.text == "Hello world"
            }
        })
        def afterLoadTimestamp = readTimestamp(dataSource, key)

        then:
        afterStoreTimestamp == 10
        afterLoadTimestamp == 20
    }

    private long readTimestamp(HikariDataSource dataSource, BuildCacheKey key) {
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("select entry_last_access from filestore.catalog where entry_key = ?")) {
                stmt.setString(1, key.getHashCode());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong(1);
                    } else {
                        return -1
                    }
                }
            }
        }
    }
}
