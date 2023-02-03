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

package org.gradle.internal.execution.history.impl

import com.google.common.collect.ImmutableSortedMap
import org.gradle.internal.file.FileMetadata.AccessType
import org.gradle.internal.file.impl.DefaultFileMetadata
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.snapshot.DirectorySnapshot
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.MissingFileSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification

class DefaultOverlappingOutputDetectorTest extends Specification {
    def detector = new DefaultOverlappingOutputDetector()

    def "detects no overlap when there are none"() {
        def previousOutputFiles = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "output", FileSystemSnapshot.EMPTY
        )
        def outputFilesBeforeExecution = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "output", FileSystemSnapshot.EMPTY
        )
        expect:
        detector.detect(previousOutputFiles, outputFilesBeforeExecution) == null
    }

    def "detects overlap when there is a stale root"() {
        def staleFileAddedBetweenExecutions = new RegularFileSnapshot("/absolute/path", "path", TestHashCodes.hashCodeFrom(1234), DefaultFileMetadata.file(0, 0, AccessType.DIRECT))
        def previousOutputFiles = ImmutableSortedMap.<String, FileSystemSnapshot>of(
            "output", FileSystemSnapshot.EMPTY
        )
        def outputFilesBeforeExecution = ImmutableSortedMap.<String, FileSystemSnapshot>of(
            "output", staleFileAddedBetweenExecutions
        )

        when:
        def overlaps = detector.detect(previousOutputFiles, outputFilesBeforeExecution)

        then:
        overlaps.propertyName == "output"
        overlaps.overlappedFilePath == "/absolute/path"
    }

    def "detects overlap when there is a stale #type in an output directory"() {
        def emptyDirectory = new DirectorySnapshot("/absolute", "absolute", AccessType.DIRECT, TestHashCodes.hashCodeFrom(0x1234), [])
        def directoryWithStaleBrokenSymlink = new DirectorySnapshot("/absolute", "absolute", AccessType.DIRECT, TestHashCodes.hashCodeFrom(0x5678), [
            staleEntry
        ])
        def previousOutputFiles = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "output", emptyDirectory
        )
        def outputFilesBeforeExecution = ImmutableSortedMap.<String, FileSystemSnapshot> of(
            "output", directoryWithStaleBrokenSymlink
        )

        when:
        def overlaps = detector.detect(previousOutputFiles, outputFilesBeforeExecution)

        then:
        overlaps.propertyName == "output"
        overlaps.overlappedFilePath == "/absolute/path"

        where:
        type             | staleEntry
        "file"           | new RegularFileSnapshot("/absolute/path", "path", TestHashCodes.hashCodeFrom(123), DefaultFileMetadata.file(0L, 0L, AccessType.DIRECT))
        "directory"      | new DirectorySnapshot("/absolute/path", "path", AccessType.DIRECT, TestHashCodes.hashCodeFrom(123), [])
        "broken symlink" | new MissingFileSnapshot("/absolute/path", "path", AccessType.VIA_SYMLINK)
    }
}
