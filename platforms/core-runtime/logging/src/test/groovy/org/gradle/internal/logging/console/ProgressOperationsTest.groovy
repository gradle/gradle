/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.console

import org.gradle.internal.operations.OperationIdentifier
import spock.lang.Specification

class ProgressOperationsTest extends Specification {

    def ops = new ProgressOperations()

    def "starts operation"() {
        when:
        def op = ops.start("compile", null, new OperationIdentifier(1), null)

        then:
        op.parent == null
        op.operationId.id == 1L
    }

    def "maintains operation hierarchy"() {
        when:
        def op1 = ops.start("compile", null, new OperationIdentifier(1), null)
        def op2 = ops.start("resolve", null, new OperationIdentifier(2), new OperationIdentifier(1))

        then:
        op1.operationId.id == 1L
        op1.parent == null
        op2.operationId.id == 2L
        op2.parent == op1
    }

    def "operation cannot be started when it is already running"() {
        given:
        ops.start("compile", null, new OperationIdentifier(1), null).message == "compile"
        ops.progress("compiling...", new OperationIdentifier(1)).message == "compiling..."

        when:
        ops.start("resolve", null, new OperationIdentifier(1), null).message == "resolve"

        then:
        thrown(IllegalStateException)
    }

    def "starts operations from different hierarchies"() {
        when:
        def op1 = ops.start("compile", null, new OperationIdentifier(1), null)
        def op2 = ops.start("resolve", null, new OperationIdentifier(2), null)

        then:
        op1.operationId.id == 1L
        op1.parent == null
        op2.operationId.id == 2L
        op2.parent == null
    }

    def "tracks progress"() {
        when:
        ops.start("Building", null, new OperationIdentifier(1), null)
        def op2 = ops.start("Resolving", null, new OperationIdentifier(2), new OperationIdentifier(1))
        def op3 = ops.progress("Download", new OperationIdentifier(2))

        then:
        op2 == op3
        op2.message == "Download"
        op2.parent.message == "Building"
    }

    def "progress cannot be reported for unknown operation"() {
        when:
        ops.progress("Download", new OperationIdentifier(1))

        then:
        thrown(IllegalStateException)
    }

    def "cannot complete an event that has not been started"() {
        when:
        ops.complete(new OperationIdentifier(2))

        then:
        thrown(IllegalStateException)
    }

    def "completed events are no longer tracked"() {
        when:
        ops.start("Building", null, new OperationIdentifier(1), null)
        ops.start("Resolving", null, new OperationIdentifier(2), new OperationIdentifier(1))
        def op3 = ops.progress("Download", new OperationIdentifier(2))
        def op4 = ops.complete(new OperationIdentifier(2))

        then:
        op3 == op4

        when:
        ops.progress("foo", new OperationIdentifier(2))

        then:
        thrown(IllegalStateException)
    }

    def "missing parents are tolerated"() {
        when:
        def op = ops.start("Building", null, new OperationIdentifier(1), new OperationIdentifier(122))

        then:
        op.parent == null
    }
}
