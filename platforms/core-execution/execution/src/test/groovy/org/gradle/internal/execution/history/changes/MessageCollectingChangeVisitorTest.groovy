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

    def "no messages"() {
        when:
        0 * change1.message >> "first change"
        then:
        assert visitor.getMessages().size() == 0
    }

    def "one message"() {
        when:
        1 * change1.message >> "first change"
        assert visitor.visitChange(change1) == true
        then:
        def messages = visitor.getMessages()
        assert messages.size() == 1
        assert messages.get(0).equals("first change")
    }

    def "two messages"() {
        when:
        1 * change1.message >> "first change"
        assert visitor.visitChange(change1) == true
        1 * change2.message >> "second change"
        assert visitor.visitChange(change2) == true
        then:
        def messages = visitor.getMessages()
        assert messages.size() == 2
        assert messages.get(0).equals("first change")
        assert messages.get(1).equals("second change")
    }

    def "many messages"() {
        when:
        1 * change1.message >> "first change"
        assert visitor.visitChange(change1) == true
        1 * change2.message >> "second change"
        assert visitor.visitChange(change2) == true
        0 * change3.message >> "third change"
        assert visitor.visitChange(change3) == false
        // We stop offering changes to the visitor once it returns false
        then:
        def messages = visitor.getMessages()
        assert messages.size() == 3
        assert messages.get(0).equals("first change")
        assert messages.get(1).equals("second change")
        assert messages.get(2).equals("more...")
    }
}
