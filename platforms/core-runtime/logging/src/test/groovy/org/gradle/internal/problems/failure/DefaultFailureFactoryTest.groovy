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

package org.gradle.internal.problems.failure

import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.failure.SimulatedJavaException
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException

class DefaultFailureFactoryTest extends Specification {

    def "creates failure from a throwable with circular references"() {
        def factory = new DefaultFailureFactory(StackTraceClassifier.USER_CODE)

        def e0 = SimulatedJavaException.simulateDeeperException()
        def e = new RuntimeException("BOOM", e0)
        e0.initCause(e)
        e0.addSuppressed(e)

        when:
        def failure = factory.create(e)
        def failureCause = failure.causes[0]

        then:
        failureCause.causes[0].header.contains("CIRCULAR REFERENCE")
        failureCause.suppressed[0].header.contains("CIRCULAR REFERENCE")
    }

    def "the same exception on different branches is not detected as circular reference"() {
        def factory = new DefaultFailureFactory(StackTraceClassifier.USER_CODE)

        def e0 = SimulatedJavaException.simulateDeeperException()
        def e = new RuntimeException("BOOM", e0)
        def multiCause = new DefaultMultiCauseException("Multiple failure", e, e0)
        e.addSuppressed(e0)

        when:
        def failure = factory.create(multiCause)
        def firstCause = failure.causes[0]
        def secondCause = failure.causes[1]


        then:
        firstCause.header == "java.lang.RuntimeException: BOOM"
        firstCause.causes[0].header == "java.lang.RuntimeException: Simulated exception"
        firstCause.suppressed[0].header == "java.lang.RuntimeException: Simulated exception"

        secondCause.header == "java.lang.RuntimeException: Simulated exception"
        secondCause.causes.empty
    }

    def "unwraps InvocationTargetException when it appears as a cause"() {
        def factory = new DefaultFailureFactory(StackTraceClassifier.USER_CODE)
        def real = new IllegalStateException("real failure")
        def outer = new RuntimeException("outer", new InvocationTargetException(real))

        when:
        def failure = factory.create(outer)

        then:
        failure.exceptionType == RuntimeException
        failure.causes.size() == 1
        failure.causes[0].original.is(real)
        failure.causes[0].causes.empty
    }

    def "unwraps InvocationTargetException when it is the top-level throwable"() {
        def factory = new DefaultFailureFactory(StackTraceClassifier.USER_CODE)
        def real = new IllegalStateException("real failure")

        when:
        def failure = factory.create(new InvocationTargetException(real))

        then:
        failure.original.is(real)
    }

    def "collapses chained InvocationTargetExceptions"() {
        def factory = new DefaultFailureFactory(StackTraceClassifier.USER_CODE)
        def real = new IllegalStateException("real failure")
        def doubleWrapped = new InvocationTargetException(new InvocationTargetException(real))

        when:
        def failure = factory.create(doubleWrapped)

        then:
        failure.original.is(real)
    }

    def "keeps InvocationTargetException with no target as-is"() {
        def factory = new DefaultFailureFactory(StackTraceClassifier.USER_CODE)
        def ite = new InvocationTargetException(null)

        when:
        def failure = factory.create(ite)

        then:
        failure.exceptionType == InvocationTargetException
        failure.causes.empty
    }

}
