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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskInputsInternal
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.TaskOutputsInternal
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.OutputFilesCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareType
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository
import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.tasks.TaskFilePropertySpec
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec
import org.gradle.api.internal.tasks.TaskPropertySpec
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import spock.lang.Issue
import spock.lang.Specification

class TaskUpToDateStateTest extends Specification {
    def mockInputs = Mock(TaskInputsInternal)
    def mockOutputs = Mock(TaskOutputsInternal)
    private TaskInternal stubTask
    private TaskHistoryRepository.History stubHistory
    private OutputFilesCollectionSnapshotter stubOutputFileSnapshotter
    private FileCollectionSnapshotter stubInputFileSnapshotter
    private FileCollectionSnapshotter stubDiscoveredInputFileSnapshotter
    private FileCollectionFactory fileCollectionFactory = Mock(FileCollectionFactory)
    private classLoaderHierarchyHasher = Mock(ClassLoaderHierarchyHasher)

    def setup() {
        this.stubTask = Stub(TaskInternal) {
            _ * getName() >> { "testTask" }
            _ * getInputs() >> mockInputs
            _ * getOutputs() >> mockOutputs
        }
        this.stubHistory = Stub(TaskHistoryRepository.History)
        this.stubOutputFileSnapshotter = Stub(OutputFilesCollectionSnapshotter)
        this.stubInputFileSnapshotter = Stub(FileCollectionSnapshotter)
        this.stubDiscoveredInputFileSnapshotter = Stub(FileCollectionSnapshotter)
    }

    def "constructor invokes snapshots" () {
        setup:
        FileCollectionSnapshot stubSnapshot = Stub(FileCollectionSnapshot)
        OutputFilesCollectionSnapshotter mockOutputFileSnapshotter = Mock(OutputFilesCollectionSnapshotter)
        FileCollectionSnapshotter mockInputFileSnapshotter = Mock(FileCollectionSnapshotter)
        FileCollectionSnapshotter mockDiscoveredInputFileSnapshotter = Mock(FileCollectionSnapshotter)

        when:
        new TaskUpToDateState(stubTask, stubHistory, mockOutputFileSnapshotter, mockInputFileSnapshotter, mockDiscoveredInputFileSnapshotter, fileCollectionFactory, classLoaderHierarchyHasher)

        then:
        noExceptionThrown()
        1 * mockInputs.getProperties() >> [:]
        1 * mockInputs.getFileProperties() >> fileProperties(prop: "a")
        1 * mockOutputs.getFileProperties() >> fileProperties(out: "b")
        1 * mockOutputFileSnapshotter.snapshot(_, _) >> stubSnapshot
        1 * mockInputFileSnapshotter.snapshot(_, _) >> stubSnapshot
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2967")
    def "constructor adds context when input snapshot throws UncheckedIOException" () {
        setup:
        def cause = new UncheckedIOException("thrown from stub")
        _ * stubInputFileSnapshotter.snapshot(_, _) >> { throw cause }

        when:
        new TaskUpToDateState(stubTask, stubHistory, stubOutputFileSnapshotter, stubInputFileSnapshotter, stubDiscoveredInputFileSnapshotter, fileCollectionFactory, classLoaderHierarchyHasher)

        then:
        1 * mockInputs.getProperties() >> [:]
        1 * mockInputs.getFileProperties() >> fileProperties(prop: "a")
        1 * mockOutputs.getFileProperties() >> fileProperties(out: "b")
        def e = thrown(UncheckedIOException)
        e.message.contains(stubTask.getName())
        e.message.contains("up-to-date")
        e.message.contains("input")
        e.cause == cause
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2967")
    def "constructor adds context when output snapshot throws UncheckedIOException" () {
        setup:
        def mockOutputProperty = Mock(TaskFilePropertySpec) {
            getPropertyName() >> "out"
            getFiles() >> {
                new SimpleFileCollection(new File("b"))
            }
        }
        def cause = new UncheckedIOException("thrown from stub")
        _ * stubOutputFileSnapshotter.snapshot(_, _) >> { throw cause }

        when:
        new TaskUpToDateState(stubTask, stubHistory, stubOutputFileSnapshotter, stubInputFileSnapshotter, stubDiscoveredInputFileSnapshotter, fileCollectionFactory, classLoaderHierarchyHasher)

        then:
        1 * mockInputs.getProperties() >> [:]
        1 * mockOutputs.getFileProperties() >> fileProperties(out: "b")
        def e = thrown(UncheckedIOException)
        e.message.contains(stubTask.getName())
        e.message.contains("up-to-date")
        e.message.contains("output")
        e.cause == cause
    }

    private static def fileProperties(Map<String, String> props) {
        return props.collect { entry ->
            return new PropertySpec(
                propertyName: entry.key,
                propertyFiles: new SimpleFileCollection([new File(entry.value)]),
                compareType: TaskFilePropertyCompareType.UNORDERED
            )
        } as SortedSet
    }

    private static class PropertySpec implements TaskInputFilePropertySpec {
        String propertyName
        FileCollection propertyFiles
        TaskFilePropertyCompareType compareType

        @Override
        int compareTo(TaskPropertySpec o) {
            return propertyName.compareTo(o.propertyName)
        }
    }
}
