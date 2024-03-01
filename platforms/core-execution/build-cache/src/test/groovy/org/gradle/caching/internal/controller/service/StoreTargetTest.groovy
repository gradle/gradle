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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification
import org.junit.Rule

class StoreTargetTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def target = new StoreTarget(temporaryFolder.file("file") << "test")

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

    def "closes output"() {
        given:
        def output = new TestOutputStream()

        when:
        target.writeTo(output)

        then:
        output.closed
    }

    def "can write multiple times"() {
        given:
        def output = new TestOutputStream()

        when:
        target.writeTo(new ByteArrayOutputStream())
        target.writeTo(output)

        then:
        output.closed
    }

    def "closes output on error"() {
        given:
        def output = new TestOutputStream(error: true)

        when:
        target.writeTo(output)

        then:
        thrown IOException
        output.closed
    }

}
