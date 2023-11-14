/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.execution.steps

import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.execution.history.BeforeExecutionState
import org.gradle.internal.execution.history.OverlappingOutputs
import org.gradle.internal.execution.history.PreviousExecutionState
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.FileSystemLocationSnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification

import static org.gradle.internal.snapshot.DirectorySnapshotBuilder.EmptyDirectoryHandlingStrategy.INCLUDE_EMPTY_DIRS

class OverlappingOutputsFilterTest extends Specification {
    def filter = new OverlappingOutputsFilter()

    def "overlapping outputs are captured"() {
        def staleFile = fileSnapshot("stale", TestHashCodes.hashCodeFrom(123))
        def outputFile = fileSnapshot("outputs", TestHashCodes.hashCodeFrom(345))

        def emptyDirectory = directorySnapshot()
        def directoryWithStaleFile = directorySnapshot(staleFile)
        def directoryWithStaleFileAndOutput = directorySnapshot(outputFile, staleFile)
        def directoryWithOutputFile = directorySnapshot(outputFile)

        def previousOutputs = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "outputDir", emptyDirectory
        )
        def outputsBeforeExecution = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "outputDir", directoryWithStaleFile
        )
        def outputsAfterExecution = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "outputDir", directoryWithStaleFileAndOutput
        )
        def filteredOutputs = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "outputDir", directoryWithOutputFile
        )
        def overlappingOutputs = Mock(OverlappingOutputs)

        def beforeExecutionState = Stub(BeforeExecutionState) {
            detectedOverlappingOutputs >> Optional.of(overlappingOutputs)
            outputFileLocationSnapshots >> outputsBeforeExecution
        }
        def previousExecutionState = Stub(PreviousExecutionState) {
            getOutputFilesProducedByWork() >> previousOutputs
        }
        def context = Stub(BeforeExecutionContext) {
            getBeforeExecutionState() >> Optional.of(beforeExecutionState)
            getPreviousExecutionState() >> Optional.of(previousExecutionState)
        }

        when:
        def result = filter.filterOutputs(context, beforeExecutionState, outputsAfterExecution)

        then:
        result == filteredOutputs
        0 * _
    }

    private static RegularFileSnapshot fileSnapshot(String name, HashCode contentHash) {
        new RegularFileSnapshot("/absolute/${name}", name, contentHash, DefaultFileMetadata.file(0L, 0L, FileMetadata.AccessType.DIRECT))
    }

    private static FileSystemLocationSnapshot directorySnapshot(RegularFileSnapshot... contents) {
        def builder = MerkleDirectorySnapshotBuilder.sortingRequired()
        builder.enterDirectory(FileMetadata.AccessType.DIRECT, "/absolute", "absolute", INCLUDE_EMPTY_DIRS)
        contents.each {
            builder.visitLeafElement(it)
        }
        builder.leaveDirectory()
        return builder.result
    }
}
