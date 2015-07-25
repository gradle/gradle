/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.CopyOnWriteArrayList

class DefaultListenerManagerTest extends ConcurrentSpec {
    def manager = new DefaultListenerManager();

    def fooListener1 = Mock(TestFooListener.class)
    def fooListener2 = Mock(TestFooListener.class)
    def fooListener3 = Mock(TestFooListener.class)
    def barListener1 = Mock(TestBarListener.class)

    def broadcasterDoesNothingWhenNoListenersRegistered() {
        given:
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param");

        then:
        0 * _
    }

    def cachesBroadcasters() {
        expect:
        manager.getBroadcaster(TestFooListener.class).is(manager.getBroadcaster(TestFooListener.class))
    }

    def canAddListenerBeforeObtainingBroadcaster() {
        given:
        manager.addListener(fooListener1);
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param");

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def canAddListenerAfterObtainingBroadcaster() {
        given:
        def broadcaster = manager.getBroadcaster(TestFooListener.class)
        manager.addListener(fooListener1);

        when:
        broadcaster.foo("param");

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def canAddLoggerBeforeObtainingBroadcaster() {
        given:
        manager.useLogger(fooListener1)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param");

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def canAddLoggerAfterObtainingBroadcaster() {
        given:
        manager.useLogger(fooListener1)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param");

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def canHaveMultipleListenerTypes() {
        given:
        manager.addListener(fooListener1)
        manager.addListener(barListener1)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _

        when:
        manager.getBroadcaster(TestBarListener.class).bar(12)

        then:
        1 * barListener1.bar(12)
        0 * _
    }

    def addedListenersGetMessagesInOrderAdded() {
        given:
        manager.addListener(fooListener1)
        manager.addListener(fooListener2)

        // get the broadcaster and then add more listeners (because broadcasters
        // are cached and so must be maintained correctly after getting defined
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        manager.addListener(fooListener3)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener1.foo("param")

        then:
        1 * fooListener2.foo("param")

        then:
        1 * fooListener3.foo("param")
        0 * _
    }

    def loggersReceiveMessagesBeforeListeners() {
        given:
        manager.addListener(fooListener1)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)
        manager.useLogger(fooListener2)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener2.foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def listenersReceiveMessagesInSameOrderRegardlessOfGeneratingThread() {
        given:
        def received1 = new CopyOnWriteArrayList<String>()
        def received2 = new CopyOnWriteArrayList<String>()
        def listener1 = { String p ->
            received1 << p
        } as TestFooListener
        def listener2 = { String p ->
            received2 << p
        } as TestFooListener

        manager.addListener(listener1)
        manager.addListener(listener2)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        async {
            start {
                broadcaster.foo("a-1")
                broadcaster.foo("a-2")
                broadcaster.foo("a-3")
                broadcaster.foo("a-4")
                broadcaster.foo("a-5")
            }
            start {
                broadcaster.foo("b-1")
                broadcaster.foo("b-2")
                broadcaster.foo("b-3")
                broadcaster.foo("b-4")
                broadcaster.foo("b-5")
            }
        }

        then:
        received1.size() == 10
        received2.size() == 10
        received1 == received2
        received1.findAll { it.startsWith("a") } == ["a-1", "a-2", "a-3", "a-4", "a-5"]
        received1.findAll { it.startsWith("b") } == ["b-1", "b-2", "b-3", "b-4", "b-5"]
    }

    def threadBlocksWhenAnotherThreadIsNotifyingOnTheSameType() {
        given:
        def listener1 = { String p ->
            if (p == "a") {
                instant.aReceived
                thread.block()
                instant.aHandled
            } else {
                instant.bReceived
            }
        } as TestFooListener

        manager.addListener(listener1)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        async {
            start {
                broadcaster.foo("a")
            }
            start {
                thread.blockUntil.aReceived
                broadcaster.foo("b")
            }
        }

        then:
        instant.bReceived > instant.aHandled
    }

    def threadDoesNotBlockWhenAnotherThreadIsNotifyingOnDifferentType() {
        given:
        def listener1 = { String p ->
            instant.aReceived
            thread.block()
            instant.aHandled
        } as TestFooListener
        def listener2 = {
            instant.bReceived
        } as TestBarListener

        manager.addListener(listener1)
        manager.addListener(listener2)
        def broadcaster1 = manager.getBroadcaster(TestFooListener.class)
        def broadcaster2 = manager.getBroadcaster(TestBarListener.class)

        when:
        async {
            start {
                broadcaster1.foo("a")
            }
            start {
                thread.blockUntil.aReceived
                broadcaster2.bar(12)
            }
        }

        then:
        instant.bReceived < instant.aHandled
    }

    def removedListenersDontGetMessages() {
        given:
        manager.addListener(fooListener1)
        manager.addListener(fooListener2)
        manager.removeListener(fooListener2)
        def testFooListener = manager.getBroadcaster(TestFooListener.class)
        manager.removeListener(fooListener1)

        when:
        testFooListener.foo("param")

        then:
        0 * _
    }

    def replacedLoggersDontGetMessages() {
        given:
        manager.useLogger(fooListener1)
        manager.useLogger(fooListener2)
        def testFooListener = manager.getBroadcaster(TestFooListener.class)
        manager.useLogger(fooListener3)

        when:
        testFooListener.foo("param")

        then:
        1 * fooListener3.foo("param")
        0 * _
    }

    def collectsFailureAndContinuesToNotifyListeners() {
        given:
        def failure = new RuntimeException()
        manager.addListener(fooListener1)
        manager.addListener(fooListener2)
        def testFooListener = manager.getBroadcaster(TestFooListener.class)

        when:
        testFooListener.foo("param")

        then:
        1 * fooListener1.foo("param") >> { throw failure }
        1 * fooListener2.foo("param")
        0 * _

        and:
        RuntimeException e = thrown()
        e == failure
    }

    def collectsMultipleFailuresAndContinuesToNotifyListeners() {
        given:
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()
        manager.addListener(fooListener1)
        manager.addListener(fooListener2)
        manager.addListener(fooListener3)
        def testFooListener = manager.getBroadcaster(TestFooListener.class)

        when:
        testFooListener.foo("param")

        then:
        1 * fooListener1.foo("param") >> { throw failure1 }
        1 * fooListener2.foo("param") >> { throw failure2 }
        1 * fooListener3.foo("param")
        0 * _

        and:
        ListenerNotificationException e = thrown()
        e.causes == [failure1, failure2]
    }

    def listenerReceivesEventsFromAnonymousBroadcasters() {
        given:
        manager.addListener(fooListener1)
        def broadcaster = manager.createAnonymousBroadcaster(TestFooListener.class)

        when:
        broadcaster.source.foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def listenerOnAnonymousBroadcasterDoesNotReceiveEventsFromListenerManager() {
        given:
        manager.createAnonymousBroadcaster(TestFooListener.class).add(fooListener1)

        when:
        manager.getBroadcaster(TestFooListener).foo("param")

        then:
        0 * _
    }

    def listenerReceivesEventsFromChildren() {
        given:
        manager.addListener(fooListener1)
        def child = manager.createChild()
        child.addListener(fooListener2)
        def broadcaster = child.getBroadcaster(TestFooListener.class)
        manager.addListener(fooListener3)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener1.foo("param")
        1 * fooListener2.foo("param")
        1 * fooListener3.foo("param")
        0 * _
    }

    def listenerDoesNotReceiveEventsFromParent() {
        given:
        manager.createChild().addListener(fooListener1)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        0 * _
    }

    def loggerReceivesEventsFromChildren() {
        given:
        manager.useLogger(fooListener1)
        def child = manager.createChild();
        def broadcaster = child.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _

        when:
        manager.useLogger(fooListener2) // replace listener
        broadcaster.foo("param")

        then:
        1 * fooListener2.foo("param")
        0 * _
    }

    def loggerDoesNotReceiveEventsFromParent() {
        given:
        manager.createChild().useLogger(fooListener1)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        0 * _
    }

    def loggerInChildHasPrecedenceOverLoggerInParent() {
        given:
        manager.useLogger(fooListener1)
        def child = manager.createChild()
        def broadcaster = child.getBroadcaster(TestFooListener.class)
        child.useLogger(fooListener2)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener2.foo("param")
        0 * _

        when:
        child.useLogger(fooListener3)
        broadcaster.foo("param2")

        then:
        1 * fooListener3.foo("param2")
        0 * _
    }

    public interface TestFooListener {
        void foo(String param);
    }

    public interface TestBarListener {
        void bar(int value);
    }
}
