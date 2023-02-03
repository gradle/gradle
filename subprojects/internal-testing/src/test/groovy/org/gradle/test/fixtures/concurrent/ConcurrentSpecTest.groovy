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

package org.gradle.test.fixtures.concurrent

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class ConcurrentSpecTest extends ConcurrentSpec {
    def "can use instants to test that an action happens asynchronously"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.block()
            instant.actionCompleted
        }

        when:
        async {
            worker.runLater(action)
            instant.queued
        }

        then:
        instant.queued < instant.actionCompleted
    }

    def "can use instants to test that an action happens while another is blocked"() {
        Worker worker = new Worker(executor)

        given:
        def action1 = {
            thread.blockUntil.action2Completed
            instant.action1Completed
        }
        def action2 = {
            instant.action2Completed
        }

        when:
        async {
            worker.runLater(action1)
            thread.block()
            worker.runLater(action2)
        }

        then:
        instant.action2Completed < instant.action1Completed
    }

    def "can use instants to test that a method blocks until action is complete"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.block()
            instant.actionCompleted
        }

        when:
        async {
            worker.runLater(action)
            worker.stop()
            instant.stopped
        }

        then:
        instant.stopped > instant.actionCompleted
    }

    def "can use operation to test that a method blocks until action is complete"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.block()
            instant.actionCompleted
        }

        when:
        operation.runAndWait {
            worker.runLater(action)
            worker.stop()
        }

        then:
        operation.runAndWait.end > instant.actionCompleted
    }

    def "can use instants to test that method blocks for a certain time"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.blockUntil.end
        }

        when:
        async {
            instant.start
            worker.run(action, 2)
            instant.end
        }

        then:
        instant.end - instant.start in approx(2000)
    }

    def "can use operation to test that method blocks for a certain time"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.blockUntil.timesOut
        }

        when:
        operation.timesOut {
            worker.run(action, 2)
        }

        then:
        operation.timesOut.duration in approx(2000)
    }

    def "fails when method does not block for expected time"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.blockUntil.runAndWait
        }

        when:
        operation.runAndWait {
            worker.run(action, 2)
        }
        def failure = null
        try {
            assert operation.runAndWait.duration in approx(5000)
        } catch (AssertionError e) {
            failure = e
        }

        then:
        failure != null
        failure.message.contains('operation.runAndWait.duration in approx(5000)')
    }

    def "can use instants to test that method executes one thing at a time"() {
        Synchronizer synchronizer = new Synchronizer()

        given:
        def action1 = {
            instant.action1Start
            thread.block()
            instant.action1End
        }
        def action2 = {
            instant.action2Start
        }

        when:
        start {
            synchronizer.runNow(action1)
        }
        async {
            thread.blockUntil.action1Start
            synchronizer.runNow(action2)
            instant.end
        }

        then:
        instant.action2Start > instant.action1End
        instant.end > instant.action2Start
    }

    def "cannot query instant that has not been defined in test thread"() {
        when:
        instant.unknown

        then:
        IllegalStateException e = thrown()
        e.message == "Instant 'unknown' has not been defined by any test thread."
    }

    def "cannot query operation that has not been defined in test thread"() {
        when:
        operation.unknown

        then:
        IllegalStateException e = thrown()
        e.message == "Operation 'unknown' has not been defined by any test thread."
    }

    def "cannot query operation end time while it is running"() {
        when:
        operation.doStuff {
            operation.doStuff.end
        }

        then:
        IllegalStateException e = thrown()
        e.message == "Operation 'doStuff' has not completed yet."
    }

    def "cannot query operation duration while it is running"() {
        when:
        operation.doStuff {
            operation.doStuff.duration
        }

        then:
        IllegalStateException e = thrown()
        e.message == "Operation 'doStuff' has not completed yet."
    }

    def "can use test threads to define instants"() {
        def worker = new Worker(executor)

        given:
        def action = {
            instant.actionExecuted
        }

        when:
        worker.runLater(action)
        worker.stop()

        then:
        instant.actionExecuted
    }

    def "can use arbitrary thread to define an instant"() {
        given:
        def action = { instant.actionExecuted }
        def thread = new Thread(action)

        when:
        thread.start()
        thread.join()

        then:
        instant.actionExecuted
    }

    def "fails when waiting for an instant that is not defined by any thread"() {
        instant.timeout = 100

        when:
        thread.blockUntil.unknown

        then:
        IllegalStateException e = thrown()
        e.message == "Timeout waiting for instant 'unknown' to be defined by another thread."

        when:
        async {
            thread.blockUntil.unknown
        }

        then:
        e = thrown()
        e.message == "Timeout waiting for instant 'unknown' to be defined by another thread."

        when:
        start {
            thread.blockUntil.unknown
        }
        async { }

        then:
        e = thrown()
        e.message == "Timeout waiting for instant 'unknown' to be defined by another thread."
    }

    def "async { } block rethrows test thread failures"() {
        def worker = new Worker(executor)
        def failure = new RuntimeException()

        when:
        async {
            worker.runLater { throw failure }
        }

        then:
        RuntimeException e = thrown()
        e == failure
    }

    def "defines implicit instant for operation end point"() {
        when:
        operation.thing {}

        then:
        instant.thing == operation.thing.end
    }

    static class Worker {
        final Executor executor
        final Object lock = new Object()
        int count

        Worker(Executor executor) {
            this.executor = executor
        }

        void run(Runnable runnable, int timeoutSeconds) {
            def finished = new CountDownLatch(1)
            runLater {
                try {
                    runnable.run()
                } finally {
                    finished.countDown()
                }
            }
            finished.await(timeoutSeconds, TimeUnit.SECONDS)
        }

        void runLater(Runnable runnable) {
            synchronized (lock) {
                count++
            }
            executor.execute {
                try {
                    runnable.run()
                } finally {
                    synchronized (lock) {
                        count--
                        lock.notifyAll()
                    }
                }
            }
        }

        void stop() {
            synchronized (lock) {
                while (count > 0) {
                    lock.wait()
                }
            }
        }
    }

    static class Synchronizer {
        synchronized void runNow(Runnable runnable) {
            runnable.run()
        }
    }
}
