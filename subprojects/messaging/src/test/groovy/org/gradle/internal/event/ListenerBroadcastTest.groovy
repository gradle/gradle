/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.internal.event

import org.gradle.api.Action
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.MethodInvocation
import spock.lang.Specification

class ListenerBroadcastTest extends Specification {
    private final ListenerBroadcast<TestListener> broadcast = new ListenerBroadcast<TestListener>(TestListener.class)

    def 'creates source object'() {
        expect:
        broadcast.getSource() != null
        broadcast.getSource() is broadcast.getSource()
        broadcast.getSource() != new ListenerBroadcast<TestListener>(TestListener.class).getSource()
        broadcast.getSource().hashCode() == broadcast.getSource().hashCode()
        broadcast.getSource().toString() == 'TestListener broadcast'
    }

    def 'getType yields the listener class'() {
        expect:
        broadcast.type == TestListener
    }

    def 'source object does nothing when no listeners are added'() {
        expect:
        broadcast.source.event1("param")
    }

    def 'visit listeners does nothing when no listeners are added'() {
        def visitor = Mock(Action)

        when:
        broadcast.visitListeners(visitor)

        then:
        0 * visitor._
    }

    def 'source object notifies each listener in the added order'() {
        given:
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        broadcast.add(listener1)
        broadcast.add(listener2)

        when:
        broadcast.source.event1("param")

        then:
        1 * listener1.event1("param")
        then:
        1 * listener2.event1("param")
        0 * _._
    }

    def 'can dispatch event to listeners'() {
        given:
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        broadcast.add(listener1)
        broadcast.add(listener2)

        when:
        MethodInvocation invocation = new MethodInvocation(TestListener.getMethod("event1", String.class), "param")
        broadcast.dispatch(invocation)

        then:
        1 * listener1.event1("param")
        then:
        1 * listener2.event1("param")
        0 * _._
    }

    def 'visit listener visits listeners in order added'() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        def visitor = Mock(Action)

        given:
        broadcast.add(listener1)
        broadcast.add(listener2)

        when:
        broadcast.visitListeners(visitor)

        then:
        1 * visitor.execute(listener1)

        then:
        1 * visitor.execute(listener2)
        0 * visitor._
    }

    def 'listener is not used after it is removed'() {
        given:
        def listener = Mock(TestListener)
        broadcast.add(listener)
        broadcast.remove(listener)

        when:
        broadcast.source.event1("param")

        then:
        0 * _._
    }

    def 'listener is not visited after it is removed'() {
        def visitor = Mock(Action)
        def listener = Mock(TestListener)

        given:
        broadcast.add(listener)
        broadcast.remove(listener)

        when:
        broadcast.visitListeners(visitor)

        then:
        0 * visitor._
    }

    def 'listener is not used after all listeners removed'() {
        given:
        def listener = Mock(TestListener)
        broadcast.add(listener)
        broadcast.removeAll()

        when:
        broadcast.source.event1("param")

        then:
        0 * _._
    }

    def 'can use dispatch to receive notifications'() {
        given:
        Dispatch<MethodInvocation> dispatch1 = Mock()
        Dispatch<MethodInvocation> dispatch2 = Mock()
        def invocation = new MethodInvocation(TestListener.getMethod("event1", String.class), "param")

        broadcast.add(dispatch1)
        broadcast.add(dispatch2)

        when:
        broadcast.source.event1("param")

        then:
        1 * dispatch1.dispatch(invocation)
        then:
        1 * dispatch2.dispatch(invocation)
        0 * _._
    }

    def 'visit listeners does not visit dispatch instances'() {
        def dispatch = Mock(Dispatch)
        def visitor = Mock(Action)

        given:
        broadcast.add(dispatch)

        when:
        broadcast.visitListeners(visitor)

        then:
        0 * visitor._
    }

    def 'dispatch is not used after it is removed'() {
        given:
        Dispatch<MethodInvocation> dispatch = Mock()
        broadcast.add(dispatch)
        broadcast.remove(dispatch)

        when:
        broadcast.source.event1("param")

        then:
        0 * _._
    }

    def 'can use Action for single event method'() {
        given:
        Action<String> action = Mock()
        broadcast.add("event1", action)

        when:
        broadcast.source.event1("param")

        then:
        1 * action.execute("param")
        0 * _._
    }

    def 'does not notify Action for other event methods'() {
        given:
        Action<String> action = Mock()
        broadcast.add("event1", action)

        when:
        broadcast.source.event2(9, "param")

        then:
        0 * _._
    }

    def 'the Action can have fewer parameters than the event method'() {
        given:
        Action<Integer> action = Mock()
        broadcast.add("event2", action)

        when:
        broadcast.source.event2(1, "param")

        then:
        1 * action.execute(1)

        when:
        broadcast.source.event2(2, null)

        then:
        1 * action.execute(2)
        0 * _._
    }

    def 'listener can add another listener'() {
        given:
        TestListener listener1 = Mock()
        TestListener listener2 = Mock()
        TestListener listener3 = Mock()
        broadcast.add(listener1)
        broadcast.add(listener2)

        when:
        broadcast.source.event1("event")

        then:
        1 * listener1.event1("event") >> { args ->
            broadcast.add(listener3)
        }
        1 * listener2.event1("event")
        0 * _._

        when:
        broadcast.source.event1("param")

        then:
        1 * listener1.event1("param")
        1 * listener2.event1("param")
        1 * listener3.event1("param")
        0 * _._
    }

    def 'wraps checked exception thrown by listener'() {
        given:
        TestListener listener = Mock()
        Exception failure = new Exception()
        broadcast.add(listener)

        when:
        broadcast.source.event3()

        then:
        1 * listener.event3() >> { throw failure }
        0 * _._
        ListenerNotificationException exception = thrown()
        exception.message == 'Failed to notify test listener.'
        exception.cause.is(failure)
    }

    def 'attempts to notify all other listeners when one throws exception'() {
        given:
        TestListener listener1 = Mock()
        TestListener listener2 = Mock()
        Exception failure = new RuntimeException()
        broadcast.add(listener1)
        broadcast.add(listener2)

        when:
        broadcast.source.event1("param")

        then:
        1 * listener1.event1("param") >> { throw failure }
        1 * listener2.event1("param")
        0 * _._
        RuntimeException exception = thrown()
        exception.is(failure)

    }

    def 'attempts to notify all other listeners when multiple exceptions are thrown'() {
        TestListener listener1 = Mock()
        TestListener listener2 = Mock()
        TestListener listener3 = Mock()
        Exception failure1 = new RuntimeException()
        Exception failure2 = new RuntimeException()
        broadcast.add(listener1)
        broadcast.add(listener2)
        broadcast.add(listener3)

        when:
        broadcast.source.event1("param")

        then:
        1 * listener1.event1("param") >> { throw failure1 }
        1 * listener2.event1("param") >> { throw failure2 }
        1 * listener3.event1("param")
        0 * _._

        ListenerNotificationException exception = thrown()
        exception.causes == [failure1, failure2]
    }

    def 'can query the number of registered listeners'() {
        TestListener listener1 = Mock()
        TestListener listener2 = Mock()
        TestListener listener3 = Mock()

        expect:
        broadcast.empty
        broadcast.size() == 0

        when:
        broadcast.add(listener1)

        then:
        !broadcast.empty
        broadcast.size() == 1

        when:
        broadcast.add(listener2)
        broadcast.add(listener3)

        then:
        !broadcast.empty
        broadcast.size() == 3

        when:
        broadcast.remove(listener1)

        then:
        !broadcast.empty
        broadcast.size() == 2

        when:
        broadcast.remove(listener2)

        then:
        !broadcast.empty
        broadcast.size() == 1

        when:
        broadcast.remove(listener3)

        then:
        broadcast.empty
        broadcast.size() == 0
    }

    interface TestListener {
        void event1(String param)

        void event2(int value, String other)

        void event3() throws Exception
    }
}
