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

import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.work.WorkerLeaseService
import spock.lang.Specification

class DefaultIncludedBuildTaskGraphTest extends Specification {
    def graph = new DefaultIncludedBuildTaskGraph(Stub(ExecutorFactory), new TestBuildOperationExecutor(), Stub(BuildStateRegistry), Stub(ProjectStateRegistry), Stub(WorkerLeaseService))

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
        when:
        graph.withNewTaskGraph {
            graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: NotPrepared"
    }

    def "cannot schedule tasks when graph has been prepared for execution"() {
        when:
        graph.withNewTaskGraph {
            graph.prepareTaskGraph {
                graph.populateTaskGraphs()
            }
            graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: ReadyToRun"
    }

    def "cannot schedule tasks when graph has started task execution"() {
        when:
        graph.withNewTaskGraph {
            graph.prepareTaskGraph {
                graph.populateTaskGraphs()
            }
            graph.startTaskExecution()
            graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: Running"
    }

    def "cannot schedule tasks when graph has completed task execution"() {
        when:
        graph.withNewTaskGraph {
            graph.prepareTaskGraph {
                graph.populateTaskGraphs()
            }
            graph.startTaskExecution()
            graph.awaitTaskCompletion()
            graph.locateTask(DefaultBuildIdentifier.ROOT, ":task").queueForExecution()
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Work graph is in an unexpected state: Finished"
    }
}
