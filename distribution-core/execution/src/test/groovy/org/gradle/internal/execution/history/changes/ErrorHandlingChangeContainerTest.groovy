/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.execution.history.changes

import org.gradle.api.Describable
import org.gradle.api.GradleException
import spock.lang.Specification

class ErrorHandlingChangeContainerTest extends Specification {
    def task = Stub(Describable) {
        getDisplayName() >> "task ':test'"
    }
    def delegate = Mock(ChangeContainer)
    def changes = new ErrorHandlingChangeContainer(task, delegate)

    def "accept error reports task path"() {
        when:
        changes.accept(Mock(ChangeVisitor))
        then:
        def ex = thrown GradleException
        ex.message == "Cannot determine changes for task ':test'"
        ex.cause.message == "Error!"
        1 * delegate.accept(_) >> { throw new RuntimeException("Error!") }
    }

    def "visitor error reports task path"() {
        def visitor = Mock(ChangeVisitor)

        when:
        changes.accept(visitor)
        then:
        1 * delegate.accept(_) >> { it[0].visitChange(null) }
        1 * visitor.visitChange(_) >> { throw new RuntimeException("Error!") }

        then:
        def ex = thrown GradleException
        ex.message == "Cannot determine changes for task ':test'"
        ex.cause.message == "Error!"
    }
}
