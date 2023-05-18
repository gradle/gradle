/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.JavaVersion
import org.gradle.internal.file.FileException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.Specification

class FileZipInputTest extends Specification implements ZipFileFixture{
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def "selects the correct zip input type"() {
        def file = makeZip("foo.zip")

        when:
        def zipInput = FileZipInput.create(file)

        then:
        if (JavaVersion.current().java11Compatible) {
            assert zipInput instanceof FileZipInput
        } else {
            assert zipInput instanceof StreamZipInput
        }

        cleanup:
        zipInput?.close()
    }

    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "throws FileException when zip is badly formed"() {
        def file = temporaryFolder.file("badly-formed").createFile()

        when:
        FileZipInput.create(file)

        then:
        thrown(FileException)
    }

    // This documents current behaviour, not desired behaviour
    @Requires(UnitTestPreconditions.Jdk10OrEarlier)
    def "silently ignores zip that is badly formed"() {
        def file = temporaryFolder.file("badly-formed").createFile()

        when:
        def zipInput = FileZipInput.create(file)
        zipInput.forEach {throw new RuntimeException() }

        then:
        noExceptionThrown()

        cleanup:
        zipInput?.close()
    }

    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "can read from zip input stream a second time"() {
        def file = makeZip("foo.zip")
        def zipInput = FileZipInput.create(file)

        when:
        def zipEntry = zipInput.iterator().next()
        def content = zipEntry.withInputStream { readAllBytes(it) }

        then:
        content == ZIP_ENTRY_CONTENT.bytes

        when:
        content = zipEntry.withInputStream { readAllBytes(it) }

        then:
        noExceptionThrown()
        content == ZIP_ENTRY_CONTENT.bytes

        cleanup:
        zipInput?.close()
    }

    @Requires(UnitTestPreconditions.Jdk11OrLater)
    def "can read zip entry content a second time"() {
        def file = makeZip("foo.zip")
        def zipInput = FileZipInput.create(file)

        when:
        def zipEntry = zipInput.iterator().next()
        def content = zipEntry.content

        then:
        content == ZIP_ENTRY_CONTENT.bytes

        when:
        content = zipEntry.content

        then:
        noExceptionThrown()
        content == ZIP_ENTRY_CONTENT.bytes

        cleanup:
        zipInput?.close()
    }
}
