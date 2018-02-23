/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations

import spock.lang.Specification

class BuildOperationIdentifierPreservingRunnableTest extends Specification {
    private static final EXPECTED_BUILD_OPERATION_IDENTIFIER = "42"
    def delegate = Mock(Runnable)
    def runner = new BuildOperationIdentifierPreservingRunnable(delegate, EXPECTED_BUILD_OPERATION_IDENTIFIER)

    def "forward execution to delegate runnable"() {
        when:
        runner.run()

        then:
        1 * delegate.run()
        0 * _
    }

    def "preserve build operation identifier during execution of the delegate runnable"() {
        given: "save previous build operation id"
        def previousBuildOperationId = BuildOperationIdentifierRegistry.currentOperationIdentifier

        and: "clear current build operation id to simulate a new thread state"
        BuildOperationIdentifierRegistry.clearCurrentOperationIdentifier()

        when:
        runner.run()

        then:
        1 * delegate.run() >> {
            assert BuildOperationIdentifierRegistry.currentOperationIdentifier == EXPECTED_BUILD_OPERATION_IDENTIFIER
        }
        BuildOperationIdentifierRegistry.currentOperationIdentifier != EXPECTED_BUILD_OPERATION_IDENTIFIER

        cleanup:
        BuildOperationIdentifierRegistry.currentOperationIdentifier = previousBuildOperationId
    }
}
