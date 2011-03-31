/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal

import spock.lang.Specification

class AbstractMultiCauseExceptionTest extends Specification {
    def getCauseReturnsTheFirstCause() {
        def cause1 = new RuntimeException()
        def cause2 = new RuntimeException()
        def failure = new TestMultiCauseException('message', [cause1, cause2])

        expect:
        failure.cause == cause1
        failure.causes == [cause1, cause2]
    }

    def getCauseReturnsNullWhenThereAreNoCauses() {
        def failure = new TestMultiCauseException('message', [])

        expect:
        failure.cause == null
        failure.causes == []
    }
    
    def printStackTraceWithMultipleCauses() {
        RuntimeException cause1 = new RuntimeException('cause1')
        RuntimeException cause2 = new RuntimeException('cause2')
        def failure = new TestMultiCauseException('message', [cause1, cause2])
        def outstr = new StringWriter()

        when:
        outstr.withPrintWriter { writer ->
            failure.printStackTrace(writer)
        }

        then:
        outstr.toString().contains("${TestMultiCauseException.name}: message")
        outstr.toString().contains("Cause 1: ${RuntimeException.name}: cause1")
        outstr.toString().contains("Cause 2: ${RuntimeException.name}: cause2")
    }
    
    def printStackTraceWithSingleCause() {
        RuntimeException cause1 = new RuntimeException('cause1')
        def failure = new TestMultiCauseException('message', [cause1])
        def outstr = new StringWriter()

        when:
        outstr.withPrintWriter { writer ->
            failure.printStackTrace(writer)
        }

        then:
        outstr.toString().contains("${TestMultiCauseException.name}: message")
        outstr.toString().contains("Caused by: ${RuntimeException.name}: cause1")
    }
}

class TestMultiCauseException extends AbstractMultiCauseException {
    TestMultiCauseException(String message, Iterable<? extends Throwable> causes) {
        super(message, causes)
    }
}