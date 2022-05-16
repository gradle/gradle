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

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


class StreamZipInputTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    private static final ZIP_ENTRY_CONTENT = "foo"

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

    private static byte[] readAllBytes(InputStream is) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        int b = is.read()
        while(b != -1) {
            baos.write(b)
            b = is.read()
        } 
        return baos.toByteArray()
    }

    private File makeZip(String zipFileName) {
        def zipFile = temporaryFolder.file(zipFileName)
        def file = temporaryFolder.file("foo.txt")
        file.text = ZIP_ENTRY_CONTENT
        def zipStream = new ZipOutputStream(zipFile.newOutputStream())
        def zipEntry = new ZipEntry(file.name)
        zipEntry.setSize(file.size())
        zipStream.putNextEntry(zipEntry)
        zipStream.write(file.bytes)
        zipStream.close()
        return zipFile
    }
}
