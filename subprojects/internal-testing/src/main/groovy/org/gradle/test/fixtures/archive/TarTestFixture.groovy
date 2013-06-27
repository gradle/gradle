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
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.file.FileVisitor
import org.gradle.api.internal.file.FileResource
import org.gradle.api.internal.file.archive.TarFileTree
import org.gradle.test.fixtures.file.TestFile
import static org.junit.Assert.*

class TarTestFixture {

    private final TestFile tarFile
    private final TestFile temporaryDir

    MultiValueMap filesByRelativePath = new MultiValueMap()

    FileResource readableFile = new FileResource(tarFile)

    public TarTestFixture(TestFile tarFile, TestFile tempDirectory) {
        this.temporaryDir = tempDirectory
        this.tarFile = tarFile

        new TarFileTree(readableFile, temporaryDir).visit(new FileVisitor() {
            void visitDir(FileVisitDetails dirDetails) {}

            void visitFile(FileVisitDetails fileDetails) {
                filesByRelativePath[fileDetails.relativePath.toString()] = fileDetails.file
            }
        })
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
