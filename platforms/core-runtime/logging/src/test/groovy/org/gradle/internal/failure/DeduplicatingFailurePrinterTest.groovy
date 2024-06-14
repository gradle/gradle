/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.failure

import org.gradle.internal.problems.failure.DeduplicatingFailurePrinter
import spock.lang.Specification

class DeduplicatingFailurePrinterTest extends Specification {

    def failureFactory = new TestFailureFactory()

    def "deduplicates the same exception"() {
        given:
        def printer = new DeduplicatingFailurePrinter()

        when:
        def e1 = new RuntimeException("BOOM")
        def printed1 = printer.printToString(failureFactory.createFailure(e1))
        then:
        printed1 != null

        when:
        def printed2 = printer.printToString(failureFactory.createFailure(e1))
        then:
        printed2 == null

        when:
        def e2 = new RuntimeException("ANOTHER BOOM")
        def printed3 = printer.printToString(failureFactory.createFailure(e2))
        then:
        printed3 != null
    }

    def "deduplicates equal exceptions"() {
        given:
        def printer = new DeduplicatingFailurePrinter()
        def exceptions = [new RuntimeException("BOOM"), new RuntimeException("BOOM")]

        when:
        def printed = exceptions.collect {
            printer.printToString(failureFactory.createFailure(it))
        }
        then:
        printed.size() == 2
        printed[0] != null
        printed[1] == null
    }

    def "deduplicates using limited number of stackframes"() {
        given:
        def exceptions = [SimulatedJavaException.simulateDeeperException(), SimulatedJavaException.simulateDeeperException()]
        assert exceptions[0].stackTrace.toList() == exceptions[1].stackTrace.toList()

        removeNthStacktraceFrame(exceptions[1], 2)

        when:
        def printer1 = new DeduplicatingFailurePrinter(3)
        def printed1 = exceptions.collect { printer1.printToString(failureFactory.createFailure(it)) }
        then:
        printed1[0] != null
        printed1[1] != null

        when:
        def printer2 = new DeduplicatingFailurePrinter(2)
        def printed2 = exceptions.collect { printer2.printToString(failureFactory.createFailure(it)) }
        then:
        printed2[0] != null
        printed2[1] == null
    }

    private static void removeNthStacktraceFrame(RuntimeException exception, int frameIndex) {
        exception.stackTrace = exception.stackTrace.toList().tap { remove(frameIndex) }.toArray(new StackTraceElement[0])
    }
}
