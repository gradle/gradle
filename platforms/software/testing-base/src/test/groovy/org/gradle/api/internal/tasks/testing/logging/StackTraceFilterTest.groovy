/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging

import spock.lang.Specification
import org.gradle.api.specs.Spec

class StackTraceFilterTest extends Specification {
    def spec = Mock(Spec)
    def filter = new StackTraceFilter(spec)
    def exception = new Exception()
    def trace = exception.stackTrace as List

    def "returns a new trace rather than mutating the original one"() {
        spec.isSatisfiedBy(_) >> true

        when:
        def filtered = filter.filter(trace)

        then:
        !filtered.is(trace)
        trace == old(trace)
    }

    def "filters stack trace according to the provided spec"() {
        spec.isSatisfiedBy(_) >> { StackTraceElement elem -> elem.methodName.size() > 10 }

        when:
        def filtered = filter.filter(trace)

        then:
        filtered.size() > 0
        filtered.size() < trace.size()
        filtered == trace.findAll { StackTraceElement elem -> elem.methodName.size() > 10 }
    }

    def "filters stack trace in invocation order (bottom to top)"() {
        trace = [
                new StackTraceElement("ClassName", "methodName", "FileName.java", 1),
                new StackTraceElement("ClassName", "methodName", "FileName.java", 2)
        ]

        when:
        filter.filter(trace)

        then:
        1 * spec.isSatisfiedBy({ it.lineNumber == 2 })

        then:
        1 * spec.isSatisfiedBy({ it.lineNumber == 1 })
    }
}
