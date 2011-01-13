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
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import spock.lang.Ignore

class ConcurrentSpecificationTest extends ConcurrentSpecification {
    def canCheckThatMethodCallWaitsUntilAsyncActionIsComplete() {
        SomeAsyncWorker worker = Mock()
        SomeSyncClass target = new SomeSyncClass(worker: worker)

        def notifyListener = later()

        when:
        def result
        def action = start {
            result = target.doWork('value')
        }

        then:
        action.waitsFor(notifyListener)
        1 * worker.doLater(!null) >> { args -> notifyListener.activate { args[0].call('result') } }

        when:
        finished()

        then:
        result == 'result'
    }

    def canCheckThatMethodCallDoesNotWaitUntilAsyncActionIsComplete() {
        SomeAsyncWorker worker = Mock()
        Closure handler = Mock()
        SomeSyncClass target = new SomeSyncClass(worker: worker)

        def notifyListener = later()

        when:
        def action = start {
            target.startWork(handler)
        }

        then:
        action.doesNotWaitFor(notifyListener)
        1 * worker.doLater(!null) >> { args -> notifyListener.activate { args[0].call('result') } }

        when:
        finished()

        then:
        1 * handler.call('[result]')
    }

    def canHaveMultipleAsyncActions() {
        SomeAsyncWorker worker = Mock()
        Closure handler = Mock()
        SomeSyncClass target = new SomeSyncClass(worker: worker)

        Closure listener
        def notifyListener = later()
        def notifyListenerAgain = later()

        when:
        def action = start {
            target.startWork(handler)
            target.startWork(handler)
        }

        then:
        action.doesNotWaitFor(notifyListener, notifyListenerAgain)
        2 * worker.doLater(!null) >> { args ->
            if (!listener) {
                listener = args[0];
                notifyListener.activate { args[0].call('result1') }
            } else {
                notifyListenerAgain.activate { args[0].call('result2') }
            }
        }

        when:
        notifyListener.completed()

        then:
        1 * handler.call('[result1]')

        when:
        finished()

        then:
        1 * handler.call('[result2]')
    }

    def canCheckThatMethodCallsBlockUntilAnotherMethodIsCalled() {
        SomeConditionClass condition = new SomeConditionClass()

        when:
        def action1 = start {
            condition.waitUntilComplete()
        }
        def action2 = start {
            condition.waitUntilComplete()
        }

        then:
        all(action1, action2).waitsFor {
            condition.complete()
        }
    }

    def canCheckThatMethodCompletesInSpecifiedTime() {
        SomeConditionClass condition = new SomeConditionClass()

        when:
        def timedOut = false
        def action = start {
            timedOut = condition.waitUntilComplete(200)
        }

        then:
        action.completesWithin(200, TimeUnit.MILLISECONDS)

        when:
        finished()

        then:
        timedOut
    }

    def finishRethrowsExceptionThrownByTestThread() {
        RuntimeException failure = new RuntimeException()

        when:
        start {
            failure.fillInStackTrace()
            throw failure
        }
        finished()

        then:
        RuntimeException e = thrown()
        e.is(failure)
    }

    @Ignore
    def completeRethrowsExceptionThrownByTestThread() {
        RuntimeException failure = new RuntimeException()

        when:
        def action = start {
            failure.fillInStackTrace()
            throw failure
        }
        action.completed()

        then:
        RuntimeException e = thrown()
        e.is(failure)
    }
}

interface SomeAsyncWorker {
    void doLater(Closure cl)
}

class SomeSyncClass {
    SomeAsyncWorker worker

    def doWork(Object value) {
        SynchronousQueue queue = new SynchronousQueue()
        worker.doLater { arg ->
            queue.put(arg)
        }
        return queue.take()
    }

    def startWork(Closure handler) {
        worker.doLater { result -> handler.call("[$result]" as String) }
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