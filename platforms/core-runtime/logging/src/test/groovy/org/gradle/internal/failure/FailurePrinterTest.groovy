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
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.StackTraceRelevance
import spock.lang.Specification

class FailurePrinterTest extends Specification {

    def "prints same as JVM for simple exception"() {
        def e = new RuntimeException("BOOM")

        def printer = new FailurePrinter()
        def f = toFailure(e)

        expect:
        printer.printToString(f) == getTraceString(e)
    }

    def "prints same as JVM for an exception with cause"() {
        def e = new RuntimeException("BOOM", SimulatedJavaException.simulateDeeperException())

        def printer = new FailurePrinter()
        def f = toFailure(e)

        expect:
        printer.printToString(f) == getTraceString(e)
    }

    def "prints same as JVM for an exception with suppressions"() {
        def e = new RuntimeException("BOOM")
        e.addSuppressed(SimulatedJavaException.simulateDeeperException())

        def printer = new FailurePrinter()
        def f = toFailure(e)

        expect:
        printer.printToString(f) == getTraceString(e)
    }

    def "handles circular references"() {
        def e0 = SimulatedJavaException.simulateDeeperException()
        def e = new RuntimeException("BOOM", e0)

        def printer = new FailurePrinter()
        def f = toFailure(e)

        // Create circular reference
        def cause = (MockFailure) f.causes[0]
        cause.overridenCauses = [f]
        cause.overridenSuppressed = [f]

        // Simulate the same circular reference with the originals
        e0.initCause(e)
        e0.addSuppressed(e)

        // Spock fails with Stack Overflow if the power assert fails, so better keep it here for easier debugging
        def expected = getTraceString(e)

        expect:
        printer.printToString(f) == expected
    }

    def "supports multi-case exceptions"() {
        def e = new DefaultMultiCauseException("BOOM", new RuntimeException("one"), new RuntimeException("two"))

        def printer = new FailurePrinter()
        def f = toFailure(e)

        def expected = getTraceString(e)
        def actual = printer.printToString(f)

        expect:
        // First, validate the assumptions about the default multi-cause exception printing.
        // Cannot compare the entire output, because `DefaultMultiCauseException` does not support stack trace tail trimming
        expected.contains("Cause 1: java.lang.RuntimeException: one")
        expected.contains("Cause 2: java.lang.RuntimeException: two")

        and:
        actual.contains("Cause 1: java.lang.RuntimeException: one")
        actual.contains("Cause 2: java.lang.RuntimeException: two")
    }

    private static Failure toFailure(Throwable t) {
        def stack = ImmutableList.of(t.stackTrace)
        def relevances = Collections.nCopies(stack.size(), StackTraceRelevance.USER_CODE)
        def causes = getCauses(t).collect { toFailure(it) }
        def suppressed = t.getSuppressed().collect { toFailure(it) }
        new MockFailure(t, stack, relevances, causes, suppressed)
    }

    private static List<Throwable> getCauses(Throwable t) {
        if (t instanceof MultiCauseException) {
            return t.causes
        }

        t.getCause() == null ? ImmutableList.of() : ImmutableList.of(t.getCause())
    }

    private static String getTraceString(Throwable t) {
        StringWriter out = new StringWriter();
        t.printStackTrace(new PrintWriter(out));
        out.toString()
    }

    static class MockFailure extends DefaultFailure {

        List<Failure> overridenCauses
        List<Failure> overridenSuppressed

        MockFailure(
            Throwable original,
            List<StackTraceElement> stackTrace,
            List<StackTraceRelevance> frameRelevance,
            List<Failure> causes,
            List<Failure> suppressed
        ) {
            super(original, stackTrace, frameRelevance, causes, suppressed)
        }

        @Override
        List<Failure> getCauses() {
            return overridenCauses == null ? super.getCauses() : overridenCauses
        }

        @Override
        List<Failure> getSuppressed() {
            return overridenSuppressed == null ? super.getSuppressed() : overridenSuppressed
        }
    }

}
