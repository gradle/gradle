/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.buildevents

import org.gradle.internal.exceptions.ContextAwareException
import org.gradle.internal.exceptions.Contextual
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.internal.problems.failure.DefaultFailureFactory
import org.gradle.internal.problems.failure.Failure
import spock.lang.Specification

class ContextAwareExceptionHandlerTest extends Specification {
    private ExceptionContextVisitor visitor = Mock()
    private failureFactory = DefaultFailureFactory.withDefaultClassifier()

    def "visitor does not visit direct cause"() {
        def cause = new RuntimeException()
        def e = new ContextAwareException(cause)

        when:
        accept(e, visitor)

        then:
        1 * visitor.visitCause(f(cause))
        1 * visitor.endVisiting()
        0 * visitor._

        and:
        getReportableCauses(e) == []
    }

    def "visitor visits indirect cause"() {
        def childCause = new RuntimeException()
        def cause = new RuntimeException(childCause)
        def e = new ContextAwareException(cause)

        when:
        accept(e, visitor)

        then:
        1 * visitor.visitCause(f(cause))
        1 * visitor.endVisiting()
        1 * visitor.startChildren()

        and:
        1 * visitor.node(f(childCause))

        and:
        1 * visitor.endChildren()
        0 * visitor._

        and:
        getReportableCauses(e) == [childCause]
    }

    def "visitor visits causes of contextual exception"() {
        def childCause = new RuntimeException()
        def cause = new TestContextualException(childCause)
        def e = new ContextAwareException(cause)

        when:
        accept(e, visitor)

        then:
        1 * visitor.visitCause(f(cause))
        1 * visitor.endVisiting()
        1 * visitor.startChildren()

        and:
        1 * visitor.node(f(childCause))

        and:
        1 * visitor.endChildren()
        0 * visitor._

        and:
        getReportableCauses(e) == [childCause]
    }

    def "visitor visits all contextual exceptions and direct cause of last contextual exception"() {
        def unreportedCause = new RuntimeException()
        def reportedCause = new RuntimeException(unreportedCause)
        def lastContextual = new TestContextualException(reportedCause)
        def interveningUnreported = new RuntimeException(lastContextual)
        def contextual = new TestContextualException(interveningUnreported)
        def interveningUnreported2 = new RuntimeException(contextual)
        def cause = new TestContextualException(interveningUnreported2)
        def e = new ContextAwareException(cause)

        when:
        accept(e, visitor)

        then:
        1 * visitor.visitCause(f(cause))
        1 * visitor.endVisiting()

        1 * visitor.node(f(contextual))
        1 * visitor.node(f(lastContextual))
        1 * visitor.node(f(reportedCause))

        and:
        _ * visitor.startChildren()
        _ * visitor.endChildren()
        0 * visitor._

        and:
        getReportableCauses(e) == [contextual, lastContextual, reportedCause]
    }

    def "visitor visits causes of multi-cause exception"() {
        def childCause1 = new RuntimeException()
        def childCause2 = new RuntimeException()
        def cause = new DefaultMultiCauseException("broken", childCause1, childCause2)
        def e = new ContextAwareException(cause)

        when:
        accept(e, visitor)

        then:
        1 * visitor.visitCause(f(cause))
        1 * visitor.endVisiting()

        1 * visitor.startChildren()

        and:
        1 * visitor.node(f(childCause1))

        and:
        1 * visitor.node(f(childCause2))

        and:
        1 * visitor.endChildren()
        0 * visitor._

        and:
        getReportableCauses(e) == [childCause1, childCause2]
    }

    def "visitor treats multi-cause exception as contextual"() {
        def childCause1 = new RuntimeException()
        def detail = new RuntimeException()
        def childCause2 = new TestContextualException(detail)
        def cause = new DefaultMultiCauseException("cause", childCause1, childCause2)
        def intermediate1 = new RuntimeException("intermediate1", cause)
        def intermediate2 = new RuntimeException("intermediate2", intermediate1)
        def e = new ContextAwareException(intermediate2)

        when:
        accept(e, visitor)

        then:
        1 * visitor.visitCause(f(intermediate2))
        1 * visitor.endVisiting()

        1 * visitor.startChildren()

        and:
        1 * visitor.node(f(cause))

        and:
        1 * visitor.startChildren()

        and:
        1 * visitor.node(f(childCause1))

        and:
        1 * visitor.node(f(childCause2))

        and:
        1 * visitor.startChildren()

        and:
        1 * visitor.node(f(detail))

        and:
        3 * visitor.endChildren()
        0 * visitor._

        and:
        getReportableCauses(e) == [cause, childCause1, childCause2, detail]
    }

    def "visitor visits causes recursively"() {
        def ignored = new RuntimeException()
        def childCause1 = new RuntimeException(ignored)
        def childCause2 = new RuntimeException()
        def childCause3 = new TestContextualException(childCause2)
        def childCause4 = new TestContextualException(childCause3)
        def cause = new DefaultMultiCauseException("broken", childCause1, childCause4)
        def e = new ContextAwareException(cause)

        when:
        accept(e, visitor)

        then:
        1 * visitor.visitCause(f(cause))
        1 * visitor.endVisiting()

        3 * visitor.startChildren()
        1 * visitor.node(f(childCause1))
        1 * visitor.node(f(childCause4))
        1 * visitor.node(f(childCause3))
        1 * visitor.node(f(childCause2))
        3 * visitor.endChildren()
        0 * visitor._

        and:
        getReportableCauses(e) == [childCause1, childCause4, childCause3, childCause2]
    }

    @Contextual
    class TestContextualException extends RuntimeException {
        TestContextualException(Throwable throwable) {
            super(throwable)
        }
    }

    private void accept(ContextAwareException e, ExceptionContextVisitor visitor) {
        def failure = failureFactory.create(e)
        ContextAwareExceptionHandler.accept(failure, visitor)
    }

    private List<Throwable> getReportableCauses(Throwable e) {
        return ContextAwareExceptionHandler.getReportableCauses(failureFactory.create(e)).collect { it.original }
    }

    private Failure f(Throwable e) {
        failureFactory.create(e)
    }
}
