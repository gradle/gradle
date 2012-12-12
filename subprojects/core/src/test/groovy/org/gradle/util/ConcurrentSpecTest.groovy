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

package org.gradle.util

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class ConcurrentSpecTest extends ConcurrentSpec {
    def "can test that an action happens asynchronously"() {
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

    def "can test that an action happens while another is blocked"() {
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

    def "can test method blocks until action is complete"() {
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

    def "can test method blocks for a certain time"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.blockUntil.end
        }

        when:
        async {
            worker.run(action, 2)
        }

        then:
        instant.end - instant.start in approx(2000)
    }

    def "fails when test method does not block for expected time"() {
        Worker worker = new Worker(executor)

        given:
        def action = {
            thread.blockUntil.end
        }

        when:
        async {
            worker.run(action, 2)
        }
        def failure = null
        try {
            assert instant.end - instant.start in approx(5000)
        } catch (AssertionError e) {
            failure = e
        }

        then:
        failure != null
        failure.message.contains('instant.end - instant.start in approx(5000)')
    }

    def "an implicit start and time point is added"() {
        given:
        async {
        }

        expect:
        instant.end > instant.start
    }

    def "cannot query instant that has not been defined in test thread"() {
        when:
        instant.unknown

        then:
        IllegalStateException e = thrown()
        e.message == "Instant 'unknown' has not been defined by any test thread."
    }

    def "can use test threads to define instants outside of an async { } block"() {
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

}
