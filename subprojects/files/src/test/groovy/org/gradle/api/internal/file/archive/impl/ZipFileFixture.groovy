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

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream


trait ZipFileFixture {
    static final String ZIP_ENTRY_CONTENT = "foo"

    static byte[] readAllBytes(InputStream is) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        int b = is.read()
        while(b != -1) {
            baos.write(b)
            b = is.read()
        }
        return baos.toByteArray()
    }

    File makeZip(String zipFileName) {
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

    abstract TestNameTestDirectoryProvider getTemporaryFolder()
}
