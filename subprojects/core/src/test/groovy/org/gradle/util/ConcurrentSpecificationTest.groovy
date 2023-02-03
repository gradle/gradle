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

import org.gradle.util.internal.FailsWithMessage

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.spockframework.mock.TooManyInvocationsError
import spock.lang.FailsWith

class ConcurrentSpecificationTest extends ConcurrentSpecification {
    def setup() {
        shortTimeout = 2000
    }

    def "can check that an action calls a mock method asynchronously"() {
        Runnable action = Mock()
        def executed = startsAsyncAction()

        when:
        executed.started {
            executor.execute { action.run() }
        }

        then:
        1 * action.run() >> { executed.done() }
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected async action to complete, but it did not.')
    def "async action fails when expected mock method is never called"() {
        Runnable action = Mock()
        def executed = startsAsyncAction()

        when:
        executed.started {}

        then:
        1 * action.run() >> { executed.done() }
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Cannot wait for action to complete from the thread that is executing it.')
    def "async action fails when expected mock method is called from start action thread"() {
        Runnable action = Mock()
        def executed = startsAsyncAction()

        when:
        executed.started {
            action.run()
        }

        then:
        1 * action.run() >> { executed.done() }
    }

    @FailsWith(TooManyInvocationsError)
    def "async action fails when start action throws an exception"() {
        Runnable action = Mock()
        def executed = startsAsyncAction()

        when:
        executed.started {
            action.run()
        }

        then:
        0 * action.run()
    }

    @FailsWith(TooManyInvocationsError)
    def "async action fails when async action throws an exception"() {
        Runnable action = Mock()

        when:
        startsAsyncAction().started {
            executor.execute { action.run() }
        }

        then:
        0 * action.run()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to complete quickly, but it did not.')
    def "async action fails when start action never finishes"() {
        def latch = new CountDownLatch(1)
        Runnable action = Mock()

        when:
        startsAsyncAction().started {
            latch.await()
        }

        then:
        1 * action.run()

        cleanup:
        latch.countDown()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to complete quickly, but it did not.')
    def "async action fails when start action blocks waiting for async action to complete"() {
        def latch = new CountDownLatch(1)
        def executed = startsAsyncAction()
        Runnable action = Mock()

        when:
        executed.started {
            start { action.run() }
            latch.await()
        }

        then:
        1 * action.run() >> { executed.done(); latch.countDown() }

        cleanup:
        latch.countDown()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Action has not been started.')
    def "async action fails when done called before start action is called"() {
        expect:
        startsAsyncAction().done()
    }

    def "can check that an action blocks until an asynchronous callback is made"() {
        Runnable action = Mock()
        def latch = new CountDownLatch(1)
        def operation = waitsForAsyncCallback()

        when:
        operation.start {
            action.run()
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater { latch.countDown() } }
    }

    @FailsWith(TooManyInvocationsError)
    def "blocking action fails when blocking action throws exception"() {
        Runnable action = Mock()

        when:
        waitsForAsyncCallback().start {
            action.run()
        }

        then:
        0 * action.run()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to block, but it did not.')
    def "blocking action fails when blocking action finishes without waiting for callback action"() {
        Runnable action = Mock()
        def operation = waitsForAsyncCallback()

        when:
        operation.start {
            action.run()
        }

        then:
        1 * action.run() >> { operation.callbackLater { } }
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to unblock, but it did not.')
    def "blocking action fails when blocking action never finishes"() {
        Runnable action = Mock()
        def latch = new CountDownLatch(1)
        def operation = waitsForAsyncCallback()

        when:
        operation.start {
            action.run()
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater {} }

        cleanup:
        latch.countDown()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to register a callback action, but it did not.')
    def "blocking action fails when blocking action never registers callback action"() {
        Runnable action = Mock()
        def latch = new CountDownLatch(1)
        def operation = waitsForAsyncCallback()

        when:
        operation.start {
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater {} }

        cleanup:
        latch.countDown()
    }

    @FailsWith(TooManyInvocationsError)
    def "blocking action fails when action throws exception"() {
        Runnable action = Mock()

        when:
        waitsForAsyncCallback().start {
            action.run()
        }

        then:
        0 * action.run()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected callback action to complete, but it did not.')
    def "blocking action fails when callback action never finishes"() {
        def operation = waitsForAsyncCallback()
        def latch = new CountDownLatch(1)
        Runnable action = Mock()

        when:
        operation.start {
            action.run()
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater { latch.await() } }

        cleanup:
        latch.countDown()
    }

    @FailsWith(TooManyInvocationsError)
    def "blocking action fails when callback action method throws exception"() {
        def operation = waitsForAsyncCallback()
        def latch = new CountDownLatch(1)
        Runnable action = Mock()

        when:
        operation.start {
            action.run()
            latch.await()
        }

        then:
        1 * action.run() >> { operation.callbackLater { action.run() } }

        cleanup:
        latch.countDown()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Action has not been started.')
    def "blocking action fails when callback made before blocking action started"() {
        expect:
        waitsForAsyncCallback().callbackLater { }
    }

    def "can check that an action blocks until an asynchronous action is finished"() {
        Runnable action = Mock()
        def operation = waitsForAsyncActionToComplete()
        def latch = new CountDownLatch(1)

        when:
        operation.start {
            start { action.run() }
            latch.await()
        }
        finished()

        then:
        1 * action.run() >> { args -> operation.done(); latch.countDown() }

        cleanup:
        latch.countDown()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to block, but it did not.')
    def "blocking action fails when action does not wait for async action to start"() {
        Runnable action = Mock()
        def operation = waitsForAsyncActionToComplete()

        when:
        operation.start {
            start { action.run() }
        }
        finished()

        then:
        _ * action.run() >> { operation.done() }
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to block, but it did not.')
    def "blocking action fails when action does not wait for async action to complete"() {
        Runnable action = Mock()
        def started = new CountDownLatch(1)
        def operation = waitsForAsyncActionToComplete()

        when:
        operation.start {
            start { action.run() }
            started.await()
        }
        finished()

        then:
        1 * action.run() >> { started.countDown(); operation.done() }

        cleanup:
        started.countDown()
    }

    @FailsWithMessage(type = IllegalStateException, message = 'Expected action to block, but it did not.')
    def "blocking action fails when action does not start async action"() {
        Runnable action = Mock()
        def operation = waitsForAsyncActionToComplete()

        when:
        operation.start { }

        then:
        1 * action.run() >> { operation.done() }
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

    @FailsWith(TestException)
    def "finish rethrows exception thrown by test thread"() {
        Runnable action = Mock()

        when:
        start {
            throw new TestException()
        }
        finished()

        then:
        1 * action.run()
    }
}

class TestException extends RuntimeException {
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
