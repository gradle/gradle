/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.history.changes

import org.apache.commons.io.FilenameUtils
import org.gradle.internal.file.FileMetadata
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.internal.snapshot.RegularFileSnapshot
import spock.lang.Specification

import static org.gradle.internal.file.impl.DefaultFileMetadata.file
import static org.gradle.internal.hash.HashCode.fromInt

class OutputFileChangesTest extends Specification {

    def "trivial absolute path change"() {
        expect:
        changes(
            fileSnapshot("one"),
            fileSnapshot("two")
        ) == [removed("one"), added("two")]
    }

    def "trivial content change"() {
        expect:
        changes(
            fileSnapshot("one", 1234),
            fileSnapshot("one", 2345)
        ) == [modified("one")]
    }

    def changes(FileSystemSnapshot previousSnapshot, FileSystemSnapshot currentSnapshot) {
        def visitor = new CollectingChangeVisitor()
        OutputFileChanges.COMPARE_STRATEGY.visitChangesSince(previousSnapshot, currentSnapshot, "test", visitor)
        visitor.getChanges().toList()
    }

    def fileSnapshot(String absolutePath, hash = 1234) {
        new RegularFileSnapshot(
            FilenameUtils.separatorsToSystem(absolutePath),
            FilenameUtils.getName(absolutePath),
            fromInt(hash),
            file(5, 5, FileMetadata.AccessType.DIRECT)
        )
    }

    def added(String path) {
        new DescriptiveChange("Output property 'test' file $path has been added.")
    }

    def removed(String path) {
        new DescriptiveChange("Output property 'test' file $path has been removed.")
    }

    def modified(String path) {
        new DescriptiveChange("Output property 'test' file $path has changed.")
    }
}
