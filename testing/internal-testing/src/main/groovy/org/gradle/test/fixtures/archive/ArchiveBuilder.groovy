/*
 * Copyright 2026 the original author or authors.
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

import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds archives in memory, for tests that need archive content without writing every level to disk.
 */
class ArchiveBuilder {

    /**
     * Returns the bytes of an archive containing a single entry with the given content.
     *
     * @param entryName the name of the single entry
     * @param content the content of the entry
     * @return the bytes of the archive
     */
    static byte[] archiveContaining(String entryName, byte[] content) {
        def crc = new CRC32()
        crc.update(content)
        def entry = new ZipEntry(entryName)
        entry.method = ZipEntry.STORED
        entry.size = content.length
        entry.compressedSize = content.length
        entry.crc = crc.value

        def bytes = new ByteArrayOutputStream(content.length + 256)
        new ZipOutputStream(bytes).withCloseable { zipStream ->
            zipStream.putNextEntry(entry)
            zipStream.write(content)
            zipStream.closeEntry()
        }
        return bytes.toByteArray()
    }

    /**
     * Returns the bytes of an archive wrapping a text file in the given number of nested archives,
     * i.e. a depth of 1 produces an archive containing an archive containing the text file.
     *
     * @param depth the number of nested archives to wrap the text file in
     * @return the bytes of the outermost archive
     */
    static byte[] nestedArchive(int depth) {
        byte[] payload = archiveContaining("foo", "Foo".bytes)
        depth.times {
            payload = archiveContaining("nested.jar", payload)
        }
        return payload
    }
}
