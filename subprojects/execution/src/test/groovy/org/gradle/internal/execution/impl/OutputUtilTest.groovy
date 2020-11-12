/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.execution.impl

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.MutableReference
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.SnapshotVisitorUtil
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static OutputUtil.filterOutputSnapshotAfterExecution
import static OutputUtil.filterOutputSnapshotBeforeExecution

class OutputUtilTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def virtualFileSystem = TestFiles.virtualFileSystem()
    def fileSystemAccess = TestFiles.fileSystemAccess(virtualFileSystem)

    def "pre-existing directories are filtered"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def beforeExecution = snapshotOutput(outputDir)
        outputDir.file()

        when:
        def filteredOutputs = filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, beforeExecution)
        then:
        collectFiles(filteredOutputs) == [outputDir]

        when:
        def outputDirFile = outputDir.file("in-output-dir").createFile()
        virtualFileSystem.invalidateAll()
        def afterExecution = snapshotOutput(outputDir)
        filteredOutputs = filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, afterExecution)
        then:
        collectFiles(filteredOutputs) == [outputDir, outputDirFile]
    }

    def "only newly created files in directory are part of filtered outputs"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        outputDir.file("outputOfOther").createFile()
        def beforeExecution = snapshotOutput(outputDir)

        when:
        def filteredOutputs = filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, beforeExecution)
        then:
        collectFiles(filteredOutputs) == [outputDir]

        when:
        def outputOfCurrent = outputDir.file("outputOfCurrent").createFile()
        def afterExecution = snapshotOutput(outputDir)
        filteredOutputs = filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, afterExecution)
        then:
        collectFiles(filteredOutputs) == [outputDir, outputOfCurrent]
    }

    def "previous outputs remain outputs"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def outputDirFile = outputDir.file("outputOfCurrent").createFile()
        def previousExecution = snapshotOutput(outputDir)
        outputDir.file("outputOfOther").createFile()
        def beforeExecution = snapshotOutput(outputDir)

        when:
        def filteredOutputs = filterOutputSnapshotAfterExecution(previousExecution, beforeExecution, beforeExecution)
        then:
        collectFiles(filteredOutputs) == [outputDir, outputDirFile]
    }

    def "missing files are ignored"() {
        def missingFile = temporaryFolder.file("missing")
        def beforeExecution = snapshotOutput(missingFile)
        expect:
        filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, beforeExecution) == FileSystemSnapshot.EMPTY
    }

    def "added empty dir is captured"() {
        def emptyDir = temporaryFolder.file("emptyDir").createDir()
        def afterExecution = snapshotOutput(emptyDir)
        def beforeExecution = FileSystemSnapshot.EMPTY
        expect:
        collectFiles(filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, afterExecution)) == [emptyDir]
        collectFiles(filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, afterExecution, afterExecution)) == [emptyDir]
    }

    def "updated files in output directory are part of the output"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        def existingFile = outputDir.file("some").createFile()
        def beforeExecution = snapshotOutput(outputDir)
        existingFile << "modified"
        def afterExecution = snapshotOutput(outputDir)
        expect:
        collectFiles(filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, afterExecution)) == [outputDir, existingFile]
    }

    def "updated files are part of the output"() {
        def existingFile = temporaryFolder.file("some").createFile()
        def beforeExecution = snapshotOutput(existingFile)
        existingFile << "modified"
        def afterExecution = snapshotOutput(existingFile)
        expect:
        collectFiles(filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, afterExecution)) == [existingFile]
    }

    def "removed files are not considered outputs"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        def outputDirFile = outputDir.file("toBeDeleted").createFile()
        def afterPreviousExecutionSnapshot = snapshotOutput(outputDir)
        def beforeExecutionSnapshot = snapshotOutput(outputDir)
        outputDirFile.delete()
        def afterExecution = snapshotOutput(outputDir)

        expect:
        collectFiles(filterOutputSnapshotAfterExecution(afterPreviousExecutionSnapshot, beforeExecutionSnapshot, afterExecution)) == [outputDir]
        collectFiles(filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, afterPreviousExecutionSnapshot, afterExecution)) == [outputDir]
    }

    def "overlapping directories are not included"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        outputDir.createDir("output-dir-2")
        def beforeExecution = snapshotOutput(outputDir)
        def outputDirFile = outputDir.createFile("outputDirFile")
        def afterExecution = snapshotOutput(outputDir)

        expect:
        collectFiles(filterOutputSnapshotAfterExecution(FileSystemSnapshot.EMPTY, beforeExecution, afterExecution)) == [outputDir, outputDirFile]
    }

    def "overlapping files are not part of the before execution snapshot"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def outputDirFile = outputDir.createFile("outputDirFile")
        def afterLastExecution = snapshotOutput(outputDir)
        outputDir.createFile("not-in-output")
        def beforeExecution = snapshotOutput(outputDir)

        expect:
        collectFiles(filterOutputSnapshotBeforeExecution(afterLastExecution, beforeExecution)) == [outputDir, outputDirFile]
    }

    private FileSystemSnapshot snapshotOutput(File output) {
        virtualFileSystem.invalidateAll()
        MutableReference<CompleteFileSystemLocationSnapshot> result = MutableReference.empty()
        fileSystemAccess.read(output.getAbsolutePath(), result.&set)
        return result.get()
    }

    List<File> collectFiles(FileSystemSnapshot fileSystemSnapshots) {
        SnapshotVisitorUtil.getAbsolutePaths(fileSystemSnapshots, true).collect { new File(it) }
    }
}
