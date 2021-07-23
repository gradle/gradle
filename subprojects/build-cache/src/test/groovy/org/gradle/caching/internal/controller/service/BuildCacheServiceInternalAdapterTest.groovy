/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.controller.service

import org.gradle.caching.BuildCacheEntryReader
import org.gradle.caching.BuildCacheEntryWriter
import org.gradle.caching.BuildCacheException
import org.gradle.caching.BuildCacheKey
import org.gradle.caching.BuildCacheService
import org.gradle.caching.internal.BuildCacheLoadOutcomeInternal
import org.gradle.caching.internal.BuildCacheServiceInternal
import org.gradle.caching.internal.BuildCacheServiceInternalAdapter
import org.gradle.caching.internal.BuildCacheStoreOutcomeInternal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class BuildCacheServiceInternalAdapterTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def entry = new DefaultBuildCacheEntryInternal(temporaryFolder.file("file") << "test")

    BuildCacheServiceInternal adapt(BuildCacheService service) {
        new BuildCacheServiceInternalAdapter(service)
    }

    static class TestOutputStream extends OutputStream {

        boolean closed
        boolean error

        @Override
        void write(int b) throws IOException {
            if (error) {
                throw new IOException("bang!")
            }
        }

        @Override
        void close() throws IOException {
            closed = true
        }
    }

    def "can write multiple times"() {
        given:
        entry.file.text = "foo"
        def output1 = new ByteArrayOutputStream()
        def output2 = new ByteArrayOutputStream()
        def service = adapt(new BuildCacheService() {
            @Override
            boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                return false
            }

            @Override
            void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                writer.writeTo(output1)
                writer.writeTo(output2)
            }

            @Override
            void close() throws IOException {

            }
        })

        when:
        def outcome = service.store(Mock(BuildCacheKey), entry)

        then:
        outcome == BuildCacheStoreOutcomeInternal.STORED
        output1.toString() == entry.file.text
        output2.toString() == entry.file.text
    }

    def "returns not stored if writer not invoked"() {
        given:
        def service = adapt(new BuildCacheService() {
            @Override
            boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                return false
            }

            @Override
            void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
                writer.size
            }

            @Override
            void close() throws IOException {

            }
        })

        when:
        def outcome = service.store(Mock(BuildCacheKey), entry)

        then:
        outcome == BuildCacheStoreOutcomeInternal.NOT_STORED
    }

    def "can load more than once"() {
        given:
        when:
        def service = adapt(new BuildCacheService() {
            @Override
            boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                reader.readFrom(new ByteArrayInputStream("foo".bytes))
                reader.readFrom(new ByteArrayInputStream("foo".bytes))
                true
            }

            @Override
            void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {

            }

            @Override
            void close() throws IOException {

            }
        })

        def outcome = service.load(Mock(BuildCacheKey), entry)

        then:
        outcome == BuildCacheLoadOutcomeInternal.LOADED
        entry.file.text == "foo"
    }

    def "return value is honoured"() {
        given:
        when:
        def service = adapt(new BuildCacheService() {
            @Override
            boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                reader.readFrom(new ByteArrayInputStream("foo".bytes))
                false
            }

            @Override
            void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {

            }

            @Override
            void close() throws IOException {

            }
        })

        def outcome = service.load(Mock(BuildCacheKey), entry)

        then:
        outcome == BuildCacheLoadOutcomeInternal.NOT_LOADED
        entry.file.text == "foo"
    }

    def "closes adaptee on close"() {
        when:
        def closed = false
        def service = adapt(new BuildCacheService() {
            @Override
            boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
                return false
            }

            @Override
            void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {

            }

            @Override
            void close() throws IOException {
                closed = true
            }
        })
        service.close()

        then:
        closed
    }

}
