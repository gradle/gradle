/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.execution

import org.gradle.api.Action
import org.gradle.api.internal.project.ProjectState
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.MultipleBuildOperationFailures
import org.gradle.internal.operations.RunnableBuildOperation
import org.gradle.internal.work.WorkerLimits
import spock.lang.Specification

class TaskPathProjectEvaluatorTest extends Specification {

    def buildOperationExecutor = Stub(BuildOperationExecutor) {
        runAllWithAccessToProjectState(_) >> { Action<BuildOperationQueue<RunnableBuildOperation>> action ->
            def failures = []
            def queue = Stub(BuildOperationQueue) {
                add(_) >> { RunnableBuildOperation operation ->
                    try {
                        operation.run(null)
                    } catch (Throwable t) {
                        failures.add(t)
                    }
                }
            }
            action.execute(queue)
            if (!failures.empty) {
                throw new MultipleBuildOperationFailures(failures, null)
            }
        }
    }
    def workerLimits = Stub(WorkerLimits) {
        getMaxWorkerCount() >> 2
    }
    def evaluator = new TaskPathProjectEvaluator(
        Stub(BuildCancellationToken),
        buildOperationExecutor,
        workerLimits,
        new DefaultInternalOptions(["org.gradle.internal.isolated-projects.scheduler": "jit"])
    )

    def "just-in-time traversal does not configure descendants of a project that failed to configure"() {
        given:
        def failure = new RuntimeException("root failed")
        def grandchild = Mock(ProjectState)
        def child = Mock(ProjectState) {
            getUnorderedChildProjects() >> [grandchild]
        }
        def root = Mock(ProjectState) {
            isRootProject() >> true
            hasChildren() >> true
            getUnorderedChildProjects() >> [child]
        }

        when:
        evaluator.configureHierarchyInParallel(root)

        then:
        1 * root.ensureSelfConfigured() >> { throw failure }
        0 * child.ensureSelfConfigured()
        0 * grandchild.ensureSelfConfigured()

        and:
        def e = thrown(RuntimeException)
        e.is(failure)
    }
}
