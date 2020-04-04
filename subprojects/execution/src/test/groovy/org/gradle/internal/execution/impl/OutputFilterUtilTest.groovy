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

import com.google.common.collect.ImmutableList
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.MutableReference
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint
import org.gradle.internal.fingerprint.FileCollectionFingerprint
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint
import org.gradle.internal.snapshot.CompleteDirectorySnapshot
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.internal.execution.impl.OutputFilterUtil.filterOutputSnapshotAfterExecution
import static org.gradle.internal.execution.impl.OutputFilterUtil.filterOutputSnapshotBeforeExecution

class OutputFilterUtilTest extends Specification {

    private static final FileCollectionFingerprint EMPTY_OUTPUT_FINGERPRINT = AbsolutePathFingerprintingStrategy.IGNORE_MISSING.emptyFingerprint

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    def virtualFileSystem = TestFiles.virtualFileSystem()

    def "pre-existing directories are filtered"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def beforeExecution = snapshotOutput(outputDir)
        outputDir.file()

        when:
        def filteredOutputs = filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, beforeExecution)
        then:
        filteredOutputs.empty

        when:
        def outputDirFile = outputDir.file("in-output-dir").createFile()
        virtualFileSystem.invalidateAll()
        def afterExecution = snapshotOutput(outputDir)
        filteredOutputs = filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, afterExecution)
        then:
        collectFiles(filteredOutputs) == [outputDir, outputDirFile]
    }

    def "only newly created files in directory are part of filtered outputs"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        outputDir.file("outputOfOther").createFile()
        def beforeExecution = snapshotOutput(outputDir)

        when:
        def filteredOutputs = filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, beforeExecution)
        then:
        filteredOutputs.empty

        when:
        def outputOfCurrent = outputDir.file("outputOfCurrent").createFile()
        def afterExecution = snapshotOutput(outputDir)
        filteredOutputs = filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, afterExecution)
        then:
        collectFiles(filteredOutputs) == [outputDir, outputOfCurrent]
    }

    def "previous outputs remain outputs"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def outputDirFile = outputDir.file("outputOfCurrent").createFile()
        def previousExecution = fingerprintOutput(outputDir)
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
        filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, beforeExecution).empty
    }

    def "added empty dir is captured"() {
        def emptyDir = temporaryFolder.file("emptyDir").createDir()
        def afterExecution = snapshotOutput(emptyDir)
        def beforeExecution = FileSystemSnapshot.EMPTY
        expect:
        collectFiles(filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, afterExecution)) == [emptyDir]
        filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, afterExecution, afterExecution).empty
    }

    def "updated files in output directory are part of the output"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        def existingFile = outputDir.file("some").createFile()
        def beforeExecution = snapshotOutput(outputDir)
        existingFile << "modified"
        def afterExecution = snapshotOutput(outputDir)
        expect:
        collectFiles(filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, afterExecution)) == [outputDir, existingFile]
    }

    def "updated files are part of the output"() {
        def existingFile = temporaryFolder.file("some").createFile()
        def beforeExecution = snapshotOutput(existingFile)
        existingFile << "modified"
        def afterExecution = snapshotOutput(existingFile)
        expect:
        collectFiles(filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, afterExecution)) == [existingFile]
    }

    def "removed files are not considered outputs"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        def outputDirFile = outputDir.file("toBeDeleted").createFile()
        def afterPreviousExecutionFingerprint = fingerprintOutput(outputDir)
        def beforeExecutionSnapshot = snapshotOutput(outputDir)
        outputDirFile.delete()
        def afterExecution = snapshotOutput(outputDir)

        expect:
        collectFiles(filterOutputSnapshotAfterExecution(afterPreviousExecutionFingerprint, beforeExecutionSnapshot, afterExecution)) == [outputDir]
        filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, afterPreviousExecutionFingerprint, afterExecution).empty
    }

    def "overlapping directories are not included"() {
        def outputDir = temporaryFolder.createDir("outputDir")
        outputDir.createDir("output-dir-2")
        def beforeExecution = snapshotOutput(outputDir)
        def outputDirFile = outputDir.createFile("outputDirFile")
        def afterExecution = snapshotOutput(outputDir)

        expect:
        collectFiles(filterOutputSnapshotAfterExecution(EMPTY_OUTPUT_FINGERPRINT, beforeExecution, afterExecution)) == [outputDir, outputDirFile]
    }

    def "overlapping files are not part of the before execution snapshot"() {
        def outputDir = temporaryFolder.file("outputDir").createDir()
        def outputDirFile = outputDir.createFile("outputDirFile")
        def afterLastExecution = fingerprintOutput(outputDir)
        outputDir.createFile("not-in-output")
        def beforeExecution = snapshotOutput(outputDir)

        expect:
        collectFiles(filterOutputSnapshotBeforeExecution(afterLastExecution, beforeExecution)) == [outputDir, outputDirFile]
    }

    private FileSystemSnapshot snapshotOutput(File output) {
        virtualFileSystem.invalidateAll()
        MutableReference<CompleteFileSystemLocationSnapshot> result = MutableReference.empty()
        virtualFileSystem.read(output.getAbsolutePath(), result.&set)
        return result.get()
    }

    private CurrentFileCollectionFingerprint fingerprintOutput(File outputDir) {
        DefaultCurrentFileCollectionFingerprint.from([snapshotOutput(outputDir)], AbsolutePathFingerprintingStrategy.IGNORE_MISSING)
    }

    List<File> collectFiles(ImmutableList<FileSystemSnapshot> fileSystemSnapshots) {
        def result = []
        fileSystemSnapshots.each {
            it.accept(new FileSystemSnapshotVisitor() {
                @Override
                boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    result.add(directorySnapshot)
                    return true
                }

                @Override
                void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                    result.add(fileSnapshot)
                }

                @Override
                void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                }
            })
        }
        result.collect { it.absolutePath as File }
    }
}
