/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.execution.history

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.file.FileType
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.function.Predicate

@CleanupTestDirectory
class OutputsCleanerTest extends Specification {
    @Rule
    public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    def deleter = TestFiles.deleter()
    Predicate<File> fileSafeToDelete = Mock()
    Predicate<File> dirSafeToDelete = Mock()
    def cleaner = new OutputsCleaner(deleter, fileSafeToDelete, dirSafeToDelete)

    def "does not delete non-empty directories"() {
        def outputFiles = []

        def outputDir = temporaryFolder.createDir("rootDir")
        outputFiles << outputDir.file("some-output.txt")

        def subDir = outputDir.createDir("subDir")
        outputFiles << subDir.file("in-subdir.txt")

        def outputFile = temporaryFolder.createFile("output.txt")
        outputFiles << outputFile

        outputFiles.each {
            it << "output ${it.name}"
        }

        def overlappingFile = subDir.file("overlap")
        overlappingFile << "overlapping file"

        when:
        cleanupOutput(outputDir, subDir, *outputFiles)
        then:
        _ * dirSafeToDelete.test(subDir) >> true
        _ * dirSafeToDelete.test(outputDir) >> true
        _ * dirSafeToDelete.test(outputDir.parentFile) >> false
        outputFiles.each {
            1 * fileSafeToDelete.test(it) >> true
        }

        subDir.directory
        cleaner.didWork
    }

    def "does delete empty directories"() {
        def outputFiles = []

        def outputDir = temporaryFolder.createDir("rootDir")
        outputFiles << outputDir.file("some-output.txt")

        def subDir = outputDir.createDir("subDir")
        outputFiles << subDir.file("in-subdir.txt")

        def outputFile = temporaryFolder.createFile("output.txt")
        outputFiles << outputFile

        outputFiles.each {
            it << "output ${it.name}"
        }

        when:
        cleanupOutput(outputDir, subDir, *outputFiles)
        then:
        _ * dirSafeToDelete.test(subDir) >> true
        _ * dirSafeToDelete.test(outputDir) >> true
        _ * dirSafeToDelete.test(outputDir.parentFile) >> false
        outputFiles.each {
            1 * fileSafeToDelete.test(it) >> true
        }

        !subDir.exists()
        !outputDir.exists()
        cleaner.didWork
    }

    private void cleanupOutput(File... files)  {
        for (File file : files) {
            cleaner.cleanupOutput(file, file.directory ? FileType.Directory : FileType.RegularFile)
        }
        cleaner.cleanupDirectories()
    }
}
