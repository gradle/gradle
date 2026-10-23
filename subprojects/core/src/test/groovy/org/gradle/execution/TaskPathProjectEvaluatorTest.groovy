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

import org.gradle.api.internal.project.ProjectState
import org.gradle.initialization.BuildCancellationToken
import org.gradle.internal.buildoption.DefaultInternalOptions
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.internal.work.WorkerLimits
import spock.lang.Specification

class TaskPathProjectEvaluatorTest extends Specification {

    def buildOperationExecutor = new TestBuildOperationExecutor()
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
