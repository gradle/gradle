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

class PreserveBuildOperationIdentifierRunnerTest extends Specification {
    def delegate = Mock(Runnable)

    def "forward execution to delegate runnable"() {
        given:
        def runner = PreserveBuildOperationIdentifierRunner.wrap(delegate)

        when:
        runner.run()

        then:
        1 * delegate.run()
        0 * _
    }

    def "preserve build operation identifier during execution of the delegate runnable"() {
        given:
        def previousBuildOperationId = BuildOperationIdentifierRegistry.currentOperationIdentifier

        // Wrap the delegated
        def expectedBuildOperationId = "42"
        BuildOperationIdentifierRegistry.currentOperationIdentifier = expectedBuildOperationId
        def runner = PreserveBuildOperationIdentifierRunner.wrap(delegate)

        // Simulate a new thread state
        BuildOperationIdentifierRegistry.clearCurrentOperationIdentifier()

        when:
        runner.run()

        then:
        delegate.run() >> {
            assert BuildOperationIdentifierRegistry.currentOperationIdentifier == expectedBuildOperationId
        }

        cleanup:
        BuildOperationIdentifierRegistry.currentOperationIdentifier = previousBuildOperationId
    }
}
