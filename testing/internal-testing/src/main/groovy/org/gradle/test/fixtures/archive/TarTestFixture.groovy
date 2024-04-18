/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.test.fixtures.archive

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.io.IOUtils
import org.gradle.test.fixtures.file.TestFile

import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

class TarTestFixture extends ArchiveTestFixture {
    private final TestFile tarFile

    TarTestFixture(TestFile tarFile, String metadataCharset = null, String contentCharset = null) {
        this.tarFile = tarFile

        boolean gzip = !tarFile.name.endsWith("tar")
        tarFile.withInputStream { inputStream ->
            TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzip ? new GZIPInputStream(inputStream) : inputStream, metadataCharset)
            for (TarArchiveEntry tarEntry = getNextEntry(tarInputStream); tarEntry != null; tarEntry = getNextEntry(tarInputStream)) {
                addMode(tarEntry.name, tarEntry.mode)
                if (tarEntry.directory) {
                    continue
                }
                def tarEntryContent = getContentForEntry(tarEntry, tarInputStream, contentCharset)
                add(tarEntry.name, tarEntryContent)
            }
        }
    }

    private static TarArchiveEntry getNextEntry(TarArchiveInputStream tarInputStream) {
        (TarArchiveEntry) tarInputStream.nextEntry
    }

    private static String getContentForEntry(TarArchiveEntry entry, TarArchiveInputStream inputStream, String contentCharset) {
        byte[] content = IOUtils.readFully(inputStream, (int) entry.getSize())
        new String(content, contentCharset ?: Charset.defaultCharset().name())
    }
}
