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

import org.apache.commons.collections.map.MultiValueMap
import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarInputStream
import org.gradle.test.fixtures.file.TestFile

import static org.junit.Assert.assertEquals

class TarTestFixture {

    private final TestFile tarFile
    private final TestFile temporaryDir

    MultiValueMap filesByRelativePath = new MultiValueMap()

    public TarTestFixture(TestFile tarFile, TestFile tempDirectory) {
        this.temporaryDir = tempDirectory
        this.tarFile = tarFile

        TarInputStream tarInputStream = new TarInputStream(this.tarFile.newInputStream())

        for (TarEntry tarEntry = tarInputStream.nextEntry; tarEntry != null; tarEntry = tarInputStream.nextEntry) {
            if (tarEntry.directory) {
                continue
            }
            filesByRelativePath[tarEntry.name] = tarEntry.file
        }
    }

    def assertContainsFile(String relativePath, int occurrences = 1) {
        assertEquals(occurrences, filesByRelativePath.getCollection(relativePath).size())
        this
    }

    File file(String path) {
        List files = filesByRelativePath[path]
        assertEquals(1, files.size())
        files.get(0)
    }
}
