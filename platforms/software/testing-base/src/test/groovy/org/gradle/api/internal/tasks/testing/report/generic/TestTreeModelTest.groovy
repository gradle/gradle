/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.internal.tasks.testing.TestCompleteEvent
import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
import org.gradle.api.internal.tasks.testing.TestStartEvent
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for {@link TestTreeModel}, specifically the merging behavior in finalizePath().
 *
 * <p>When a test group (e.g. a test class) is emitted multiple times within the same root,
 * some executions may have no children (e.g. when all methods are skipped). These childless
 * executions appear as leaves and must be merged into any existing non-leaf entry for the same
 * node, rather than being added as separate entries. True leaf retries (where all entries are
 * leaves) should still be preserved as separate entries.</p>
 */
class TestTreeModelTest extends Specification {

    @TempDir
    Path tempDir

    private int nextDescriptorId = 1

    def "merges leaf entry into existing non-leaf within same store"() {
        given:
        def store = writeStore { writer ->
            def root = descriptor("root", null)
            def suite1 = descriptor("MySuite", root, "com.example.MySuite")
            def testA = descriptor("testA", suite1, "com.example.MySuite")

            writer.started(root, new TestStartEvent(100))
            writer.started(suite1, new TestStartEvent(100))
            writer.started(testA, new TestStartEvent(100))
            writer.completed(testA, successResult(100, 200), new TestCompleteEvent(200))
            writer.completed(suite1, successResult(100, 200), new TestCompleteEvent(200))

            def suite2 = descriptor("MySuite", root, "com.example.MySuite")
            writer.started(suite2, new TestStartEvent(300))
            writer.completed(suite2, successResult(300, 400), new TestCompleteEvent(400))

            writer.completed(root, successResult(100, 400), new TestCompleteEvent(400))
        }

        when:
        def model = TestTreeModel.loadModelFromStores([store])

        then:
        model.children.size() == 1
        def suiteNode = model.children[0]
        suiteNode.path.name == "MySuite"
        suiteNode.perRootInfo[0].size() == 1
        !suiteNode.perRootInfo[0][0].isLeaf()
        suiteNode.children.size() == 1
        suiteNode.children[0].path.name == "testA"
    }

    def "report generation does not crash when childless execution follows execution with children"() {
        given:
        def storeDir = writeStore { writer ->
            def root = descriptor("root", null)
            def suite1 = descriptor("MySuite", root, "com.example.MySuite")
            def testA = descriptor("testA", suite1, "com.example.MySuite")

            writer.started(root, new TestStartEvent(100))
            writer.started(suite1, new TestStartEvent(100))
            writer.started(testA, new TestStartEvent(100))
            writer.completed(testA, successResult(100, 200), new TestCompleteEvent(200))
            writer.completed(suite1, successResult(100, 200), new TestCompleteEvent(200))

            def suite2 = descriptor("MySuite", root, "com.example.MySuite")
            writer.started(suite2, new TestStartEvent(300))
            writer.completed(suite2, successResult(300, 400), new TestCompleteEvent(400))

            writer.completed(root, successResult(100, 400), new TestCompleteEvent(400))
        }

        when:
        TestTreeModelResultsProvider.useResultsFrom(tempDir.resolve("store")) { provider ->
            provider.visitClasses { }
        }

        then:
        noExceptionThrown()
    }

    def "true leaf retries are preserved as separate entries"() {
        given:
        def store = writeStore { writer ->
            def root = descriptor("root", null)

            writer.started(root, new TestStartEvent(100))

            def testA1 = descriptor("testA", root, "com.example.MySuite")
            writer.started(testA1, new TestStartEvent(100))
            writer.completed(testA1, successResult(100, 200), new TestCompleteEvent(200))

            def testA2 = descriptor("testA", root, "com.example.MySuite")
            writer.started(testA2, new TestStartEvent(300))
            writer.completed(testA2, successResult(300, 400), new TestCompleteEvent(400))

            writer.completed(root, successResult(100, 400), new TestCompleteEvent(400))
        }

        when:
        def model = TestTreeModel.loadModelFromStores([store])

        then:
        model.children.size() == 1
        def testNode = model.children[0]
        testNode.path.name == "testA"
        testNode.perRootInfo[0].size() == 2
        testNode.perRootInfo[0][0].isLeaf()
        testNode.perRootInfo[0][1].isLeaf()
    }

    private SerializableTestResultStore writeStore(
        String name = "store",
        @ClosureParams(value = SimpleType, options = "org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore.Writer")
            Closure<?> writeAction
    ) {
        def storeDir = tempDir.resolve(name)
        def store = new SerializableTestResultStore(storeDir)
        def writer = store.openWriter(0)
        try {
            writeAction(writer)
        } finally {
            writer.close()
        }
        return store
    }

    private TestDescriptorInternal descriptor(String name, TestDescriptorInternal parent, String className = null) {
        def uniqueId = nextDescriptorId++
        return Mock(TestDescriptorInternal) {
            getId() >> uniqueId
            getName() >> name
            getDisplayName() >> name
            getClassName() >> className
            getParent() >> parent
        }
    }

    private TestResult successResult(long startTime, long endTime) {
        return Mock(TestResult) {
            getStartTime() >> startTime
            getEndTime() >> endTime
            getResultType() >> TestResult.ResultType.SUCCESS
            getAssumptionFailure() >> null
            getFailures() >> []
        }
    }
}
