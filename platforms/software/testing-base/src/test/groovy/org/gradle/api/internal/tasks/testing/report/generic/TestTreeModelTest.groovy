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
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore
import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestResult
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

import static org.gradle.api.internal.tasks.testing.report.generic.TestTreeModelTest.Run.CHILDLESS
import static org.gradle.api.internal.tasks.testing.report.generic.TestTreeModelTest.Run.FAILED_CHILDLESS
import static org.gradle.api.internal.tasks.testing.report.generic.TestTreeModelTest.Run.SKIPPED_CHILDLESS
import static org.gradle.api.internal.tasks.testing.report.generic.TestTreeModelTest.Run.WITH_CHILD
import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE
import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED
import static org.gradle.api.tasks.testing.TestResult.ResultType.SUCCESS

class TestTreeModelTest extends Specification {

    private static final String CLASS_NAME = "com.example.MySuite"

    @TempDir
    Path tempDir

    private int nextDescriptorId = 1

    def "merges runs #runs into a single entry ordered by start time"() {
        given:
        def store = writeRuns(runs)

        when:
        def model = loadModel(store)
        def classes = loadClasses(store)

        then:
        model.children*.path*.name == ["MySuite"]
        def suiteNode = model.children[0]
        suiteNode.perRootInfo[0].size() == 1
        def entry = suiteNode.perRootInfo[0][0]
        !entry.isLeaf()
        entry.results*.startTime == (1..runs.size()).collect { 100L * it }
        suiteNode.children*.path*.name == childNames

        and:
        classes*.className == ["MySuite"]
        classes[0].results*.name == childNames

        where:
        runs                                           | childNames
        [WITH_CHILD, CHILDLESS]                        | ["test0"]
        [CHILDLESS, WITH_CHILD]                        | ["test1"]
        [CHILDLESS, CHILDLESS, WITH_CHILD]             | ["test2"]
        [CHILDLESS, CHILDLESS, CHILDLESS, WITH_CHILD]  | ["test3"]
        [CHILDLESS, WITH_CHILD, CHILDLESS]             | ["test1"]
        [WITH_CHILD, CHILDLESS, WITH_CHILD]            | ["test0", "test2"]
        [CHILDLESS, WITH_CHILD, CHILDLESS, WITH_CHILD] | ["test1", "test3"]
        [WITH_CHILD, WITH_CHILD]                       | ["test0", "test1"]
        [SKIPPED_CHILDLESS, WITH_CHILD]                | ["test1"]
    }

    def "does not count successful or skipped childless runs merged from #runs"() {
        given:
        def store = writeRuns(runs)

        when:
        def model = loadModel(store)

        then:
        def suiteAndRoot = [model.children[0].perRootInfo[0][0], model.perRootInfo[0][0]]
        suiteAndRoot*.totalLeafCount == [total, total]
        suiteAndRoot*.failedLeafCount == [0, 0]
        suiteAndRoot*.skippedLeafCount == [0, 0]

        where:
        runs                                           | total
        [WITH_CHILD, CHILDLESS]                        | 1
        [CHILDLESS, WITH_CHILD, CHILDLESS, WITH_CHILD] | 2
        [SKIPPED_CHILDLESS, WITH_CHILD]                | 1
    }

    def "counts failed childless runs merged from #runs via a synthetic child"() {
        given:
        def store = writeRuns(runs)

        when:
        def model = loadModel(store)

        then: "the synthetic child exists with an entry per failed run"
        def suiteNode = model.children[0]
        def executionError = suiteNode.children.find { it.path.name == "executionError" }
        executionError != null
        executionError.perRootInfo[0].size() == failed

        and: "each entry is counted in the total and failed leaf counts"
        def suiteAndRoot = [suiteNode.perRootInfo[0][0], model.perRootInfo[0][0]]
        suiteAndRoot*.totalLeafCount == [total, total]
        suiteAndRoot*.failedLeafCount == [failed, failed]
        suiteAndRoot*.skippedLeafCount == [0, 0]

        where:
        runs                                                        | total | failed
        [FAILED_CHILDLESS, WITH_CHILD]                              | 2     | 1
        [FAILED_CHILDLESS, CHILDLESS, FAILED_CHILDLESS, WITH_CHILD] | 3     | 2
    }

    def "reports failed childless runs merged from #runs as executionError"() {
        given:
        def store = writeRuns(runs)

        when:
        def model = loadModel(store)
        def classes = loadClasses(store)

        then: "a single executionError child is added after the reported children"
        def suiteNode = model.children[0]
        suiteNode.children*.path*.name == childNames

        and: "each of its entries is a leaf carrying the failures of a failed run"
        def executionErrors = suiteNode.children.find { it.path.name == "executionError" }.perRootInfo[0]
        executionErrors.every { it.isLeaf() }
        executionErrors.collect { it.results*.failures*.message.flatten() } ==
            [["java.lang.RuntimeException: worker lost"]] * runs.count(FAILED_CHILDLESS)

        and: "every entry in the model has a unique id"
        def ids = []
        model.walkDepthFirst { node -> node.perRootInfo[0].each { ids << it.id } }
        ids.size() == ids.toUnique().size()

        and: "the XML results have an executionError per failed run, ordered by start time, with its failures"
        classes[0].results*.name == methodNames
        classes[0].results.findAll { it.name == "executionError" }.collect { it.failures*.message } ==
            [["java.lang.RuntimeException: worker lost"]] * runs.count(FAILED_CHILDLESS)

        where:
        // The model has one node per child name, and adds executionError after the reported children, as it's created
        // when merging. The XML results have one method per entry instead, ordered by start time, so each
        // executionError takes the position of the run it came from.
        runs                                                        | childNames                  | methodNames
        [FAILED_CHILDLESS, WITH_CHILD]                              | ["test1", "executionError"] | ["executionError", "test1"]
        [WITH_CHILD, FAILED_CHILDLESS]                              | ["test0", "executionError"] | ["test0", "executionError"]
        [FAILED_CHILDLESS, CHILDLESS, FAILED_CHILDLESS, WITH_CHILD] | ["test3", "executionError"] | ["executionError", "executionError", "test3"]
    }

    def "orders merged runs by start time rather than by arrival order"() {
        given:
        def store = writeRunsArrivingInReverse(200)

        when:
        def model = loadModel(store)

        then:
        def entries = model.children[0].perRootInfo[0]
        entries.size() == 1
        entries[0].results*.startTime == [100L, 200L]
        entries[0].results*.endTime == [500L, 300L]
    }

    def "orders merged runs that start at the same time by id"() {
        given:
        def store = writeRunsArrivingInReverse(100)

        when:
        def model = loadModel(store)

        then:
        def entries = model.children[0].perRootInfo[0]
        entries.size() == 1
        entries[0].results*.endTime == [500L, 300L]
        entries[0].outputEntries*.id == entries[0].outputEntries*.id.sort(false)
    }

    def "merges runs separately for each root"() {
        given:
        def first = writeStore("first") { writer ->
            def root = descriptor("root", null)
            writer.started(root, new TestStartEvent(0))
            childlessRun(writer, root, 100, SUCCESS)
            runWithChildren(writer, root, 200, "testA")
            writer.completed(root, result(0, 300), new TestCompleteEvent(300))
        }
        def second = writeStore("second") { writer ->
            def root = descriptor("root", null)
            writer.started(root, new TestStartEvent(0))
            runWithChildren(writer, root, 100, "testA")
            writer.completed(root, result(0, 200), new TestCompleteEvent(200))
        }

        when:
        def model = loadModel(first, second)

        then:
        def suiteNode = model.children[0]
        suiteNode.perRootInfo*.size() == [1, 1]
        suiteNode.perRootInfo[0][0].results*.startTime == [100L, 200L]
        suiteNode.perRootInfo[1][0].results*.startTime == [100L]
    }

    def "true leaf retries are preserved as separate entries"() {
        given:
        def store = writeStore { writer ->
            def root = descriptor("root", null)
            writer.started(root, new TestStartEvent(100))

            def testA1 = descriptor("testA", root, CLASS_NAME)
            writer.started(testA1, new TestStartEvent(100))
            writer.completed(testA1, result(100, 200, FAILURE), new TestCompleteEvent(200))

            def testA2 = descriptor("testA", root, CLASS_NAME)
            writer.started(testA2, new TestStartEvent(300))
            writer.completed(testA2, result(300, 400), new TestCompleteEvent(400))

            writer.completed(root, result(100, 400), new TestCompleteEvent(400))
        }

        when:
        def model = loadModel(store)

        then:
        model.children*.path*.name == ["testA"]
        def entries = model.children[0].perRootInfo[0]
        entries.size() == 2
        entries.every { it.isLeaf() }
        entries*.results*.startTime == [[100L], [300L]]
        entries*.failedLeafCount == [1, 0]
    }

    def "childless runs are preserved as separate entries when no run has children"() {
        given:
        def store = writeStore { writer ->
            def root = descriptor("root", null)
            writer.started(root, new TestStartEvent(0))
            childlessRun(writer, root, 100, SUCCESS)
            childlessRun(writer, root, 200, SUCCESS)
            writer.completed(root, result(0, 300), new TestCompleteEvent(300))
        }

        when:
        def model = loadModel(store)

        then:
        model.children*.path*.name == ["MySuite"]
        def entries = model.children[0].perRootInfo[0]
        entries.size() == 2
        entries.every { it.isLeaf() }
        entries*.results*.startTime == [[100L], [200L]]
        entries*.totalLeafCount == [1, 1]
    }

    private Path writeStore(
        String name = "store",
        @ClosureParams(value = SimpleType, options = "org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore.Writer")
            Closure<?> writeAction
    ) {
        def storeDir = tempDir.resolve(name)
        def writer = new SerializableTestResultStore(storeDir).openWriter(0)
        try {
            writeAction(writer)
        } finally {
            writer.close()
        }
        return storeDir
    }

    /**
     * The kinds of runs of {@code MySuite} that can be written to a store.
     */
    private enum Run {
        CHILDLESS,
        FAILED_CHILDLESS,
        SKIPPED_CHILDLESS,
        WITH_CHILD
    }

    /**
     * Writes the given runs of {@code MySuite} under a single root, each starting 100ms after the previous one.
     * The child of a run with children is named after the run's index, e.g. {@code test1}.
     */
    private Path writeRuns(List<Run> runs) {
        return writeStore { writer ->
            def root = descriptor("root", null)
            writer.started(root, new TestStartEvent(0))
            runs.eachWithIndex { Run run, int i ->
                long start = 100 * (i + 1)
                switch (run) {
                    case CHILDLESS:
                        childlessRun(writer, root, start, SUCCESS)
                        break
                    case FAILED_CHILDLESS:
                        childlessRun(writer, root, start, FAILURE)
                        break
                    case SKIPPED_CHILDLESS:
                        childlessRun(writer, root, start, SKIPPED)
                        break
                    case WITH_CHILD:
                        runWithChildren(writer, root, start, "test$i")
                        break
                }
            }
            def end = 100 * (runs.size() + 1)
            writer.completed(root, result(0, end), new TestCompleteEvent(end))
        }
    }

    /**
     * Writes two overlapping runs of {@code MySuite}, so they arrive in the reverse of the order they were started in.
     * The first is started at 100 and completes at 500 without children.
     * The second is started at {@code secondStart} and completes at 300 with a single child.
     */
    private Path writeRunsArrivingInReverse(long secondStart) {
        return writeStore { writer ->
            def root = descriptor("root", null)
            writer.started(root, new TestStartEvent(0))

            def first = descriptor("MySuite", root, CLASS_NAME)
            writer.started(first, new TestStartEvent(100))

            def second = descriptor("MySuite", root, CLASS_NAME)
            def testA = descriptor("testA", second, CLASS_NAME)
            writer.started(second, new TestStartEvent(secondStart))
            writer.started(testA, new TestStartEvent(secondStart))
            writer.completed(testA, result(secondStart, 300), new TestCompleteEvent(300))
            writer.completed(second, result(secondStart, 300), new TestCompleteEvent(300))

            writer.completed(first, result(100, 500), new TestCompleteEvent(500))
            writer.completed(root, result(0, 500), new TestCompleteEvent(500))
        }
    }

    private static TestTreeModel loadModel(Path... storeDirs) {
        return TestTreeModel.loadModelFromStores(storeDirs.collect { new SerializableTestResultStore(it) })
    }

    private static List<TestClassResult> loadClasses(Path storeDir) {
        List<TestClassResult> classes = []
        TestTreeModelResultsProvider.useResultsFrom(storeDir) { provider ->
            provider.visitClasses { classes << it }
        }
        return classes
    }

    /**
     * Writes a run of {@code MySuite} that reports no children, taking 50ms from {@code start}.
     */
    private void childlessRun(SerializableTestResultStore.Writer writer, TestDescriptorInternal parent, long start, TestResult.ResultType resultType) {
        def suite = descriptor("MySuite", parent, CLASS_NAME)
        writer.started(suite, new TestStartEvent(start))
        writer.completed(suite, result(start, start + 50, resultType), new TestCompleteEvent(start + 50))
    }

    /**
     * Writes a run of {@code MySuite} that reports a single successful child, taking 50ms from {@code start}.
     */
    private void runWithChildren(SerializableTestResultStore.Writer writer, TestDescriptorInternal parent, long start, String childName) {
        def suite = descriptor("MySuite", parent, CLASS_NAME)
        def child = descriptor(childName, suite, CLASS_NAME)
        writer.started(suite, new TestStartEvent(start))
        writer.started(child, new TestStartEvent(start))
        writer.completed(child, result(start, start + 50), new TestCompleteEvent(start + 50))
        writer.completed(suite, result(start, start + 50), new TestCompleteEvent(start + 50))
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

    private TestResult result(long startTime, long endTime, TestResult.ResultType resultType = SUCCESS) {
        def failures = resultType == FAILURE ? [TestFailure.fromTestFrameworkFailure(new RuntimeException("worker lost"))] : []
        return Mock(TestResult) {
            getStartTime() >> startTime
            getEndTime() >> endTime
            getResultType() >> resultType
            getAssumptionFailure() >> null
            getFailures() >> failures
        }
    }
}
