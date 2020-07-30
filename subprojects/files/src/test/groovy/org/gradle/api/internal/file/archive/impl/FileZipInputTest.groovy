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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

import java.util.zip.ZipOutputStream

class FileZipInputTest extends Specification {
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

    @Requires(TestPrecondition.JDK11_OR_LATER)
    def "throws FileException when zip is badly formed"() {
        def file = temporaryFolder.file("badly-formed").createFile()

        when:
        FileZipInput.create(file)

        then:
        thrown(FileException)
    }

    // This documents current behaviour, not desired behaviour
    @Requires(TestPrecondition.JDK10_OR_EARLIER)
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

    private File makeZip(String filename) {
        def file = temporaryFolder.file(filename)
        new ZipOutputStream(new FileOutputStream(file)).close()
        return file
    }
}
