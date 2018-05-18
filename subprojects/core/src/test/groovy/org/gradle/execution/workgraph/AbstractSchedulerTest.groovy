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

package org.gradle.execution.workgraph

import com.google.common.collect.Lists
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.specs.Spec
import org.gradle.caching.BuildCacheException
import org.gradle.execution.workgraph.AbstractSchedulerTest.TestResourceLock
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.graph.DirectedGraph
import org.gradle.internal.graph.DirectedGraphRenderer
import org.gradle.internal.graph.GraphNodeRenderer
import org.gradle.internal.logging.text.StyledTextOutput
import org.gradle.internal.resources.ResourceLock
import org.gradle.internal.scheduler.AbstractSchedulingTest
import org.gradle.internal.scheduler.ConcurrentNodeExecutionCoordinator
import org.gradle.internal.scheduler.CycleReporter
import org.gradle.internal.scheduler.Edge
import org.gradle.internal.scheduler.Event
import org.gradle.internal.scheduler.Graph
import org.gradle.internal.scheduler.GraphExecutionResult
import org.gradle.internal.scheduler.Node
import org.gradle.internal.scheduler.NodeExecutor
import org.gradle.internal.scheduler.NodeFailedEvent
import org.gradle.internal.scheduler.NodeFinishedEvent
import org.gradle.internal.scheduler.Scheduler
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule

import static org.gradle.internal.scheduler.EdgeType.DEPENDENCY_OF
import static org.gradle.internal.scheduler.EdgeType.FINALIZED_BY
import static org.gradle.internal.scheduler.EdgeType.MUST_COMPLETE_BEFORE
import static org.gradle.util.TestUtil.createRootProject

@CleanupTestDirectory
@UsesNativeServices
abstract class AbstractSchedulerTest extends AbstractSchedulingTest {

    // Naming the field "temporaryFolder" since that is the default field intercepted by the
    // @CleanupTestDirectory annotation.
    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()
    ProjectInternal root

    static final TASK_NODE_COMPARATOR = new Comparator<? super Node>() {
        @Override
        int compare(def a, def b) {
            return a.task <=> b.task
        }
    }

    def cancellationHandler = Mock(BuildCancellationToken)
    def concurrentNodeExecutionCoordinator = new ConcurrentNodeExecutionCoordinator() {
        private final Map<String, ResourceLock> locks = [:].<String, ResourceLock>withDefault {
            return new TestResourceLock()
        }

        @Override
        ResourceLock findLockFor(Node node) {
            if (node instanceof TaskNode) {
                return locks[node.task.project.path]
            } else {
                return null
            }
        }

        @Override
        Node findConflictingNode(Graph graph, Node nodeToRun, Collection<? extends Node> runningNodes) {
            null
        }
    }

    private static class TestResourceLock implements ResourceLock {
        private Thread lockerThread

        @Override
        synchronized boolean isLocked() {
            lockerThread != null
        }

        @Override
        synchronized boolean isLockedByCurrentThread() {
            lockerThread == Thread.currentThread()
        }

        @Override
        synchronized boolean tryLock() {
            if (locked) {
                if (!lockedByCurrentThread) {
                    return false
                }
            } else {
                lockerThread = Thread.currentThread()
            }
            return true
        }

        @Override
        void unlock() {
            lockerThread = null
        }

        @Override
        String getDisplayName() {
            return "test lock"
        }
    }

    WorkGraphBuilder workGraphBuilder = new WorkGraphBuilder()
    WorkGraph workGraph
    List<TaskInternal> allTasks
    boolean continueOnFailure

    Map<TaskInternal, Throwable> failingTasks = [:]
    def nodeExecutor = new NodeExecutor() {
        @Override
        Throwable execute(Node node) {
            println "Executing $node"
            return failingTasks.get(node.task)
        }
    }

    GraphExecutionResult results

    protected static void executeNode(Node node, NodeExecutor nodeExecutor, Queue<Event> eventQueue) {
        def failure = nodeExecutor.execute(node)
        if (failure) {
            eventQueue.add(new NodeFailedEvent(node, failure))
        } else {
            eventQueue.add(new NodeFinishedEvent(node))
        }
    }

    def cycleReporter = new CycleReporter() {
        @Override
        String reportCycle(Graph graph, Collection<Node> cycle) {
            def sortedCycle = Lists.newArrayList(cycle)
            Collections.sort(sortedCycle, TASK_NODE_COMPARATOR)
            DirectedGraphRenderer<Node> graphRenderer = new DirectedGraphRenderer<Node>(new GraphNodeRenderer<Node>() {
                @Override
                void renderTo(Node node, StyledTextOutput output) {
                    output.withStyle(StyledTextOutput.Style.Identifier).text(node)
                }
            }, new DirectedGraph<Node, Object>() {
                @Override
                void getNodeValues(Node node, Collection<? super Object> values, Collection<? super Node> connectedNodes) {
                    for (Node dependency : sortedCycle) {
                        for (Edge incoming : graph.getIncomingEdges(node)) {
                            if ((incoming.getType() == DEPENDENCY_OF || incoming.getType() == MUST_COMPLETE_BEFORE)
                                && incoming.getSource() == dependency) {
                                connectedNodes.add(dependency)
                            }
                        }
                        for (Edge outgoing : graph.getOutgoingEdges(node)) {
                            if (outgoing.getType() == FINALIZED_BY && outgoing.getTarget() == dependency) {
                                connectedNodes.add(dependency)
                            }
                        }
                    }
                }
            })
            StringWriter writer = new StringWriter()
            def firstNode = sortedCycle.get(0)
            graphRenderer.renderTo(firstNode, writer)
            return writer.toString()
        }
    }

    def setup() {
        root = createRootProject(temporaryFolder.testDirectory)
    }

    abstract Scheduler getScheduler()

    def "stops returning tasks when build is cancelled"() {
        2 * cancellationHandler.isCancellationRequested() >>> [false, true]
        def a = task("a")
        def b = task("b")

        when:
        addToGraphAndPopulate([a, b])

        then:
        executedTasks == [a]
        failures*.class == [BuildCacheException]
        failures*.message == ['Build cancelled.']
    }

    protected void executeGraph() {
        results = scheduler.execute(workGraph.graph, workGraph.requestedNodes, continueOnFailure, nodeExecutor)
    }

    @Override
    protected void addToGraph(List tasks) {
        workGraphBuilder.addTasks(tasks)
    }

    @Override
    protected void determineExecutionPlan() {
        workGraph = workGraphBuilder.build(cycleReporter)
        allTasks = workGraph.graph.allNodes*.task
        executeGraph()
    }

    @Override
    protected void executes(Object... expectedTasks) {
        assert results.executedNodes*.task == (expectedTasks as List)
    }

    @Override
    protected Set getAllTasks() {
        return allTasks
    }

    @Override
    protected List getExecutedTasks() {
        return results.executedNodes*.task as List
    }

    @Override
    protected createTask(String name) {
        task(name)
    }

    @Override
    protected TaskInternal task(Map options, String name) {
        ProjectInternal project
        String projectName = options.project
        if (projectName) {
            project = root.findProject(projectName)
            if (project == null) {
                def projectDir = temporaryFolder.testDirectory.createDir(projectName)
                project = TestUtil.createChildProject(root, projectName, projectDir)
            }
        } else {
            project = root
        }
        def task = project.tasks.create(name) as TaskInternal
        relationships(options, task)
        Exception failure = options.failure
        if (failure) {
            failingTasks.put(task, failure)
        }
        return task
    }

    @Override
    protected void relationships(Map options, def task) {
        if (options.dependsOn) { task.dependsOn(options.dependsOn) }
        if (options.mustRunAfter) { task.mustRunAfter(options.mustRunAfter) }
        if (options.shouldRunAfter) { task.shouldRunAfter(options.shouldRunAfter) }
        if (options.finalizedBy) { task.finalizedBy(options.finalizedBy) }
    }

    @Override
    protected void filtered(Object... expectedTasks) {
        assert workGraph.filteredTasks == (expectedTasks as Set)
    }

    @Override
    protected filteredTask(String name) {
        return task(failure: new RuntimeException("fails"), name)
    }

    @Override
    protected void useFilter(Spec filter) {
        workGraphBuilder.filter = filter
    }

    @Override
    protected List<? extends Throwable> getFailures() {
        return results.failures
    }

    @Override
    protected void continueOnFailure() {
        continueOnFailure = true
    }
}
