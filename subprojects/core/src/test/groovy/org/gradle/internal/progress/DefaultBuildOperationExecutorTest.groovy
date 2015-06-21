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
        def result = executor.run("id", "parent", BuildOperationType.CONFIGURING_BUILD, action)

        then:
        result == "result"

        and:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id"
            assert operation.parentId == "parent"
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
        executor.run("id", "parent", BuildOperationType.CONFIGURING_BUILD, action)

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * timeProvider.currentTime >> 123L
        1 * listener.started(_, _) >> { BuildOperationInternal operation, OperationStartEvent start ->
            assert operation.id == "id"
            assert operation.parentId == "parent"
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
}
