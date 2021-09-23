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

package org.gradle.composite.internal

import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.BuildWorkGraph
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultIncludedBuildTaskGraphTest extends ConcurrentSpec {
    def buildStateRegistry = Mock(BuildStateRegistry)
    def graph = new DefaultIncludedBuildTaskGraph(executorFactory, new TestBuildOperationExecutor(), buildStateRegistry, Stub(ProjectStateRegistry), Stub(WorkerLeaseService))

    def "does nothing when nothing scheduled"() {
        when:
        graph.withNewTaskGraph { g ->
            g.prepareTaskGraph { b ->
                g.populateTaskGraphs()
            }
            g.startTaskExecution()
            g.awaitTaskCompletion().rethrow()
        }

        then:
        0 * _
    }

    def "finalizes graph for a build when something scheduled"() {
        given:
        def id = Stub(BuildIdentifier)
        def workGraph = Mock(BuildWorkGraph)
        def build = build(id, workGraph)

        when:
        graph.withNewTaskGraph { g ->
            g.prepareTaskGraph { b ->
                b.withWorkGraph(build) {}
                g.populateTaskGraphs()
            }
            g.startTaskExecution()
            g.awaitTaskCompletion().rethrow()
        }

        then:
        1 * workGraph.populateWorkGraph(_)
        1 * workGraph.prepareForExecution()
    }

    def "cannot schedule tasks when graph has not been created"() {
        when:
        graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: NotCreated"
    }

    def "cannot schedule tasks when after graph has finished execution"() {
        when:
        graph.withNewTaskGraph { 12 }
        graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: NotCreated"
    }

    def "cannot schedule tasks when graph is not yet being prepared for execution"() {
        given:
        def id = Stub(BuildIdentifier)
        def build = build(id)

        when:
        graph.withNewTaskGraph { g ->
            graph.locateTask(id, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: NotPrepared"
    }

    def "cannot schedule tasks when graph has been prepared for execution"() {
        when:
        graph.withNewTaskGraph { g ->
            g.prepareTaskGraph {
                g.populateTaskGraphs()
            }
            graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: ReadyToRun"
    }

    def "cannot schedule tasks when graph has started task execution"() {
        when:
        graph.withNewTaskGraph { g ->
            g.prepareTaskGraph {
                g.populateTaskGraphs()
            }
            g.startTaskExecution()
            graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: Running"
    }

    def "cannot schedule tasks when graph has completed task execution"() {
        when:
        graph.withNewTaskGraph { g ->
            g.prepareTaskGraph {
                g.populateTaskGraphs()
            }
            g.startTaskExecution()
            g.awaitTaskCompletion()
            graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: Finished"
    }

    BuildState build(BuildIdentifier id, BuildWorkGraph workGraph = null) {
        def build = Mock(IncludedBuildState)
        _ * build.buildIdentifier >> id
        _ * build.workGraph >> (workGraph ?: Stub(BuildWorkGraph))
        _ * buildStateRegistry.getBuild(id) >> build
        return build
    }
}
