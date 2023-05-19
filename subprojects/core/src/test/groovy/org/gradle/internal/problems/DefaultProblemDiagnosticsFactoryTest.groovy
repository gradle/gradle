/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.problems

import org.gradle.problems.Location
import org.gradle.problems.buildtree.ProblemDiagnosticsFactory
import spock.lang.Specification

import java.util.function.Supplier

class DefaultProblemDiagnosticsFactoryTest extends Specification {
    def locationAnalyzer = Mock(ProblemLocationAnalyzer)
    def factory = new DefaultProblemDiagnosticsFactory(locationAnalyzer, 2)

    def "uses caller's stack trace to calculate problem location"() {
        given:
        def location = Stub(Location)

        when:
        def diagnostics = factory.forCurrentCaller()

        then:
        diagnostics.exception == null
        assertIsCallerStackTrace(diagnostics.stack)
        diagnostics.location == location

        1 * locationAnalyzer.locationForUsage(_, false) >> { List<StackTraceElement> trace, b ->
            assertIsCallerStackTrace(trace)
            location
        }
    }

    def "uses caller provided exception factory to calculate problem location"() {
        given:
        def exception = new Exception()
        def supplier = Stub(Supplier) {
            get() >> exception
        }
        def location = Stub(Location)

        when:
        def diagnostics = factory.forCurrentCaller(supplier)

        then:
        diagnostics.exception == exception
        diagnostics.stack == exception.stackTrace.toList()
        diagnostics.location == location

        1 * locationAnalyzer.locationForUsage(_, false) >> { List<StackTraceElement> trace, b ->
            assert trace == exception.stackTrace.toList()
            location
        }
    }

    def "does not populate stack traces after limit has been reached"() {
        def transformer = Stub(ProblemDiagnosticsFactory.StackTraceTransformer) {
            transform(_) >> { StackTraceElement[] original -> original.toList() }
        }
        def supplier = Stub(Supplier) {
            get() >> { throw new Exception() }
        }

        expect:
        def diagnostics1 = factory.forCurrentCaller(transformer)
        diagnostics1.exception == null
        !diagnostics1.stack.empty

        def diagnostics2 = factory.forCurrentCaller(transformer)
        diagnostics2.exception == null
        !diagnostics2.stack.empty

        def diagnostics3 = factory.forCurrentCaller(transformer)
        diagnostics3.exception == null
        diagnostics3.stack.empty

        def diagnostics4 = factory.forCurrentCaller()
        diagnostics4.exception == null
        diagnostics4.stack.empty

        def diagnostics5 = factory.forCurrentCaller(supplier)
        diagnostics5.exception == null
        diagnostics5.stack.empty
    }

    def "keeps stack trace after limit has been reached when diagnostics constructed from exception"() {
        given:
        factory.forCurrentCaller()
        factory.forCurrentCaller()

        expect:
        factory.forCurrentCaller().stack.empty

        def failure1 = new Exception("broken")
        def diagnostics1 = factory.forCurrentCaller(failure1)
        diagnostics1.exception == failure1
        !diagnostics1.stack.empty

        def failure2 = new Exception("broken")
        def diagnostics2 = factory.forException(failure2)
        diagnostics2.exception == failure2
        !diagnostics2.stack.empty
    }

    void assertIsCallerStackTrace(List<StackTraceElement> trace) {
        assert trace.any { it.className == DefaultProblemDiagnosticsFactoryTest.name }
    }
}
