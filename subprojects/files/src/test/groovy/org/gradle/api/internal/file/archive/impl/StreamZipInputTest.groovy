/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.file.archive.impl

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class StreamZipInputTest extends Specification implements ZipFileFixture {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "cannot read from a zip entry stream a second time"() {
        def zipInput = new StreamZipInput(makeZip("foo.zip").newInputStream())

        when:
        def zipEntry = zipInput.iterator().next()
        def content = zipEntry.withInputStream { readAllBytes(it) }

        then:
        content == ZIP_ENTRY_CONTENT.bytes
        noExceptionThrown()

        when:
        zipEntry.withInputStream { readAllBytes(it) }

        then:
        thrown(IllegalStateException)

        cleanup:
        zipInput?.close()
    }

    def "cannot read zip entry content a second time"() {
        def zipInput = new StreamZipInput(makeZip("foo.zip").newInputStream())

        when:
        def zipEntry = zipInput.iterator().next()
        def content = zipEntry.content

        then:
        content == ZIP_ENTRY_CONTENT.bytes
        noExceptionThrown()

        when:
        zipEntry.content

        then:
        thrown(IllegalStateException)

        cleanup:
        zipInput?.close()
    }
}
