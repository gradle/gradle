/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner

import org.gradle.internal.buildtree.BuildModelNestedActionRunner
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.internal.operations.BuildOperationQueue
import org.gradle.internal.operations.MultipleBuildOperationFailures
import spock.lang.Specification

import java.util.function.Supplier

class BuildModelNestedActionRunnerTest extends Specification {

    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def buildModelParameters = Mock(BuildModelParameters)
    def runner = new BuildModelNestedActionRunner(buildOperationExecutor, buildModelParameters, "Test operation")

    def "parallelism is defined by Tooling API parallel actions"() {
        when:
        runner.isParallel()

        then:
        1 * buildModelParameters.isParallelToolingApiActions() >> true
        0 * _
    }

    def "runs supplied actions"() {
        given:
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def action3 = Mock(Supplier)

        when:
        def result = runner.run([action1, action2, action3])

        then:
        result == ["one", "two", "three"]

        1 * buildModelParameters.isParallelToolingApiActions() >> false
        1 * action1.get() >> "one"
        1 * action2.get() >> "two"
        1 * action3.get() >> "three"
        0 * _
    }

    def "queues supplied actions to run in parallel"() {
        given:
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def action3 = Mock(Supplier)
        def queue = Mock(BuildOperationQueue)

        when:
        def result = runner.run([action1, action2, action3])

        then:
        result == ["one", "two", "three"]

        1 * buildModelParameters.isParallelToolingApiActions() >> true
        1 * buildOperationExecutor.runAllWithAccessToProjectState(_) >> { def params ->
            def queueingAction = params[0]
            queueingAction.execute(queue)
        }
        3 * queue.add(_) >> { def params ->
            def action = params[0]
            action.run(null)
        }
        1 * action1.get() >> "one"
        1 * action2.get() >> "two"
        1 * action3.get() >> "three"
        0 * _
    }

    def "collects all failures from actions when running sequentially"() {
        given:
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def action3 = Mock(Supplier)
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()

        when:
        runner.run([action1, action2, action3])

        then:
        def e = thrown(MultipleBuildOperationFailures)
        e.causes == [failure1, failure2]

        1 * buildModelParameters.isParallelToolingApiActions() >> false
        1 * action1.get() >> { throw failure1 }
        1 * action2.get() >> { throw failure2 }
        1 * action3.get() >> "three"
        0 * _
    }

    def "collects all failures from actions when queued to run in parallel"() {
        given:
        def action1 = Mock(Supplier)
        def action2 = Mock(Supplier)
        def action3 = Mock(Supplier)
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()
        def queue = Mock(BuildOperationQueue)

        when:
        runner.run([action1, action2, action3])

        then:
        def e = thrown(MultipleBuildOperationFailures)
        e.causes == [failure1, failure2]

        1 * buildModelParameters.isParallelToolingApiActions() >> true
        1 * buildOperationExecutor.runAllWithAccessToProjectState(_) >> { def params ->
            def queueingAction = params[0]
            queueingAction.execute(queue)
        }
        3 * queue.add(_) >> { def params ->
            def action = params[0]
            action.run(null)
        }
        1 * action1.get() >> { throw failure1 }
        1 * action2.get() >> { throw failure2 }
        1 * action3.get() >> "three"
        0 * _
    }

}
