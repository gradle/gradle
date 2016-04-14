/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules

import org.gradle.api.UncheckedIOException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.state.*
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.tasks.TaskInputs
import spock.lang.Issue
import spock.lang.Specification

class TaskUpToDateStateTest extends Specification {
    private TaskInternal stubTask
    private TaskHistoryRepository.History stubHistory
    private OutputFilesCollectionSnapshotter stubOutputFileSnapshotter
    private FileCollectionSnapshotter stubInputFileSnapshotter
    private FileCollectionSnapshotter stubDiscoveredInputFileSnapshotter
    private FileCollectionFactory fileCollectionFactory = Mock(FileCollectionFactory)

    def setup() {
        TaskInputs stubInputs = Stub(TaskInputs)
        TaskOutputsInternal stubOutputs = Stub(TaskOutputsInternal)
        this.stubTask = Stub(TaskInternal) {
            _ * getName() >> { "testTask" }
            _ * getInputs() >> stubInputs
            _ * getOutputs() >> stubOutputs
        }
        this.stubHistory = Stub(TaskHistoryRepository.History)
        this.stubOutputFileSnapshotter = Stub(OutputFilesCollectionSnapshotter)
        this.stubInputFileSnapshotter = Stub(FileCollectionSnapshotter)
        this.stubDiscoveredInputFileSnapshotter = Stub(FileCollectionSnapshotter)
    }

    def "constructor invokes snapshots" () {
        setup:
        FileCollectionSnapshot stubSnapshot = Stub(FileCollectionSnapshot) {
            _ * getSnapshot() >> Stub(FilesSnapshotSet)
        }
        OutputFilesCollectionSnapshotter mockOutputFileSnapshotter = Mock(OutputFilesCollectionSnapshotter)
        FileCollectionSnapshotter mockInputFileSnapshotter = Mock(FileCollectionSnapshotter)
        FileCollectionSnapshotter mockDiscoveredInputFileSnapshotter = Mock(FileCollectionSnapshotter)
        FileCollectionSnapshot.PreCheck inputPreCheck = Mock(FileCollectionSnapshot.PreCheck)
        FileCollectionSnapshot.PreCheck outputPreCheck = Mock(FileCollectionSnapshot.PreCheck)

        when:
        new TaskUpToDateState(stubTask, stubHistory, mockOutputFileSnapshotter, mockInputFileSnapshotter, mockDiscoveredInputFileSnapshotter, fileCollectionFactory)

        then:
        noExceptionThrown()
        1 * mockOutputFileSnapshotter.preCheck(_, _) >> outputPreCheck
        1 * mockInputFileSnapshotter.preCheck(_, _) >> inputPreCheck
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2967")
    def "constructor adds context when input snapshot throws UncheckedIOException" () {
        setup:
        def cause = new UncheckedIOException("thrown from stub")
        _ * stubInputFileSnapshotter.preCheck(_, _) >> { throw cause }

        when:
        new TaskUpToDateState(stubTask, stubHistory, stubOutputFileSnapshotter, stubInputFileSnapshotter, stubDiscoveredInputFileSnapshotter, fileCollectionFactory)

        then:
        def e = thrown(UncheckedIOException)
        e.message.contains(stubTask.getName())
        e.message.contains("up-to-date")
        e.message.contains("input")
        e.cause == cause
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2967")
    def "constructor adds context when output snapshot throws UncheckedIOException" () {
        setup:
        def cause = new UncheckedIOException("thrown from stub")
         _ * stubOutputFileSnapshotter.preCheck(_, _) >> { throw cause }

        when:
        new TaskUpToDateState(stubTask, stubHistory, stubOutputFileSnapshotter, stubInputFileSnapshotter, stubDiscoveredInputFileSnapshotter, fileCollectionFactory)

        then:
        def e = thrown(UncheckedIOException)
        e.message.contains(stubTask.getName())
        e.message.contains("up-to-date")
        e.message.contains("output")
        e.cause == cause
    }
}
