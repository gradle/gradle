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
import org.gradle.internal.problems.failure.Failure
import org.gradle.internal.problems.failure.StackTraceRelevance
import spock.lang.Specification

class DefaultFailurePrinterTest extends Specification {

    def "prints same as JVM for simple exception"() {
        def e = new RuntimeException("BOOM")

        def printer = new DefaultFailurePrinter()
        def f = toFailure(e)

        expect:
        printer.print(f) == getTraceString(e)
    }

    def "prints same as JVM for an exception with cause"() {
        def e = new RuntimeException("BOOM", SimulatedJavaException.simulateDeeperException())

        def printer = new DefaultFailurePrinter()
        def f = toFailure(e)

        expect:
        printer.print(f) == getTraceString(e)
    }

    def "prints same as JVM for an exception with suppressions"() {
        def e = new RuntimeException("BOOM")
        e.addSuppressed(SimulatedJavaException.simulateDeeperException())

        def printer = new DefaultFailurePrinter()
        def f = toFailure(e)

        expect:
        printer.print(f) == getTraceString(e)
    }

    private static Failure toFailure(Throwable t) {
        def stack = ImmutableList.of(t.stackTrace)
        def relevances = Collections.nCopies(stack.size(), StackTraceRelevance.USER_CODE)
        new DefaultFailure(t, stack, relevances, getCauses(t).collect { toFailure(it) }, t.getSuppressed().collect { toFailure(it) })
    }

    private static List<Throwable> getCauses(Throwable t) {
        t.getCause() == null ? ImmutableList.of() : ImmutableList.of(t.getCause())
    }

    private static String getTraceString(Throwable t) {
        StringWriter out = new StringWriter();
        t.printStackTrace(new PrintWriter(out));
        out.toString()
    }

}
