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

class LoadTargetTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def target = new LoadTarget(temporaryFolder.file("file"))

    static class TestInputStream extends InputStream {

        boolean closed
        boolean error

        @Override
        int read() throws IOException {
            if (error) {
                throw new IOException("bang!")
            } else {
                -1
            }
        }

        @Override
        void close() throws IOException {
            closed = true
        }
    }

    def "closes input"() {
        given:
        def input = new TestInputStream()

        when:
        target.readFrom(input)

        then:
        input.closed
    }

    def "closes input on subsequent use"() {
        given:
        def input = new TestInputStream()

        when:
        target.readFrom(new ByteArrayInputStream([] as byte[]))
        target.readFrom(input)

        then:
        thrown IllegalStateException
        input.closed
    }

    def "closes input on error"() {
        given:
        def input = new TestInputStream(error: true)

        when:
        target.readFrom(input)

        then:
        thrown IOException
        input.closed
    }

}
