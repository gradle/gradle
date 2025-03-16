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

package org.gradle.internal.execution.history.changes

import spock.lang.Specification

class MessageCollectingChangeVisitorTest extends Specification {
    def maxNumMessages = 2
    def visitor = new MessageCollectingChangeVisitor(maxNumMessages)
    def change1 = Mock(Change)
    def change2 = Mock(Change)
    def change3 = Mock(Change)
    def change4 = Mock(Change)
    def change5 = Mock(Change)

    def "no messages"() {
        when:
        0 * change1.message >> "first change"

        then:
        visitor.getMessages().isEmpty()
    }

    def "one message"() {
        when:
        1 * change1.message >> "first change"

        then:
        visitor.visitChange(change1)
        visitor.getMessages() == ["first change"]
    }

    def "two messages"() {
        when:
        1 * change1.message >> "first change"

        then:
        visitor.visitChange(change1)

        when:
        1 * change2.message >> "second change"

        then:
        visitor.visitChange(change2)
        visitor.getMessages() == ["first change", "second change"]
    }

    def "many messages"() {
        when:
        1 * change1.message >> "first change"

        then:
        visitor.visitChange(change1)

        when:
        1 * change2.message >> "second change"

        then:
        visitor.visitChange(change2)

        when:
        0 * change3.message >> "third change"
        0 * change4.message >> "forth change"
        0 * change5.message >> "fifth change"

        then:
        // We stop offering changes to the visitor once it returns false
        !visitor.visitChange(change3)
        !visitor.visitChange(change4)
        !visitor.visitChange(change5)
        visitor.getMessages() == ["first change", "second change", "and more..."]
    }
}
