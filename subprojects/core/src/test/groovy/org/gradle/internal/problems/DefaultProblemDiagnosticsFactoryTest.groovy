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

import spock.lang.Specification

class DefaultProblemDiagnosticsFactoryTest extends Specification {
    def locationAnalyzer = Mock(ProblemLocationAnalyzer)

    def "does not populate stack traces after limit has been reached"() {
        def factory = new DefaultProblemDiagnosticsFactory(locationAnalyzer, 2)

        expect:
        def diagnostics1 = factory.forCurrentCaller { it.toList() }
        diagnostics1.exception == null
        !diagnostics1.stack.empty

        def diagnostics2 = factory.forCurrentCaller { it.toList() }
        diagnostics2.exception == null
        !diagnostics2.stack.empty

        def diagnostics3 = factory.forCurrentCaller { it.toList() }
        diagnostics3.exception == null
        diagnostics3.stack.empty
    }

    def "keeps stack trace after limit has been reached when diagnostics constructed from exception"() {
        def factory = new DefaultProblemDiagnosticsFactory(locationAnalyzer, 2)

        given:
        factory.forCurrentCaller { it.toList() }
        factory.forCurrentCaller { it.toList() }

        expect:
        def failure1 = new Exception("broken")
        def diagnostics1 = factory.forCurrentCaller(failure1)
        diagnostics1.exception == failure1
        !diagnostics1.stack.empty

        def failure2 = new Exception("broken")
        def diagnostics2 = factory.forException(failure2)
        diagnostics2.exception == failure2
        !diagnostics2.stack.empty
    }
}
