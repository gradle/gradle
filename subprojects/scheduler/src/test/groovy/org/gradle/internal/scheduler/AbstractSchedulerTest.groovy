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

package org.gradle.internal.scheduler

import org.gradle.api.Task
import org.gradle.api.specs.Spec

import static org.gradle.internal.scheduler.EdgeType.AVOID_STARTING_BEFORE
import static org.gradle.internal.scheduler.EdgeType.DEPENDENT
import static org.gradle.internal.scheduler.EdgeType.FINALIZER
import static org.gradle.internal.scheduler.EdgeType.MUST_RUN_AFTER

abstract class AbstractSchedulerTest extends AbstractSchedulingTest {
    def graph = new Graph()
    def nodesToExecute = []
    def executedNodes = []
    def executionTracker = new NodeExecutionTracker() {
        @Override
        synchronized void nodeExecuted(Node node) {
            executedNodes.add(node)
        }
    }

    def cycleReporter = Mock(CycleReporter)
    abstract Scheduler getScheduler()

    protected void executeGraph(List<Node> entryNodes) {
        def scheduler = getScheduler()
        try {
            scheduler.execute(graph, entryNodes)
        } finally {
            scheduler.close()
        }
    }

    @Override
    protected void addToGraph(List tasks) {
        nodesToExecute.addAll(tasks)
    }

    @Override
    protected void determineExecutionPlan() {
    }

    @Override
    protected void executes(Object... nodes) {
        executeGraph(nodesToExecute.empty ? graph.allNodes : nodesToExecute)
        assert executedNodes == (nodes as List)
    }

    @Override
    protected List getExecutedTasks() {
        return executedNodes
    }

    @Override
    protected createTask(String name) {
        task(name)
    }

    @Override
    protected TaskNode task(Map options, String name) {
        String project = options.project ?: "root"
        boolean failure = options.failure ?: false
        def task = new TaskNode(project, name, executionTracker, failure)
        graph.addNode(task)
        relationships(options, task)
        return task
    }

    @Override
    protected void relationships(Map options, def task) {
        options.dependsOn?.each { TaskNode dependency ->
            graph.addEdge(new Edge(dependency, task as TaskNode, DEPENDENT))
        }
        options.mustRunAfter?.each { TaskNode predecessor ->
            graph.addEdge(new Edge(predecessor, task as TaskNode, MUST_RUN_AFTER))
        }
        options.shouldRunAfter?.each { TaskNode predecessor ->
            graph.addEdge(new Edge(predecessor, task as TaskNode, AVOID_STARTING_BEFORE))
        }
        options.finalizedBy?.each { TaskNode finalizer ->
            graph.addEdge(new Edge(task as TaskNode, finalizer, FINALIZER))
        }
    }

    @Override
    protected void filtered(Object... expectedTasks) {

    }

    @Override
    protected filteredTask(String name) {
        return null
    }

    @Override
    protected void useFilter(Spec<? super Task> filter) {

    }

    @Override
    protected void awaitCompletion() {

    }

    @Override
    protected void ignoreFailureFor(Object node) {

    }
}
