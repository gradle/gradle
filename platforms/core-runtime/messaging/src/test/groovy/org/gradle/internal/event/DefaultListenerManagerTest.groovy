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

import com.google.common.reflect.ClassPath
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.ParallelListener
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.StatefulListener
import org.gradle.test.fixtures.concurrent.ConcurrentSpec
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Timeout

import java.util.concurrent.CopyOnWriteArrayList

@Timeout(60)
class DefaultListenerManagerTest extends ConcurrentSpec {
    def manager = new DefaultListenerManager(Scope.BuildTree)

    def fooListener1 = Mock(TestFooListener.class)
    def fooListener2 = Mock(TestFooListener.class)
    def fooListener3 = Mock(TestFooListener.class)
    def barListener1 = Mock(TestBarListener.class)

    def broadcasterDoesNothingWhenNoListenersRegistered() {
        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")
        manager.createChild(Scope.Build).getBroadcaster(BuildScopeListener.class).foo("param")
        manager.createChild(Scope.Build).createAnonymousBroadcaster(BuildScopeListener.class).source.foo("param")

        then:
        0 * _
    }

    def cachesBroadcasters() {
        expect:
        manager.getBroadcaster(TestFooListener.class).is(manager.getBroadcaster(TestFooListener.class))
    }

    def canAddListenerBeforeObtainingBroadcaster() {
        given:
        manager.addListener(fooListener1)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def canAddListenerAfterObtainingBroadcaster() {
        given:
        def broadcaster = manager.getBroadcaster(TestFooListener.class)
        manager.addListener(fooListener1)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def canAddLoggerBeforeObtainingBroadcaster() {
        given:
        manager.useLogger(fooListener1)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def "cannot use listener type that does not have @EventScope annotation"() {
        when:
        manager.getBroadcaster(Runnable.class)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Listener type java.lang.Runnable is not annotated with @EventScope.'
    }

    def "cannot use listener type with mismatched scope"() {
        when:
        manager.getBroadcaster(TestListenerWithWrongScope.class)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Listener type ${TestListenerWithWrongScope.name} with scope Global cannot be used to generate events in scope BuildTree."
    }

    def canAddLoggerAfterObtainingBroadcaster() {
        given:
        manager.useLogger(fooListener1)
        def broadcaster = manager.getBroadcaster(TestFooListener.class)

        when:
        broadcaster.foo("param")

        then:
        1 * fooListener1.foo("param")
        0 * _
    }

    def canHaveListenersOfDifferentTypes() {
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

    def listenerCanImplementMultipleTypes() {
        given:
        def listener = Mock(BothListener)
        manager.addListener(listener)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        1 * listener.foo("param")
        0 * _

        when:
        manager.getBroadcaster(TestBarListener.class).bar(12)

        then:
        1 * listener.bar(12)
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
        def events1 = events("a", 20)
        def events2 = events("b", 20)
        def events3 = events("c", 20)
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
                events1.each {
                    broadcaster.foo(it)
                }
            }
            start {
                events2.each {
                    broadcaster.foo(it)
                }
            }
            start {
                events3.each {
                    broadcaster.foo(it)
                }
            }
        }

        then:
        received1.size() == 60
        received2.size() == 60
        received1 == received2
        received1.findAll { it.startsWith("a") } == events1
        received1.findAll { it.startsWith("b") } == events2
    }

    List<String> events(String prefix, int count) {
        return (1..count).collect { i -> "$prefix-$i" as String }
    }

    def notifyBlocksWhenAnotherThreadIsNotifyingOnTheSameType() {
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

    def notifyDoesntBlockWhenAnotherThreadIsNotifyingOnTheSameNonThreadSafeType() {
        given:
        ParallelTestListener listener1 = new ParallelTestListener() {
            @Override
            void something(String p) {
                if (p == "a") {
                    instant.aReceived
                    thread.block()
                    instant.aHandled
                } else {
                    instant.bReceived
                }
            }
        }

        manager.addListener(listener1)
        def broadcaster = manager.getBroadcaster(StatefulTestListener.class)

        when:
        async {
            start {
                broadcaster.something("a")
            }
            start {
                thread.blockUntil.aReceived
                broadcaster.something("b")
            }
        }

        then:
        instant.bReceived < instant.aHandled
    }

    def notifyDoesNotBlockWhenAnotherThreadIsNotifyingOnDifferentType() {
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

    def notifyBlocksWhenAnotherThreadIsNotifyingTheSameListenerWithDifferentType() {
        given:
        def listener = [foo: { String p ->
            instant.aReceived
            thread.block()
            instant.aHandled
        },
                        bar: { int i ->
                            instant.bReceived
                        }
        ] as BothListener

        manager.addListener(listener)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("a")
            }
            start {
                thread.blockUntil.aReceived
                manager.getBroadcaster(TestBarListener.class).bar(12)
            }
        }

        then:
        instant.bReceived > instant.aHandled
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

    def collectsMultipleFailuresFromParent() {
        given:
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()
        def failure3 = new RuntimeException()
        def listener1 = Mock(BuildScopeListener)
        def listener2 = Mock(BuildScopeListener)
        def listener3 = Mock(BuildScopeListener)
        manager.addListener(listener1)
        manager.addListener(listener2)
        def child = manager.createChild(Scope.Build)
        child.addListener(listener3)
        def broadcast = child.getBroadcaster(BuildScopeListener.class)

        when:
        broadcast.foo("param")

        then:
        1 * listener1.foo("param") >> { throw failure1 }
        1 * listener2.foo("param") >> { throw failure2 }
        1 * listener3.foo("param") >> { throw failure3 }
        0 * _

        and:
        ListenerNotificationException e = thrown()
        e.causes == [failure1, failure2, failure3]
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

    def anonymousBroadcasterCollectsMultipleFailures() {
        given:
        def failure1 = new RuntimeException()
        def failure2 = new RuntimeException()
        def failure3 = new RuntimeException()
        def listener1 = Mock(BuildScopeListener)
        def listener2 = Mock(BuildScopeListener)
        def listener3 = Mock(BuildScopeListener)
        manager.addListener(listener1)
        def child = manager.createChild(Scope.Build)
        child.addListener(listener2)
        def broadcast = child.createAnonymousBroadcaster(BuildScopeListener.class)
        broadcast.add(listener3)
        def broadcaster = broadcast.getSource()

        when:
        broadcaster.foo("param")

        then:
        1 * listener1.foo("param") >> { throw failure1 }
        1 * listener2.foo("param") >> { throw failure2 }
        1 * listener3.foo("param") >> { throw failure3 }
        0 * _

        and:
        ListenerNotificationException e = thrown()
        e.causes == [failure1, failure2, failure3]
    }

    def listenerReceivesEventsFromChildren() {
        given:
        def listener1 = Mock(BuildScopeListener)
        def listener2 = Mock(BuildScopeListener)
        def listener3 = Mock(BuildScopeListener)
        manager.addListener(listener1)
        def child = manager.createChild(Scope.Build)
        child.addListener(listener2)
        def broadcaster = child.getBroadcaster(BuildScopeListener.class)
        manager.addListener(listener3)

        when:
        broadcaster.foo("param")

        then:
        1 * listener1.foo("param")
        1 * listener2.foo("param")
        1 * listener3.foo("param")
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
        def listener1 = Mock(BuildScopeListener)
        def listener2 = Mock(BuildScopeListener)
        manager.useLogger(listener1)
        def child = manager.createChild(Scope.Build)
        def broadcaster = child.getBroadcaster(BuildScopeListener.class)

        when:
        broadcaster.foo("param")

        then:
        1 * listener1.foo("param")
        0 * _

        when:
        manager.useLogger(listener2) // replace listener
        broadcaster.foo("param")

        then:
        1 * listener2.foo("param")
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
        def listener1 = Mock(BuildScopeListener)
        def listener2 = Mock(BuildScopeListener)
        def listener3 = Mock(BuildScopeListener)
        manager.useLogger(listener1)
        def child = manager.createChild(Scope.Build)
        def broadcaster = child.getBroadcaster(BuildScopeListener.class)
        child.useLogger(listener2)

        when:
        broadcaster.foo("param")

        then:
        1 * listener2.foo("param")
        0 * _

        when:
        child.useLogger(listener3)
        broadcaster.foo("param2")

        then:
        1 * listener3.foo("param2")
        0 * _
    }

    def listenerCanAddAnotherListenerOfSameType() {
        given:
        manager.addListener(fooListener1)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        1 * fooListener1.foo("param") >> {
            manager.addListener(fooListener2)
        }
        0 * _

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param 2")

        then:
        1 * fooListener1.foo("param 2")
        1 * fooListener2.foo("param 2")
    }

    def listenerCanAddAnotherListenerOfDifferentType() {
        given:
        manager.addListener(fooListener1)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        1 * fooListener1.foo("param") >> {
            manager.addListener(barListener1)
            manager.getBroadcaster(TestBarListener.class).bar(12)
        }
        1 * barListener1.bar(12)
        0 * _
    }

    def listenerCanRemoveAnotherListener() {
        given:
        manager.addListener(fooListener1)
        manager.addListener(fooListener2)
        manager.addListener(fooListener3)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        1 * fooListener1.foo("param")
        1 * fooListener2.foo("param") >> {
            manager.removeListener(fooListener1)
            manager.removeListener(fooListener3)
        }
        0 * _
    }

    def listenerCannotGenerateEventsOfSameType() {
        given:
        manager.addListener(fooListener1)
        manager.addListener(barListener1)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        IllegalStateException e = thrown()
        e.message == "Cannot notify listeners of type TestFooListener as these listeners are already being notified."

        and:
        1 * fooListener1.foo("param") >> {
            manager.getBroadcaster(TestFooListener.class).foo("param2")
        }
        0 * _

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        IllegalStateException e2 = thrown()
        e2.message == "Cannot notify listeners of type TestFooListener as these listeners are already being notified."

        and:
        1 * fooListener1.foo("param") >> {
            manager.getBroadcaster(TestBarListener.class).bar(12)
        }
        1 * barListener1.bar(12) >> {
            manager.getBroadcaster(TestFooListener.class).foo("param 2")
        }
        0 * _
    }

    def listenerCanGenerateEventsOfDifferentType() {
        given:
        manager.addListener(fooListener1)
        manager.addListener(barListener1)

        when:
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        1 * fooListener1.foo("param") >> {
            manager.getBroadcaster(TestBarListener.class).bar(12)
        }
        1 * barListener1.bar(12)
        0 * _
    }

    def multipleThreadsCanAddListeners() {
        when:
        async {
            start {
                manager.addListener(fooListener1)
            }
            start {
                manager.addListener(fooListener2)
            }
            start {
                manager.addListener(fooListener3)
            }
        }
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        1 * fooListener1.foo("param")
        1 * fooListener2.foo("param")
        1 * fooListener3.foo("param")
        0 * _
    }

    def multipleThreadsCanRemoveListeners() {
        when:
        async {
            start {
                manager.addListener(fooListener1)
                manager.removeListener(fooListener1)
            }
            start {
                manager.addListener(fooListener2)
                manager.removeListener(fooListener2)
            }
            start {
                manager.addListener(fooListener3)
                manager.removeListener(fooListener3)
            }
        }
        manager.getBroadcaster(TestFooListener.class).foo("param")

        then:
        0 * _
    }

    def "can remove a listener which tries to add another listener"() {
        given:
        def listener1 = {
            sleep 100
            manager.addListener(barListener1)
        } as TestFooListener
        def listener2 = {
            sleep 20
            manager.removeListener(listener1)
        } as TestBarListener
        manager.addListener(listener1)
        manager.addListener(listener2)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("param")
            }
            start {
                manager.getBroadcaster(TestBarListener.class).bar(1)
            }
        }
        then:
        0 * _
    }

    def "can remove a listener which tries to notify another broadcaster"() {
        given:
        def listener1 = {
            sleep 1000
            // Try to get broadcaster, should not block
            def broadcaster = manager.getBroadcaster(TestBazListener)
            // Notify broadcaster, should not block
            broadcaster.baz()
        } as TestFooListener
        def listener2 = {
            sleep 20
            // Try to remove listener, should be blocked until listener 1 is done
            manager.removeListener(listener1)
        } as TestBarListener
        manager.addListener(listener1)
        manager.addListener(listener2)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("param")
            }
            start {
                manager.getBroadcaster(TestBarListener.class).bar(1)
            }
        }
        then:
        0 * _
    }

    @Ignore("This test highlights a very peculiar use case which is not supported yet")
    def "can remove a listener which tries to notify a broadcaster itself trying to notify the same listener"() {
        given:
        def listener1 = {
            sleep 1000
            // Try to get broadcaster, should not block
            def broadcaster = manager.getBroadcaster(TestBarListener)
            // Notify broadcaster, should not block
            broadcaster.bar(1) // today, deadlocks here
        } as TestFooListener
        def listener2 = {
            sleep 20
            // Try to remove listener, should be blocked until listener 1 is done
            manager.removeListener(listener1)
        } as TestBarListener
        manager.addListener(listener1)
        manager.addListener(listener2)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("param")
            }
            start {
                manager.getBroadcaster(TestBarListener.class).bar(1)
            }
        }
        then:
        0 * _
    }

    def addingListenerDoesNotBlockWhileAnotherThreadIsNotifying() {
        given:
        def listener1 = {
            instant.received
            thread.block()
            instant.handled
        } as TestFooListener
        manager.addListener(listener1)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("param")
            }
            thread.blockUntil.received
            manager.addListener(fooListener2)
            instant.added
        }

        then:
        instant.added < instant.handled

        and:
        0 * _
    }

    def addingListenerDoesNotBlockWhileAnotherThreadIsNotifyingOnDifferentType() {
        given:
        def listener1 = {
            instant.received
            thread.block()
            instant.handled
        } as TestFooListener
        manager.addListener(listener1)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("param")
            }
            thread.blockUntil.received
            manager.addListener(barListener1)
            instant.added
        }

        then:
        instant.added < instant.handled

        and:
        0 * _
    }

    def removingListenerBlocksWhileAnotherThreadIsNotifyingListener() {
        given:
        def listener1 = {
            instant.received
            thread.block()
            instant.handled
        } as TestFooListener
        manager.addListener(listener1)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("param")
            }
            thread.blockUntil.received
            manager.removeListener(listener1)
            instant.removed
        }

        then:
        instant.removed > instant.handled
    }

    def removingListenerDoesNotBlockWhileAnotherThreadIsNotifyingOnDifferentType() {
        given:
        def listener1 = {
            instant.received
            thread.block()
            instant.handled
        } as TestFooListener
        manager.addListener(listener1)
        manager.addListener(barListener1)

        when:
        async {
            start {
                manager.getBroadcaster(TestFooListener.class).foo("param")
            }
            thread.blockUntil.received
            manager.removeListener(barListener1)
            instant.removed
        }

        then:
        instant.removed < instant.handled
    }

    @Issue('https://github.com/gradle/gradle-private/issues/1031')
    def concurrentAccessIsSafe() {
        when:
        List<Class> manyClasses = ClassPath.from(Thread.currentThread().getContextClassLoader()).getTopLevelClassesRecursive('java.util')*.load().findAll { it.isInterface() }

        start {
            manyClasses.each {
                manager.getBroadcaster(it)
            }
        }
        start {
            10000.times {
                manager.addListener(new Object())
                manager.removeListener(new Object())
            }
        }

        then:
        executor.failure == null
    }

    def "cannot add a stateful listener after an event has been broadcast"() {
        given:
        manager.addListener(Stub(StatefulTestListener))
        def broadcaster = manager.getBroadcaster(StatefulTestListener)
        manager.addListener(Stub(StatefulTestListener))
        broadcaster.something("12")

        when:
        manager.addListener(Stub(StatefulTestListener))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add listener of type StatefulTestListener after events have been broadcast.'
    }

    def "cannot add a stateful listener after events have been broadcast by a child"() {
        given:
        manager.addListener(Stub(StatefulTestListener))
        def broadcaster = manager.createChild(Scope.BuildTree).getBroadcaster(StatefulTestListener)
        broadcaster.something("12")

        when:
        manager.addListener(Stub(StatefulTestListener))

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Cannot add listener of type StatefulTestListener after events have been broadcast.'
    }

    def "can query for registered listeners"() {
        expect:
        !manager.hasListeners(TestFooListener)

        when:
        manager.getBroadcaster(TestFooListener)

        then:
        !manager.hasListeners(TestFooListener)

        when:
        manager.addListener(fooListener1)

        then:
        manager.hasListeners(TestFooListener)
        !manager.hasListeners(TestBarListener)

        when:
        manager.removeListener(fooListener1)

        then:
        !manager.hasListeners(TestFooListener)

        when:
        manager.addListener(Mock(BothListener))

        then:
        manager.hasListeners(TestFooListener)
        manager.hasListeners(TestBarListener)
    }

    def "anonymous broadcaster forwards events after all listeners removed"() {
        def listener1 = Mock(BuildScopeListener)
        def listener2 = Mock(BuildScopeListener)
        def listener3 = Mock(BuildScopeListener)

        manager.addListener(listener1)
        def child = manager.createChild(Scope.Build)
        def broadcast = child.createAnonymousBroadcaster(BuildScopeListener)
        child.addListener(listener2)
        broadcast.add(listener3)

        when:
        broadcast.removeAll()
        broadcast.source.foo("value")

        then:
        1 * listener1.foo("value")
        1 * listener2.foo("value")
        0 * listener3._
    }

    @EventScope(Scope.BuildTree.class)
    interface TestFooListener {
        void foo(String param);
    }

    @EventScope(Scope.Build.class)
    interface BuildScopeListener {
        void foo(String param);
    }

    @EventScope(Scope.BuildTree.class)
    interface TestBarListener {
        void bar(int value);
    }

    @EventScope(Scope.BuildTree.class)
    interface BothListener extends TestFooListener, TestBarListener {
    }

    @EventScope(Scope.BuildTree.class)
    interface TestBazListener {
        void baz()
    }

    @EventScope(Scope.Global)
    interface TestListenerWithWrongScope {
        void foo(String param)
    }

    @EventScope(Scope.BuildTree.class)
    @StatefulListener
    interface StatefulTestListener {
        void something(String value)
    }

    @EventScope(Scope.BuildTree.class)
    @ParallelListener
    interface ParallelTestListener extends StatefulTestListener {}
}
