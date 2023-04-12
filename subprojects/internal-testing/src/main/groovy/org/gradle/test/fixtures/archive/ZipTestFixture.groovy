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

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import org.gradle.test.fixtures.file.TestFile

import java.nio.charset.Charset

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

class ZipTestFixture extends ArchiveTestFixture {
    protected final String metadataCharset
    protected final String contentCharset
    private final ListMultimap<String, Integer> compressionMethodsByRelativePath = ArrayListMultimap.create()

    ZipTestFixture(File file, String metadataCharset = null, String contentCharset = null) {
        new TestFile(file).assertIsFile()
        this.metadataCharset = metadataCharset ?: Charset.defaultCharset().name()
        this.contentCharset = contentCharset ?: Charset.defaultCharset().name()
        def zipFile = new ZipFile(file, this.metadataCharset)
        try {
            def entries = zipFile.getEntries()
            while (entries.hasMoreElements()) {
                def entry = entries.nextElement()
                String content = getContentForEntry(entry, zipFile)
                if (!entry.directory) {
                    add(entry.name, content)
                } else {
                    addDir(entry.name)
                }
                addMode(entry.name, entry.getUnixMode())
                addCompressionMethod(entry.name, entry.getMethod())
            }
        } finally {
            zipFile.close();
        }
    }

    void hasCompression(String relativePath, int compressionMethod) {
        def methods = compressionMethodsByRelativePath.get(relativePath)
        assert methods.size() == 1
        assertThat(methods.get(0), equalTo(compressionMethod))
    }

    private void addCompressionMethod(String relativePath, int compressionMethod) {
        compressionMethodsByRelativePath.put(relativePath, compressionMethod)
    }

    private String getContentForEntry(ZipArchiveEntry entry, ZipFile zipFile) {
        def extension = entry.name.tokenize(".").last()
        if (!(extension in ["jar", "zip"])) {
            return zipFile.getInputStream(entry).getText(contentCharset)
        }
        return ""
    }
}
