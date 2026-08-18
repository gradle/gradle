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

import com.google.common.collect.ImmutableList
import org.gradle.execution.plan.PlanExecutor
import org.gradle.internal.build.BuildWorkGraph
import org.gradle.internal.build.BuildWorkGraphController
import org.gradle.internal.build.ExecutionResult
import org.gradle.internal.buildtree.BuildTreeWorkGraphPreparer
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import org.gradle.util.Path

class DefaultIncludedBuildTaskGraphTest extends AbstractIncludedBuildTaskGraphTest {

    def workerLeaseService = new TestWorkerLeaseService()
    def preparer = Mock(BuildTreeWorkGraphPreparer)
    def graph = new DefaultIncludedBuildTaskGraph(executorFactory, new TestBuildOperationRunner(), workerLeaseService, Stub(PlanExecutor), preparer)

    def "does no work when nothing scheduled"() {
        when:
        graph.withNewWorkGraph { g ->
            def f = g.scheduleWork { b ->
            }
            f.runWork().rethrow()
        }

        then:
        1 * preparer.prepareToScheduleTasks(_)
        0 * _
    }

    def "finalizes graph for a build when something scheduled"() {
        given:
        def buildPath = Path.ROOT
        def workGraphController = Mock(BuildWorkGraphController)
        def workGraph = Mock(BuildWorkGraph)
        def build = build(buildPath, workGraphController)

        when:
        graph.withNewWorkGraph { g ->
            def f = g.scheduleWork { b ->
                b.withWorkGraph(build) {}
            }
            f.runWork().rethrow()
        }

        then:
        1 * workGraphController.newWorkGraph() >> workGraph
        1 * preparer.prepareToScheduleTasks(_)
        1 * workGraph.populateWorkGraph(_)
        _ * workGraph.takeCrossBuildReferences() >> ImmutableList.of()
        1 * workGraph.finalizeGraph()
        1 * workGraph.runWork() >> ExecutionResult.succeeded()
    }

    def "cannot schedule work more than once for a graph"() {
        when:
        graph.withNewWorkGraph { g ->
            g.scheduleWork {
            }
            g.scheduleWork {
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: ReadyToRun, expected: NotPrepared"
    }

    def "cannot run the work of a graph more than once"() {
        when:
        graph.withNewWorkGraph { g ->
            def f = g.scheduleWork {
            }
            f.runWork().rethrow()
            f.runWork()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: Finished, expected: ReadyToRun"
    }

}
