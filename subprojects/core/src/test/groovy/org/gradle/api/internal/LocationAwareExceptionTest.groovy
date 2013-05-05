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
package org.gradle.api.internal

import spock.lang.Specification
import org.gradle.util.TreeVisitor

class LocationAwareExceptionTest extends Specification {
    def "visit reportable causes does not visit direct cause"() {
        TreeVisitor visitor = Mock()
        def cause = new RuntimeException()
        def e = new LocationAwareException(cause, cause, null, 100)

        when:
        e.visitReportableCauses(visitor)

        then:
        1 * visitor.node(e)
        0 * visitor._
        
        and:
        e.reportableCauses == []
    }

    def "visit reportable causes visits indirect cause"() {
        TreeVisitor visitor = Mock()
        def childCause = new RuntimeException()
        def cause = new RuntimeException(childCause)
        def e = new LocationAwareException(cause, cause, null, 100)

        when:
        e.visitReportableCauses(visitor)

        then:
        1 * visitor.node(e)

        and:
        1 * visitor.startChildren()

        and:
        1 * visitor.node(childCause)

        and:
        1 * visitor.endChildren()
        0 * visitor._
        
        and:
        e.reportableCauses == [childCause]
    }

    def "visit reportable causes visits causes of contextual exception"() {
        TreeVisitor visitor = Mock()
        def childCause = new RuntimeException()
        def cause = new TestContextualException(childCause)
        def e = new LocationAwareException(cause, cause, null, 100)

        when:
        e.visitReportableCauses(visitor)

        then:
        1 * visitor.node(e)

        and:
        1 * visitor.startChildren()

        and:
        1 * visitor.node(childCause)

        and:
        1 * visitor.endChildren()
        0 * visitor._

        and:
        e.reportableCauses == [childCause]
    }

    def "visit reportable causes visits all contextual exceptions and direct cause of last contextual exception"() {
        TreeVisitor visitor = Mock()
        def unreportedCause = new RuntimeException()
        def reportedCause = new RuntimeException(unreportedCause)
        def lastContextual = new TestContextualException(reportedCause)
        def interveningUnreported = new RuntimeException(lastContextual)
        def contextual = new TestContextualException(interveningUnreported)
        def interveningUnreported2 = new RuntimeException(contextual)
        def cause = new TestContextualException(interveningUnreported2)
        def e = new LocationAwareException(cause, cause, null, 100)

        when:
        e.visitReportableCauses(visitor)

        then:
        1 * visitor.node(e)
        1 * visitor.node(contextual)
        1 * visitor.node(lastContextual)
        1 * visitor.node(reportedCause)

        and:
        _ * visitor.startChildren()
        _ * visitor.endChildren()
        0 * visitor._

        and:
        e.reportableCauses == [contextual, lastContextual, reportedCause]
    }

    def "visit reportable causes visits causes of multi-cause exception"() {
        TreeVisitor visitor = Mock()
        def childCause1 = new RuntimeException()
        def childCause2 = new RuntimeException()
        def cause = new AbstractMultiCauseException("broken", childCause1, childCause2)
        def e = new LocationAwareException(cause, cause, null, 100)

        when:
        e.visitReportableCauses(visitor)

        then:
        1 * visitor.node(e)

        and:
        1 * visitor.startChildren()

        and:
        1 * visitor.node(childCause1)

        and:
        1 * visitor.node(childCause2)

        and:
        1 * visitor.endChildren()
        0 * visitor._

        and:
        e.reportableCauses == [childCause1, childCause2]
    }

    def "visit reportable causes visits causes recursively"() {
        TreeVisitor visitor = Mock()
        def ignored = new RuntimeException()
        def childCause1 = new RuntimeException(ignored)
        def childCause2 = new RuntimeException()
        def childCause3 = new TestContextualException(childCause2)
        def childCause4 = new TestContextualException(childCause3)
        def cause = new AbstractMultiCauseException("broken", childCause1, childCause4)
        def e = new LocationAwareException(cause, cause, null, 100)

        when:
        e.visitReportableCauses(visitor)

        then:
        1 * visitor.node(e)

        and:
        3 * visitor.startChildren()
        1 * visitor.node(childCause1)
        1 * visitor.node(childCause4)
        1 * visitor.node(childCause3)
        1 * visitor.node(childCause2)
        3 * visitor.endChildren()
        0 * visitor._

        and:
        e.reportableCauses == [childCause1, childCause4, childCause3, childCause2]
    }

    @Contextual
    class TestContextualException extends RuntimeException {
        TestContextualException(Throwable throwable) {
            super(throwable)
        }
    }
}
