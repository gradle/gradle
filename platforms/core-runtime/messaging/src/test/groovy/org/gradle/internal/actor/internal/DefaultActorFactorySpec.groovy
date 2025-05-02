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

package org.gradle.internal.actor.internal

import org.gradle.internal.concurrent.ThreadSafe
import org.gradle.internal.dispatch.DispatchException
import org.gradle.internal.dispatch.MethodInvocation
import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class DefaultActorFactorySpec extends ConcurrentSpec {
    private final TargetObject target = Mock()
    private final DefaultActorFactory factory = new DefaultActorFactory(executorFactory)

    def cleanup() {
        factory.stop()
    }

    def createsANonBlockingActorForATargetObject() {
        when:
        def actor = factory.createActor(target)

        then:
        actor != null
    }

    def cachesTheNonBlockingActorForATargetObject() {
        when:
        def actor1 = factory.createActor(target)
        def actor2 = factory.createActor(target)

        then:
        actor2.is(actor1)
    }

    def returnsTargetObjectIfTargetObjectIsAnActor() {
        when:
        def actor1 = factory.createActor(target)
        def actor2 = factory.createActor(actor1)

        then:
        actor2.is(actor1)
    }

    def nonBlockingActorAndProxyAreBothMarkedAsThreadSafe() {
        when:
        def actor = factory.createActor(target)

        then:
        actor instanceof ThreadSafe
        actor.getProxy(Runnable) instanceof ThreadSafe
    }

    def createsABlockingActorForATargetObject() {
        when:
        def actor = factory.createBlockingActor(target)

        then:
        actor != null
    }

    def cachesTheBlockingActorForATargetObject() {
        when:
        def actor1 = factory.createBlockingActor(target)
        def actor2 = factory.createBlockingActor(target)

        then:
        actor1.is(actor2)
    }

    def blockingActorAndProxyAreBothMarkedAsThreadSafe() {
        when:
        def actor = factory.createBlockingActor(target)

        then:
        actor instanceof ThreadSafe
        actor.getProxy(Runnable) instanceof ThreadSafe
    }

    def blockingActorDispatchesMethodInvocationToTargetObjectAndBlocksUntilMethodIsComplete() {
        def actor = factory.createBlockingActor(target)
        def testThread = Thread.currentThread()

        when:
        actor.dispatch(new MethodInvocation(TargetObject.class.getMethod('doStuff', String.class), ['param'] as Object[]))

        then:
        1 * target.doStuff('param') >> {
            assert Thread.currentThread() == testThread
        }
    }

    def blockingActorProxyDispatchesMethodInvocationToTargetObjectAndBlocksUntilMethodIsComplete() {
        def actor = factory.createBlockingActor(target)
        def proxy = actor.getProxy(TargetObject)
        def testThread = Thread.currentThread()

        when:
        proxy.doStuff('param')

        then:
        1 * target.doStuff('param') >> {
            assert Thread.currentThread() == testThread
        }
    }

    def blockingActorDispatchesMethodInvocationFromOneThreadAtATime() {
        def target = {param ->
            if (param == 'param') {
                instant.param1Start
                thread.block()
                instant.param1End
            } else if (param == 'param2') {
                instant.param2Start
            }
        } as TargetObject

        def actor = factory.createBlockingActor(target)
        def proxy = actor.getProxy(TargetObject)

        when:
        start {
            proxy.doStuff('param')
        }
        async {
            thread.blockUntil.param1Start
            proxy.doStuff('param2')
        }

        then:
        instant.param2Start > instant.param1End
    }

    def nonBlockingActorDispatchesMethodInvocationToTargetObjectAndDoesNotWaitForResult() {
        def actor = factory.createActor(target)

        when:
        operation.dispatch {
            actor.dispatch(new MethodInvocation(TargetObject.class.getMethod('doStuff', String.class), ['param'] as Object[]))
        }
        thread.blockUntil.handled

        then:
        1 * target.doStuff('param') >> {
            thread.block()
            instant.handled
        }

        and:
        operation.dispatch.end < instant.handled
    }

    def nonBlockingActorProxyDispatchesMethodCallToTargetObjectAndDoesNotWaitForResult() {
        def actor = factory.createActor(target)
        def proxy = actor.getProxy(TargetObject.class)

        when:
        operation.dispatch {
            proxy.doStuff('param')
        }
        thread.blockUntil.actionFinished

        then:
        1 * target.doStuff('param') >> {
            thread.block()
            instant.actionFinished
        }

        and:
        operation.dispatch.end < instant.actionFinished
    }

    def nonBlockingActorPropagatesMethodFailuresOnStop() {
        def actor = factory.createActor(target)
        def proxy = actor.getProxy(TargetObject.class)
        def failure = new RuntimeException()

        given:
        target.doStuff('param') >> { throw failure }

        when:
        proxy.doStuff('param')
        actor.stop()

        then:
        DispatchException e = thrown()
        e.message.startsWith("Could not dispatch message")
        e.cause == failure
    }

    def nonBlockingActorStopBlocksUntilAllMethodCallsComplete() {
        def actor = factory.createActor(target)
        def proxy = actor.getProxy(TargetObject.class)

        given:
        target.doStuff('param') >> {
            thread.block()
            instant.param1
        }
        target.doStuff('param2') >> {
            instant.param2
        }

        when:
        operation.dispatchAndStop {
            proxy.doStuff('param')
            proxy.doStuff('param2')
            actor.stop()
        }

        then:
        operation.dispatchAndStop.end > instant.param1
        operation.dispatchAndStop.end > instant.param2
    }

    def blockingActorStopBlocksUntilAllMethodCallsComplete() {
        def actor = factory.createBlockingActor(target)
        def proxy = actor.getProxy(TargetObject.class)

        given:
        target.doStuff('param') >> {
            instant.actionStarted
            thread.block()
            instant.actionFinished
        }

        when:
        start {
            proxy.doStuff('param')
        }
        operation.stop {
            thread.blockUntil.actionStarted
            actor.stop()
        }

        then:
        operation.stop.end > instant.actionFinished
    }

    def factoryStopBlocksUntilAllMethodCallsComplete() {
        def actor = factory.createActor(target)
        def proxy = actor.getProxy(TargetObject.class)

        given:
        target.doStuff('param') >> {
            thread.block()
            instant.actionFinished
        }

        when:
        proxy.doStuff('param')
        operation.stop {
            factory.stop()
        }

        then:
        operation.stop.end > instant.actionFinished
    }

    def cannotDispatchToBlockingActorAfterItHasBeenStopped() {
        def actor = factory.createBlockingActor(target)
        def proxy = actor.getProxy(TargetObject.class)

        given:
        actor.stop()

        when:
        proxy.doStuff('param')

        then:
        IllegalStateException e = thrown()
        e.message == 'This actor has been stopped.'
    }

    def cannotDispatchToNonBlockingActorAfterItHasBeenStopped() {
        def actor = factory.createActor(target)
        def proxy = actor.getProxy(TargetObject.class)

        given:
        actor.stop()

        when:
        proxy.doStuff('param')

        then:
        IllegalStateException e = thrown()
        e.message.startsWith('Cannot dispatch message, as this message dispatch has been stopped.')
    }
}

interface TargetObject {
    void doStuff(String param)
}
