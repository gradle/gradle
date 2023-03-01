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

package org.gradle.internal.execution.impl

import org.gradle.api.file.FileCollection
import org.gradle.internal.execution.FileCollectionSnapshotter
import org.gradle.internal.execution.OutputSnapshotter
import org.gradle.internal.execution.UnitOfWork
import org.gradle.internal.file.TreeType
import org.gradle.internal.snapshot.FileSystemSnapshot
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultOutputSnapshotterTest extends Specification {
    def work = Mock(UnitOfWork)
    def fileCollectionSnapshotter = Mock(FileCollectionSnapshotter)
    def outputSnapshotter = new DefaultOutputSnapshotter(fileCollectionSnapshotter)

    @Rule
    TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider(getClass())

    def workspace = tempDir.file("workspace")
    def contents = Mock(FileCollection)
    def root = workspace.file("root")

    def "snapshots outputs"() {
        def outputSnapshot = Mock(FileSystemSnapshot)

        when:
        def result = outputSnapshotter.snapshotOutputs(work, workspace)

        then:
        1 * work.visitOutputs(workspace, _ as UnitOfWork.OutputVisitor) >> { File workspace, UnitOfWork.OutputVisitor outputVisitor ->
            outputVisitor.visitOutputProperty("output", TreeType.FILE, UnitOfWork.OutputFileValueSupplier.fromStatic(root, contents))
        }
        1 * fileCollectionSnapshotter.snapshot(contents) >> Stub(FileCollectionSnapshotter.Result) {
            snapshot >> outputSnapshot
        }
        0 * _

        then:
        result as Map == ["output": outputSnapshot]
    }

    def "reports snapshotting problem"() {
        def failure = new UncheckedIOException(new IOException("Error"))

        when:
        outputSnapshotter.snapshotOutputs(work, workspace)

        then:
        1 * work.visitOutputs(workspace, _ as UnitOfWork.OutputVisitor) >> { File workspace, UnitOfWork.OutputVisitor outputVisitor ->
            outputVisitor.visitOutputProperty("output", TreeType.FILE, UnitOfWork.OutputFileValueSupplier.fromStatic(root, contents))
        }
        1 * fileCollectionSnapshotter.snapshot(contents) >> { throw failure }
        0 * _

        then:
        def ex = thrown OutputSnapshotter.OutputFileSnapshottingException
        ex.propertyName == "output"
        ex.cause == failure
    }
}
