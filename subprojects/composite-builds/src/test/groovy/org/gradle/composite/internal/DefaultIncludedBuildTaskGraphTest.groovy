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
import org.gradle.internal.build.BuildState
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.BuildWorkGraph
import org.gradle.internal.build.BuildWorkGraphController
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.build.IncludedBuildState
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import org.gradle.test.fixtures.work.TestWorkerLeaseService

class DefaultIncludedBuildTaskGraphTest extends ConcurrentSpec {
    def buildStateRegistry = Mock(BuildStateRegistry)
    def workerLeaseService = new TestWorkerLeaseService()
    def graph = new DefaultIncludedBuildTaskGraph(executorFactory, new TestBuildOperationExecutor(), buildStateRegistry, workerLeaseService)

    def "does nothing when nothing scheduled"() {
        when:
        graph.withNewWorkGraph { g ->
            g.scheduleWork { b ->
            }
            g.runWork().rethrow()
        }

        then:
        0 * _
    }

    def "finalizes graph for a build when something scheduled"() {
        given:
        def id = Stub(BuildIdentifier)
        def workGraphController = Mock(BuildWorkGraphController)
        def workGraph = Mock(BuildWorkGraph)
        def build = build(id, workGraphController)

        when:
        graph.withNewWorkGraph { g ->
            g.scheduleWork { b ->
                b.withWorkGraph(build) {}
            }
            g.runWork().rethrow()
        }

        then:
        1 * workGraphController.newWorkGraph() >> workGraph
        1 * workGraph.populateWorkGraph(_)
        1 * workGraph.finalizeGraph()
        1 * workGraph.runWork() >> ExecutionResult.succeeded()
    }

    def "cannot schedule tasks when graph has not been created"() {
        when:
        graph.locateTask(taskIdentifier(DefaultBuildIdentifier.ROOT, ":task")).queueForExecution()

        then:
        def e = thrown(IllegalStateException)
        e.message == "No work graph available for this thread."
    }

    def "cannot schedule tasks when after graph has finished execution"() {
        when:
        graph.withNewWorkGraph { 12 }
        graph.locateTask(taskIdentifier(DefaultBuildIdentifier.ROOT, ":task")).queueForExecution()

        then:
        def e = thrown(IllegalStateException)
        e.message == "No work graph available for this thread."
    }

    def "cannot schedule tasks when graph is not yet being prepared for execution"() {
        given:
        def id = Stub(BuildIdentifier)
        def build = build(id)

        when:
        graph.withNewWorkGraph { g ->
            graph.locateTask(taskIdentifier(id, ":task")).queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: NotPrepared"
    }

    def "cannot schedule tasks when graph has been prepared for execution"() {
        given:
        def id = Stub(BuildIdentifier)
        def build = build(id)

        when:
        graph.withNewWorkGraph { g ->
            g.scheduleWork {
            }
            graph.locateTask(taskIdentifier(id, ":task")).queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: ReadyToRun"
    }

    def "cannot schedule tasks when graph has started task execution"() {
        given:
        def id = Stub(BuildIdentifier)
        def workGraphController = Mock(BuildWorkGraphController)
        def workGraph = Mock(BuildWorkGraph)
        def build = build(id, workGraphController)

        workGraphController.newWorkGraph() >> workGraph
        workGraph.runWork() >> {
            graph.locateTask(taskIdentifier(DefaultBuildIdentifier.ROOT, ":task")).queueForExecution()
        }

        when:
        graph.withNewWorkGraph { g ->
            g.scheduleWork { b ->
                b.withWorkGraph(build) {}
            }
            g.runWork().rethrow()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "No work graph available for this thread."
    }

    def "cannot schedule tasks when graph has completed task execution"() {
        given:
        def id = Stub(BuildIdentifier)
        def build = build(id)

        when:
        graph.withNewWorkGraph { g ->
            g.scheduleWork {
            }
            g.runWork()
            graph.locateTask(taskIdentifier(id, ":task")).queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: Finished"
    }

    BuildState build(BuildIdentifier id, BuildWorkGraphController workGraph = null) {
        def build = Mock(IncludedBuildState)
        _ * build.buildIdentifier >> id
        _ * build.workGraph >> (workGraph ?: Stub(BuildWorkGraphController))
        _ * buildStateRegistry.getBuild(id) >> build
        return build
    }

    static TaskIdentifier taskIdentifier(BuildIdentifier id, String taskPath) {
        return TaskIdentifier.of(id, taskPath)
    }
}
