/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.progress

import org.gradle.internal.Factory
import org.gradle.internal.TimeProvider
import spock.lang.Specification

class DefaultBuildOperationExecutorTest extends Specification {
    def listener = Mock(InternalBuildListener)
    def timeProvider = Mock(TimeProvider)
    def executor = new DefaultBuildOperationExecutor(listener, timeProvider)

    def "fires events when operation starts and finishes successfully"() {
        def action = Mock(Factory)

        when:
        def result = executor.run("id", BuildOperationType.CONFIGURING_BUILD, action)

        then:
        result == "result"

        and:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id"
            assert operation.parentId == null
            assert operation.operationType == BuildOperationType.CONFIGURING_BUILD
            assert start.startTime == 123L
        }
        1 * action.create() >> "result"
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id"
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == null
        }
    }

    def "fires events when operation starts and fails"() {
        def action = Mock(Factory)
        def failure = new RuntimeException()

        when:
        executor.run("id", BuildOperationType.CONFIGURING_BUILD, action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id"
            assert operation.parentId == null
            assert operation.operationType == BuildOperationType.CONFIGURING_BUILD
            assert start.startTime == 123L
        }
        1 * action.create() >> { throw failure }
        1 * timeProvider.currentTime >> 124L
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id"
            assert opResult.startTime == 123L
            assert opResult.endTime == 124L
            assert opResult.failure == failure
        }
    }

    def "can query operation id from inside operation"() {
        def action1 = Mock(Runnable)
        def action2 = Mock(Runnable)

        when:
        executor.run("id", BuildOperationType.CONFIGURING_BUILD, action1)

        then:
        1 * action1.run() >> {
            assert executor.currentOperationId == "id"
            executor.run("id2", BuildOperationType.EXECUTING_TASKS, action2)
        }
        1 * action2.run() >> {
            assert executor.currentOperationId == "id2"
        }
    }

    def "cannot query operation id when no operation running"() {
        when:
        executor.currentOperationId

        then:
        IllegalStateException e = thrown()
        e.message == "No operation is currently running."
    }

    def "attaches parent id when operation is nested inside another"() {
        def action1 = Mock(Factory)
        def action2 = Mock(Factory)
        def action3 = Mock(Factory)

        when:
        def result = executor.run("id", BuildOperationType.CONFIGURING_BUILD, action1)

        then:
        result == "result"

        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id"
            assert operation.parentId == null
        }
        1 * action1.create() >> {
            return executor.run("id2", BuildOperationType.EVALUATING_INIT_SCRIPTS, action2)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id2"
            assert operation.parentId == "id"
        }
        1 * action2.create() >> {
            return executor.run("id3", BuildOperationType.EXECUTING_TASKS, action3)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id3"
            assert operation.parentId == "id2"
        }
        1 * action3.create() >> {
            return "result"
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id3"
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id2"
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id"
        }
    }

    def "attaches parent id when sibling operation fails"() {
        def action1 = Mock(Factory)
        def action2 = Mock(Factory)
        def action3 = Mock(Factory)

        when:
        def result = executor.run("id", BuildOperationType.CONFIGURING_BUILD, action1)

        then:
        result == "result"

        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id"
            assert operation.parentId == null
        }
        1 * action1.create() >> {
            try {
                executor.run("id2", BuildOperationType.EVALUATING_INIT_SCRIPTS, action2)
            } catch (RuntimeException) {
                // Ignore
            }
            return executor.run("id3", BuildOperationType.EXECUTING_TASKS, action3)
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id2"
            assert operation.parentId == "id"
        }
        1 * action2.create() >> { throw new RuntimeException() }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id2"
        }

        and:
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id3"
            assert operation.parentId == "id"
        }
        1 * action3.create() >> {
            return "result"
        }
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id3"
        }

        and:
        1 * listener.finished(_, _) >> { BuildOperationInternal operation, OperationResult opResult ->
            assert operation.id == "id"
        }
    }
}
