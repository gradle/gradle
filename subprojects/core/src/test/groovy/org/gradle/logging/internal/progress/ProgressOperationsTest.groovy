/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.logging.internal.progress

import org.gradle.internal.progress.OperationIdentifier
import spock.lang.Specification

class ProgressOperationsTest extends Specification {

    def ops = new ProgressOperations()

    def "starts operation"() {
        when:
        def op = ops.start("compile", null, new OperationIdentifier(1), null)

        then:
        op.parent == null
        op.message == "compile"
    }

    def "starts operations"() {
        when:
        def op1 = ops.start("compile", null, new OperationIdentifier(1), null)
        def op2 = ops.start("resolve", null, new OperationIdentifier(2), new OperationIdentifier(1))

        then:
        op1.message == "compile"
        op1.parent == null
        op2.message == "resolve"
        op2.parent == op1
    }

    def "operation can be started multiple times"() {
        expect:
        ops.start("compile", null, new OperationIdentifier(1), null).message == "compile"
        ops.progress("compiling...", new OperationIdentifier(1)).message == "compiling..."
        ops.start("resolve", null, new OperationIdentifier(1), null).message == "resolve"
        ops.progress("resolving...", new OperationIdentifier(1)).message == "resolving..."
        ops.complete(new OperationIdentifier(1)).message == "resolving..."
    }

    def "starts operations from different hierarchies"() {
        when:
        def op1 = ops.start("compile", null, new OperationIdentifier(1), null)
        def op2 = ops.start("resolve", null, new OperationIdentifier(2), null)

        then:
        op1.message == "compile"
        op1.parent == null
        op2.message == "resolve"
        op2.parent == null
    }

    def "the operation uses status first"() {
        expect:
        ops.start("foo", "compiling now", new OperationIdentifier(1), null).message == "compiling now"
    }

    def "tracks progress"() {
        when:
        ops.start("Building", "", new OperationIdentifier(1), null)
        def op2 = ops.start("Resolving", "", new OperationIdentifier(2), new OperationIdentifier(1))
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

    def "completed events are no longer tracked"() {
        when:
        ops.start("Building", "", new OperationIdentifier(1), null)
        ops.start("Resolving", "", new OperationIdentifier(2), new OperationIdentifier(1))
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
        def op = ops.start("Building", "", new OperationIdentifier(1), new OperationIdentifier(122))

        then:
        op.parent == null
    }
}
