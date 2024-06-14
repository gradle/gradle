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


import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailurePrinter
import org.gradle.internal.problems.failure.FailurePrinterListener
import spock.lang.Specification

import static org.gradle.internal.problems.failure.FailurePrinterListener.*
import static org.gradle.internal.problems.failure.StackTraceRelevance.USER_CODE

class FailurePrinterTest extends Specification {

    def "prints same as JVM for simple exception"() {
        def e = new RuntimeException("BOOM")
        def f = toFailure(e)

        expect:
        FailurePrinter.printToString(f) == getTraceString(e)
    }

    def "prints same as JVM for an exception with cause"() {
        def e = new RuntimeException("BOOM", SimulatedJavaException.simulateDeeperException())
        def f = toFailure(e)

        expect:
        FailurePrinter.printToString(f) == getTraceString(e)
    }

    def "prints same as JVM for an exception with suppressions"() {
        def e = new RuntimeException("BOOM")
        e.addSuppressed(SimulatedJavaException.simulateDeeperException())

        def f = toFailure(e)

        expect:
        FailurePrinter.printToString(f) == getTraceString(e)
    }

    def "supports multi-case exceptions"() {
        def e = new DefaultMultiCauseException("BOOM", new RuntimeException("one"), new RuntimeException("two"))

        def f = toFailure(e)

        def expected = getTraceString(e)
        def actual = FailurePrinter.printToString(f)

        expect:
        // First, validate the assumptions about the default multi-cause exception printing.
        // Cannot compare the entire output, because `DefaultMultiCauseException` does not support stack trace tail trimming
        expected.contains("Cause 1: java.lang.RuntimeException: one")
        expected.contains("Cause 2: java.lang.RuntimeException: two")

        and:
        actual.contains("Cause 1: java.lang.RuntimeException: one")
        actual.contains("Cause 2: java.lang.RuntimeException: two")
    }

    def "notifies the listener"() {
        given:
        def e = new RuntimeException("BOOM")
        def firstFrame = e.stackTrace[0]
        def listener = Mock(FailurePrinterListener)
        def f = toFailure(e)

        when:
        def output = new StringBuilder().tap {
            FailurePrinter.print(it, f, listener)
        }.toString()

        then:
        getTraceString(e).startsWith(output)

        and:
        1 * listener.beforeFrames() >> VisitResult.CONTINUE
        1 * listener.beforeFrame(firstFrame, USER_CODE) >> VisitResult.CONTINUE
        _ * listener.beforeFrame(_, _) >> VisitResult.CONTINUE
        1 * listener.afterFrames() >> VisitResult.CONTINUE
        0 * _
    }

    def "listener can terminate traversal before frames"() {
        given:
        def e = new RuntimeException("BOOM")
        def listener = Mock(FailurePrinterListener)
        def f = toFailure(e)

        when:
        def output = new StringBuilder().tap {
            FailurePrinter.print(it, f, listener)
        }.toString()

        then:
        output == head(getTraceString(e), 1)

        and:
        1 * listener.beforeFrames() >> VisitResult.TERMINATE
        0 * listener.beforeFrame(_, _)
        0 * _
    }

    def "listener can terminate traversal after a frame"() {
        given:
        def e = new RuntimeException("BOOM")
        def firstFrame = e.stackTrace[0]

        def listener = Mock(FailurePrinterListener)

        def f = toFailure(e)

        when:
        def output = new StringBuilder().tap {
            FailurePrinter.print(it, f, listener)
        }.toString()

        then:
        output == head(getTraceString(e), 2)

        and:
        1 * listener.beforeFrames() >> VisitResult.CONTINUE
        1 * listener.beforeFrame(firstFrame, USER_CODE) >> VisitResult.CONTINUE
        1 * listener.beforeFrame(_, _) >> VisitResult.TERMINATE
        0 * _
    }

    def "listener can terminate traversal after frames"() {
        given:
        def e = new RuntimeException("BOOM", new RuntimeException("BOOM CAUSE"))

        def listener = Mock(FailurePrinterListener)

        def f = toFailure(e)

        when:
        def output = new StringBuilder().tap {
            FailurePrinter.print(it, f, listener)
        }.toString()

        then:
        !output.contains("BOOM CAUSE")

        and:
        1 * listener.beforeFrames() >> VisitResult.CONTINUE
        _ * listener.beforeFrame(_, _) >> VisitResult.CONTINUE
        1 * listener.afterFrames() >> VisitResult.TERMINATE
        0 * _
    }

    private static Failure toFailure(Throwable t) {
        new TestFailureFactory().createFailure(t)
    }

    private static String getTraceString(Throwable t) {
        StringWriter out = new StringWriter()
        t.printStackTrace(new PrintWriter(out))
        out.toString()
    }

    private static String head(String text, int lines) {
        text.readLines().take(lines).join("\n") + "\n"
    }
}
