/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.gradle.api.Action

class ConcurrentSpecificationTest extends ConcurrentSpecification {
    def "can check that an action calls a mock method asynchronously"() {
        Runnable action = Mock()
        def executed = asyncAction()

        when:
        executed.startedBy {
            executor.execute { action.run() }
        }

        then:
        1 * action.run() >> { executed.done() }
    }

    def "async action fails when expected mock method is never called"() {
        when:
        asyncAction().startedBy {}

        then:
        RuntimeException e = thrown()
        e.message == 'Expected async action to complete, but it did not.'
    }

    def "async action fails when expected mock method is called from start action thread"() {
        Runnable action = Mock()
        def executed = asyncAction()

        when:
        executed.startedBy {
            action.run()
        }

        then:
        1 * action.run() >> { executed.done() }
        RuntimeException e = thrown()
        e.message == 'Cannot wait for start action to complete from the start action.'
    }

    def "async action fails when start action throws an exception"() {
        def failure = new RuntimeException()

        when:
        asyncAction().startedBy {
            throw failure
        }

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "async action fails when expected mock method throws an exception"() {
        Runnable action = Mock()
        def failure = new RuntimeException()

        when:
        asyncAction().startedBy {
            executor.execute { action.run() }
        }

        then:
        1 * action.run() >> { throw failure }
        RuntimeException e = thrown()
        e == failure
    }

    def "async action fails when start action does not finish"() {
        def latch = new CountDownLatch(1)

        when:
        asyncAction().startedBy {
            latch.await()
        }

        then:
        RuntimeException e = thrown()
        e.message == 'Expected start action to complete quickly, but it did not.'

        cleanup:
        latch.countDown()
    }

    def "async action fails when done called before start action is called"() {
        when:
        asyncAction().done()

        then:
        RuntimeException e = thrown()
        e.message == 'Action has not been started by calling startedBy().'
    }

    def "can check that an action blocks until an asynchronous callback is made"() {
        Action<Runnable> service = Mock()
        def operation = blockingAction()

        when:
        operation.blocksUntilCallback {
            def latch = new CountDownLatch(1)
            service.execute { latch.countDown() }
            latch.await()
        }

        then:
        1 * service.execute(!null) >> { args -> operation.callbackLater { args[0].run() } }
    }

    def "blocking action fails when blocking action throws exception"() {
        def failure = new RuntimeException()

        when:
        blockingAction().blocksUntilCallback {
            throw failure
        }

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "blocking action fails when blocking action finishes without waiting for callback action"() {
        when:
        blockingAction().blocksUntilCallback {
        }

        then:
        RuntimeException e = thrown()
        e.message == 'Expected action to block, but it did not.'
    }

    def "blocking action fails when blocking action never finishes"() {
        Runnable action = Mock()
        def latch = new CountDownLatch(1)
        def operation = blockingAction()

        when:
        operation.blocksUntilCallback {
            action.run()
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater {} }
        RuntimeException e = thrown()
        e.message == 'Expected action to unblock, but it did not.'

        cleanup:
        latch.countDown()
    }

    def "blocking action fails when blocking action never registers callback action"() {
        def latch = new CountDownLatch(1)
        def operation = blockingAction()

        when:
        operation.blocksUntilCallback {
            latch.await()
        }

        then:
        RuntimeException e = thrown()
        e.message == 'Expected action to register a callback action, but it did not.'

        cleanup:
        latch.countDown()
    }

    def "blocking action fails when mock method throws exception"() {
        def failure = new RuntimeException()

        when:
        blockingAction().blocksUntilCallback {
            throw failure
        }

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "blocking action fails when callback action never finishes"() {
        def operation = blockingAction()
        def latch = new CountDownLatch(1)
        Runnable action = Mock()

        when:
        operation.blocksUntilCallback {
            action.run()
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater { latch.await() } }
        RuntimeException e = thrown()
        e.message == 'Expected callback action to complete, but it did not.'

        cleanup:
        latch.countDown()
    }

    def "blocking action fails when callback action method throws exception"() {
        def failure = new RuntimeException()
        def operation = blockingAction()
        def latch = new CountDownLatch(1)
        Runnable action = Mock()

        when:
        operation.blocksUntilCallback {
            action.run()
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater { throw failure } }
        RuntimeException e = thrown()
        e == failure

        cleanup:
        latch.countDown()
    }

    def "blocking action fails when callback made before blocking action started"() {
        when:
        blockingAction().callbackLater { }

        then:
        RuntimeException e = thrown()
        e.message == 'Action has not been started by calling blocksUntilCallback().'
    }

    def "can check that some method completes in expected time"() {
        SomeConditionClass condition = new SomeConditionClass()

        when:
        def timedOut = false
        def action = start {
            timedOut = condition.waitUntilComplete(200)
        }
        action.completesWithin(200, TimeUnit.MILLISECONDS)

        then:
        timedOut
    }

    def "finish rethrows exception thrown by test thread"() {
        RuntimeException failure = new RuntimeException()

        when:
        start {
            throw failure
        }
        finished()

        then:
        RuntimeException e = thrown()
        e.is(failure)
    }
}

class SomeConditionClass {
    final CountDownLatch latch = new CountDownLatch(1)

    void waitUntilComplete() {
        latch.await()
    }

    boolean waitUntilComplete(int maxWaitMillis) {
        return !latch.await(maxWaitMillis, TimeUnit.MILLISECONDS)
    }

    void complete() {
        latch.countDown()
    }
}