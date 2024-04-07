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

import com.google.common.collect.ImmutableList
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.internal.problems.failure.DefaultFailure
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.FailurePrinter
import org.gradle.internal.problems.failure.FailurePrinterListener
import spock.lang.Specification

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
        def e = new RuntimeException("BOOM")
        def firstFrame = e.stackTrace[0]

        def listener = Mock(FailurePrinterListener)

        def f = toFailure(e)

        when:
        def output = new StringBuilder()
        FailurePrinter.print(output, f, listener)

        then:
        getTraceString(e).startsWith(output.toString())

        and:
        1 * listener.beforeFrames()
        1 * listener.beforeFrame(firstFrame, USER_CODE)
        1 * listener.afterFrames()
    }

    private static Failure toFailure(Throwable t) {
        def stack = ImmutableList.copyOf(t.stackTrace)
        def relevances = Collections.nCopies(stack.size(), USER_CODE)
        def causes = getCauses(t).collect { toFailure(it) }
        def suppressed = t.getSuppressed().collect { toFailure(it) }
        new DefaultFailure(t, stack, relevances, suppressed, causes)
    }

    private static List<Throwable> getCauses(Throwable t) {
        if (t instanceof MultiCauseException) {
            return t.causes
        }

        t.getCause() == null ? ImmutableList.of() : ImmutableList.of(t.getCause())
    }

    private static String getTraceString(Throwable t) {
        StringWriter out = new StringWriter()
        t.printStackTrace(new PrintWriter(out))
        out.toString()
    }
}
