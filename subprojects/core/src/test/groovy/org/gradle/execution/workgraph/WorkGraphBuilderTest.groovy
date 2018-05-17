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
import org.gradle.api.Task
import org.gradle.api.internal.TaskInternal
import org.gradle.internal.scheduler.CycleReporter
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import spock.lang.Unroll

import static org.gradle.internal.scheduler.EdgeType.DEPENDENCY_OF
import static org.gradle.internal.scheduler.EdgeType.SHOULD_COMPLETE_BEFORE

class WorkGraphBuilderTest extends AbstractProjectBuilderSpec {
    def builder = new WorkGraphBuilder()
    def cycleReporter = Mock(CycleReporter)

    def "can create empty graph"() {
        when:
        def workGraph = builder.build(cycleReporter)

        then:
        rootTasks(workGraph) == []
        tasks(workGraph) == []
        edges(workGraph) == []
        requestedTasks(workGraph) == []
        filteredTasks(workGraph) == []
    }

    def "can create graph with single task"() {
        given:
        def a = task("a")
        builder.addTasks([a])

        when:
        def workGraph = builder.build(cycleReporter)

        then:
        rootTasks(workGraph) == [a]
        tasks(workGraph) == [a]
        edges(workGraph) == []
        requestedTasks(workGraph) == [a]
        filteredTasks(workGraph) == []
    }

    def "can filter requested task"() {
        given:
        def a = task("a")
        builder.addTasks([a])
        builder.filter = { task -> task != a }

        when:
        def workGraph = builder.build(cycleReporter)

        then:
        rootTasks(workGraph) == []
        tasks(workGraph) == []
        edges(workGraph) == []
        requestedTasks(workGraph) == []
        filteredTasks(workGraph) == [a]
    }

    @Unroll
    def "can filter #relationship"() {
        given:
        def a = task("a")
        def b = task("b", (relationship): [a])
        builder.addTasks([b])
        builder.filter = { task -> task != a }

        when:
        def workGraph = builder.build(cycleReporter)

        then:
        rootTasks(workGraph) == [b]
        tasks(workGraph) == [b]
        edges(workGraph) == []
        requestedTasks(workGraph) == [b]
        filteredTasks(workGraph) == [a]

        where:
        relationship << ["dependsOn", "finalizedBy"]
    }

    def "can filter dependency transitively"() {
        given:
        def a = task("a")
        def b = task("b", dependsOn: [a])
        def c = task("c", dependsOn: [b])
        def d = task("d", dependsOn: [c])
        builder.addTasks([d])
        builder.filter = { task -> task != b }

        when:
        def workGraph = builder.build(cycleReporter)

        then:
        rootTasks(workGraph) == [c]
        tasks(workGraph) == [c, d]
        edges(workGraph) == [[c, DEPENDENCY_OF, d]]
        requestedTasks(workGraph) == [d]
        filteredTasks(workGraph) == [b]
    }

    def "can filter finalizer"() {
        given:
        def a = task("a")
        def b = task("b", finalizedBy: [a])
        builder.addTasks([b])
        builder.filter = { task -> task != a }

        when:
        def workGraph = builder.build(cycleReporter)

        then:
        rootTasks(workGraph) == [b]
        tasks(workGraph) == [b]
        edges(workGraph) == []
        requestedTasks(workGraph) == [b]
        filteredTasks(workGraph) == [a]
    }

    def "cyclic should run after ordering is ignored in complex task graph"() {
        given:
        def e = task("e")
        def x = task("x", dependsOn: [e])
        def a = task("a", shouldRunAfter: [x])
        def b = task("b", shouldRunAfter: [a])
        def c = task("c", shouldRunAfter: [b])
        def f = task("f", dependsOn: [x], shouldRunAfter: [c])
        def d = task("d", dependsOn: [f], shouldRunAfter: [c])
        relationships(e, shouldRunAfter: [d])
        def build = task("build", dependsOn: [x, a, b, c, d, e])
        builder.addTasks([build])

        when:
        def workGraph = builder.build(cycleReporter)

        then:
        rootTasks(workGraph) == [b]
        tasks(workGraph) == [a, b, build, c, d, e, f, x]
        edges(workGraph) == [
            [a, DEPENDENCY_OF, build],
            [b, DEPENDENCY_OF, build],
            [c, DEPENDENCY_OF, build],
            [d, DEPENDENCY_OF, build],
            [e, DEPENDENCY_OF, build],
            [e, DEPENDENCY_OF, x],
            [f, DEPENDENCY_OF, d],
            [x, DEPENDENCY_OF, build],
            [x, DEPENDENCY_OF, f],
            [b, SHOULD_COMPLETE_BEFORE, c],
            [c, SHOULD_COMPLETE_BEFORE, d],
            [c, SHOULD_COMPLETE_BEFORE, f],
            [d, SHOULD_COMPLETE_BEFORE, e],
            [x, SHOULD_COMPLETE_BEFORE, a],
        ]
        requestedTasks(workGraph) == [build]
        filteredTasks(workGraph) == []
    }

    TaskInternal task(String name) {
        project.tasks.create(name) as TaskInternal
    }

    TaskInternal task(Map options, String name) {
        def task = task(name)
        relationships(options, task)
        return task
    }

    private static void relationships(Map options, task) {
        options.dependsOn?.each { task.dependsOn(it) }
        options.mustRunAfter?.each { task.mustRunAfter(it) }
        options.shouldRunAfter?.each { task.shouldRunAfter(it) }
        options.finalizedBy?.each { task.finalizedBy(it) }
    }

    List<Task> tasks(WorkGraph workGraph) {
        workGraph.graph.allNodes*.task.sort()
    }

    List<Task> rootTasks(WorkGraph workGraph) {
        workGraph.graph.rootNodes*.task
    }

    List<Task> requestedTasks(WorkGraph workGraph) {
        workGraph.requestedNodes*.task.sort()
    }

    List<Task> filteredTasks(WorkGraph workGraph) {
        workGraph.filteredTasks.sort()
    }

    def edges(WorkGraph workGraph) {
        Lists.newArrayList(workGraph.graph.allEdges)
            .sort { a, b ->
                def result = a.type <=> b.type
                if (result == 0) {
                    result = a.source.task <=> b.source.task
                    if (result == 0) {
                        result = a.target.task <=> b.target.task
                    }
                }
                result
            }
            .collect { edge ->
                [edge.source.task, edge.type, edge.target.task]
            }
    }
}
