/*
 * Copyright 2013 the original author or authors.
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


import spock.lang.Specification

class SummarizingChangeContainerTest extends Specification {

    def state1 = Mock(ChangeContainer)
    def state2 = Mock(ChangeContainer)
    def state = new SummarizingChangeContainer(state1, state2)
    def visitor = new CollectingChangeVisitor()

    def "looks for changes in all delegate change sets"() {
        when:
        state.accept(visitor)

        then:
        1 * state1.accept(_) >> true
        1 * state2.accept(_) >> true
        0 * _

        and:
        visitor.getChanges().empty
    }

    def "only returns changes from a single delegate"() {
        def change1 = Mock(Change)

        when:
        state.accept(visitor)

        then:
        1 * state1.accept(_) >> { args ->
            args[0].visitChange(change1)
        }
        0 * _

        and:
        visitor.changes == [change1]
    }
}
