/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.execution

import com.google.common.collect.ImmutableSortedMap
import org.gradle.api.execution.internal.TaskInputsListeners
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileCollectionInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.execution.BuildOutputCleanupRegistry
import org.gradle.internal.execution.ExecutionOutcome
import org.gradle.internal.execution.OutputChangeListener
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultEmptySourceTaskSkipperTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    final task = Stub(TaskInternal)
    final inputFiles = Mock(FileCollectionInternal)
    final sourceFiles = Mock(FileCollectionInternal)
    final taskInputsListeners = Mock(TaskInputsListeners)
    final cleanupRegistry = Mock(BuildOutputCleanupRegistry)
    final outputChangeListener = Mock(OutputChangeListener)
    final skipper = new DefaultEmptySourceTaskSkipper(cleanupRegistry, TestFiles.deleter(), outputChangeListener, taskInputsListeners)
    final fileCollectionSnapshotter = TestFiles.fileCollectionSnapshotter()

    def "skips task when sourceFiles are empty and previous output is empty"() {
        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, [:])

        then:
        outcome.get() == ExecutionOutcome.SHORT_CIRCUITED

        and:
        1 * sourceFiles.empty >> true
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, sourceFiles)

        then:
        0 * _
    }

    def "deletes previous output when sourceFiles are empty"() {
        given:
        def previousFile = temporaryFolder.createFile("output.txt")
        def previousOutputFiles = snapshot(previousFile)

        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, previousOutputFiles)

        then:
        outcome.get() == ExecutionOutcome.EXECUTED_NON_INCREMENTALLY

        and:
        1 * sourceFiles.empty >> true

        then:
        1 * outputChangeListener.beforeOutputChange(rootPaths(previousFile))

        then:
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> true
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile.parentFile) >> false

        then:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, sourceFiles)

        then:
        !previousFile.exists()
        0 * _
    }

    def "does not delete previous output when they are not safe to delete"() {
        given:
        def previousFile = temporaryFolder.createFile("output.txt")
        def previousOutputFiles = snapshot(previousFile)

        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, previousOutputFiles)

        then:
        outcome.get() == ExecutionOutcome.SHORT_CIRCUITED

        and:
        1 * sourceFiles.empty >> true

        then:
        1 * outputChangeListener.beforeOutputChange(rootPaths(previousFile))

        then:
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> false

        then:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, sourceFiles)

        then:
        previousFile.exists()
        0 * _
    }

    def "does not delete non-empty directories"() {
        given:
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

        def previousOutputFiles = snapshot(outputDir, outputFile)

        def overlappingFile = subDir.file("overlap")
        overlappingFile << "overlapping file"

        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, previousOutputFiles)

        then:
        outcome.get() == ExecutionOutcome.EXECUTED_NON_INCREMENTALLY

        and:
        1 * sourceFiles.empty >> true

        then:
        1 * outputChangeListener.beforeOutputChange(rootPaths(outputDir, outputFile))

        then:
        _ * cleanupRegistry.isOutputOwnedByBuild(subDir) >> true
        _ * cleanupRegistry.isOutputOwnedByBuild(outputDir) >> true
        _ * cleanupRegistry.isOutputOwnedByBuild(outputDir.parentFile) >> false
        outputFiles.each {
            1 * cleanupRegistry.isOutputOwnedByBuild(it) >> true
        }
        outputDir.exists()
        subDir.exists()
        overlappingFile.exists()
        outputFiles.each {
            assert !it.exists()
        }

        then:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, sourceFiles)

        then:
        0 * _
    }

    def "exception thrown when sourceFiles are empty and deletes previous output, but delete fails"() {
        given:
        def previousFile = temporaryFolder.createFile("output.txt")
        def previousOutputFiles = snapshot(previousFile)

        when:
        skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, previousOutputFiles)

        then:
        def ex = thrown Exception
        ex.message.contains("Couldn't delete ${previousFile.absolutePath}")

        and:
        1 * sourceFiles.empty >> true

        then:
        1 * outputChangeListener.beforeOutputChange(rootPaths(previousFile))

        then: "deleting the previous file fails"
        1 * cleanupRegistry.isOutputOwnedByBuild(previousFile) >> {
            // Convert file into directory here, so that deletion in DefaultEmptySourceTaskSkipper fails
            assert previousFile.delete()
            previousFile.file("subdir/inSubdir.txt") << "inSubdir"
            return true
        }
    }

    def "does not skip when sourceFiles are not empty"() {
        when:
        def outcome = skipper.skipIfEmptySources(task, true, inputFiles, sourceFiles, [:])

        then:
        !outcome.present

        and:
        1 * sourceFiles.empty >> false

        then:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, inputFiles)
        0 * _
    }

    def "does not skip when it has not declared any source files"() {
        when:
        def outcome = skipper.skipIfEmptySources(task, false, inputFiles, sourceFiles, [:])

        then:
        !outcome.present

        and:
        1 * taskInputsListeners.broadcastFileSystemInputsOf(task, inputFiles)
        0 * _
    }

    def snapshot(File... files) {
        ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "output", fileCollectionSnapshotter.snapshot(TestFiles.fixed(files))
        )
    }

    Set<String> rootPaths(File... files) {
        files*.absolutePath as Set
    }
}
