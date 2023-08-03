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

package org.gradle.internal.event

import org.gradle.api.Action
import org.gradle.internal.dispatch.Dispatch
import org.gradle.internal.dispatch.MethodInvocation
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class BroadcastDispatchTest extends Specification {
    def method = TestListener.getMethod("doSomething", String)

    def "does nothing when no listeners"() {
        expect:
        def dispatch = BroadcastDispatch.empty(TestListener)
        dispatch.empty
        dispatch.size() == 0
        dispatch.dispatch(new MethodInvocation(method, ["param"] as Object[]))
    }

    def "can add a dispatch listener"() {
        def listener = Mock(Dispatch)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add(listener)
        !dispatch.empty
        dispatch.size() == 1
        dispatch.dispatch(invocation)

        then:
        1 * listener.dispatch(invocation)
        0 * _
    }

    def "can add a typed listener"() {
        def listener = Mock(TestListener)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add(listener)
        dispatch.dispatch(invocation)

        then:
        1 * listener.doSomething("param")
        0 * _
    }

    def "can add an action listener"() {
        def listener = Mock(Action)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add("doSomething", listener)
        dispatch.dispatch(invocation)

        then:
        1 * listener.execute("param")
        0 * _
    }

    def "can remove a dispatch listener"() {
        def listener1 = Mock(Dispatch)
        def listener2 = Mock(Dispatch)
        def listener3 = Mock(Dispatch)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def original = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2).add(listener3)
        def dispatch = original.remove(listener1)
        dispatch.dispatch(invocation)

        then:
        1 * listener2.dispatch(invocation)
        1 * listener3.dispatch(invocation)
        0 * _

        when:
        dispatch = dispatch.remove(listener3)
        dispatch.dispatch(invocation)

        then:
        1 * listener2.dispatch(invocation)
        0 * _

        when:
        dispatch = dispatch.remove(listener2)
        dispatch.dispatch(invocation)

        then:
        0 * _
    }

    def "can remove a typed listener"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(Dispatch)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def original = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2)
        def dispatch = original.remove(listener1)
        dispatch.dispatch(invocation)

        then:
        1 * listener2.dispatch(invocation)
        0 * _
    }

    def "can remove an action listener"() {
        def listener1 = Mock(Action)
        def listener2 = Mock(Dispatch)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def original = BroadcastDispatch.empty(TestListener).add("doSomething", listener1).add(listener2)
        def dispatch = original.remove(listener1)
        dispatch.dispatch(invocation)

        then:
        1 * listener2.dispatch(invocation)
        0 * _
    }

    def "does nothing when listener already included"() {
        def listener1 = Mock(Dispatch)
        def listener2 = Mock(Dispatch)
        def listener3 = Mock(Dispatch)

        expect:
        def dispatch1 = BroadcastDispatch.empty(TestListener).add(listener1)
        dispatch1.add(listener1).is(dispatch1)

        def dispatch2 = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2).add(listener3)
        dispatch2.add(listener1).is(dispatch2)
        dispatch2.add(listener3).is(dispatch2)
    }

    def "does nothing when unknown listener removed"() {
        def listener1 = Mock(Dispatch)
        def listener2 = Mock(Dispatch)
        def listener3 = Mock(Dispatch)

        expect:
        def dispatch1 = BroadcastDispatch.empty(TestListener)
        dispatch1.remove(listener1).is(dispatch1)

        def dispatch2 = BroadcastDispatch.empty(TestListener).add(listener1)
        dispatch2.remove(listener2).is(dispatch2)

        def dispatch3 = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2)
        dispatch3.remove(listener3).is(dispatch3)
    }

    def "can add multiple listeners"() {
        def listener1 = Mock(Dispatch)
        def listener2 = Mock(Dispatch)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2)
        !dispatch.empty
        dispatch.size() == 2
        dispatch.dispatch(invocation)

        then:
        1 * listener1.dispatch(invocation)

        then:
        1 * listener2.dispatch(invocation)
        0 * _
    }

    def "can add multiple listeners as a batch"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        def listener3 = Mock(TestListener)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).addAll([listener1, listener2])
        !dispatch.empty
        dispatch.size() == 2
        dispatch.dispatch(invocation)

        then:
        1 * listener1.doSomething("param")
        1 * listener2.doSomething("param")
        0 * _

        when:
        dispatch = BroadcastDispatch.empty(TestListener).add(listener1).addAll([listener2, listener3])
        !dispatch.empty
        dispatch.size() == 3
        dispatch.dispatch(invocation)

        then:
        1 * listener1.doSomething("param")
        1 * listener2.doSomething("param")
        1 * listener3.doSomething("param")
        0 * _

        when:
        dispatch = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2).addAll([listener3])
        !dispatch.empty
        dispatch.size() == 3
        dispatch.dispatch(invocation)

        then:
        1 * listener1.doSomething("param")
        1 * listener2.doSomething("param")
        1 * listener3.doSomething("param")
        0 * _
    }

    def "removes duplicates when adding listeners as a batch"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        def listener3 = Mock(TestListener)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).addAll([listener1, listener1])
        !dispatch.empty
        dispatch.size() == 1
        dispatch.dispatch(invocation)

        then:
        1 * listener1.doSomething("param")
        0 * _

        when:
        dispatch = dispatch.addAll([listener2, listener1, listener2])
        !dispatch.empty
        dispatch.size() == 2
        dispatch.dispatch(invocation)

        then:
        1 * listener1.doSomething("param")
        1 * listener2.doSomething("param")
        0 * _

        when:
        dispatch = dispatch.addAll([listener3, listener2, listener3])
        !dispatch.empty
        dispatch.size() == 3
        dispatch.dispatch(invocation)

        then:
        1 * listener1.doSomething("param")
        1 * listener2.doSomething("param")
        1 * listener3.doSomething("param")
        0 * _
    }

    def "does nothing when adding a batch of listeners that are already included"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)

        expect:
        def dispatch1 = BroadcastDispatch.empty(TestListener)
        dispatch1.addAll([]).is(dispatch1)

        def dispatch2 = dispatch1.add(listener1)
        dispatch2.addAll([listener1, listener1]).is(dispatch2)

        def dispatch3 = dispatch2.add(listener2)
        dispatch3.addAll([listener2, listener1]).is(dispatch3)
    }

    def "can remove multiple listeners as a batch"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch1 = BroadcastDispatch.empty(TestListener).add(listener1)
        def dispatch2 = dispatch1.add(listener2)

        dispatch1 = dispatch1.removeAll([listener1])
        dispatch1.dispatch(invocation)

        dispatch2 = dispatch2.removeAll([listener1, listener2])
        dispatch2.dispatch(invocation)

        then:
        0 * _
    }

    def "does nothing when removing a batch of unknown listeners"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        def listener3 = Mock(TestListener)

        expect:
        def dispatch1 = BroadcastDispatch.empty(TestListener)
        dispatch1.removeAll([]).is(dispatch1)
        dispatch1.removeAll([listener2, listener3]).is(dispatch1)

        def dispatch2 = dispatch1.add(listener1)
        dispatch2.removeAll([]).is(dispatch2)
        dispatch2.removeAll([listener2, listener3]).is(dispatch2)

        def dispatch3 = dispatch2.add(listener2)
        dispatch3.removeAll([]).is(dispatch3)
        dispatch3.removeAll([listener3]).is(dispatch3)
    }

    def "propagates single unchecked failure"() {
        def listener1 = Mock(Dispatch)
        def listener2 = Mock(Dispatch)
        def listener3 = Mock(Dispatch)
        def failure = new RuntimeException()
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2).add(listener3)
        dispatch.dispatch(invocation)

        then:
        def e = thrown(RuntimeException)
        e == failure

        1 * listener1.dispatch(invocation)
        1 * listener2.dispatch(invocation) >> { throw failure }
        1 * listener3.dispatch(invocation)
        0 * _
    }

    def "propagates single checked failure"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        def listener3 = Mock(TestListener)
        def failure = new Exception()
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2).add(listener3)
        dispatch.dispatch(invocation)

        then:
        def e = thrown(ListenerNotificationException)
        e.cause == failure

        1 * listener1.doSomething("param")
        1 * listener2.doSomething("param") >> { throw failure }
        1 * listener3.doSomething("param")
        0 * _
    }

    def "listener can throw ListenerNotificationException"() {
        def listener1 = Mock(TestListener)
        def listener2 = Mock(TestListener)
        def listener3 = Mock(TestListener)
        def failure = new ListenerNotificationException(null, "not a wrapper", [])
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2).add(listener3)
        dispatch.dispatch(invocation)

        then:
        def e = thrown(ListenerNotificationException)
        e.is(failure)

        1 * listener1.doSomething("param")
        1 * listener2.doSomething("param") >> { throw failure }
        1 * listener3.doSomething("param")
        0 * _
    }

    def "propagates multiple failures"() {
        def listener1 = Mock(Dispatch)
        def listener2 = Mock(TestListener)
        def listener3 = Mock(Dispatch)
        def failure1 = new RuntimeException()
        def failure2 = new Exception()
        def invocation = new MethodInvocation(method, ["param"] as Object[])

        when:
        def dispatch = BroadcastDispatch.empty(TestListener).add(listener1).add(listener2).add(listener3)
        dispatch.dispatch(invocation)

        then:
        def e = thrown(ListenerNotificationException)
        e.causes == [failure1, failure2]

        1 * listener1.dispatch(invocation) >> { throw failure1 }
        1 * listener2.doSomething("param") >> { throw failure2 }
        1 * listener3.dispatch(invocation)
        0 * _
    }

    def "can remove listener implemented using Proxy that does not implement equals()"() {
        TestListener other1 = Stub(TestListener)
        TestListener other2 = Stub(TestListener)
        TestListener listener = Proxy.newProxyInstance(getClass().classLoader, [TestListener] as Class[], new InvocationHandler() {
            @Override
            Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.name == "equals") {
                    return false
                }
                if (method.name == "hashCode") {
                    return this.hashCode()
                }
                if (method.name == "toString") {
                    return "<proxy>"
                }
                return null;
            }
        })

        expect:
        def empty = BroadcastDispatch.empty(TestListener)

        // Remove
        empty.add(listener).remove(listener).empty
        empty.add(listener).removeAll([listener, listener]).empty
        empty.add(other1).add(listener).remove(listener).remove(other1).empty
        empty.add(other1).add(listener).removeAll([listener, listener]).remove(other1).empty
        empty.add(other1).add(other2).add(listener).remove(listener).removeAll([other1, other2]).empty
        empty.add(other1).add(other2).add(listener).removeAll([listener]).removeAll([other1, other2]).empty

        // Add duplicates
        empty.addAll([listener, listener]).remove(listener).empty
        empty.add(other1).addAll([listener, listener]).remove(listener).remove(other1).empty
        empty.add(other1).add(other2).addAll([listener, listener]).remove(listener).removeAll([other1, other2]).empty

        // Add existing
        def b1 = empty.add(listener)
        b1.add(listener).is(b1)
        b1.addAll([listener, listener]).is(b1)
        def b2 = empty.add(other1).add(listener)
        b2.add(listener).is(b2)
        b2.addAll([listener, listener]).is(b2)
    }

    interface TestListener {
        void doSomething(String param) throws Exception
    }
}
