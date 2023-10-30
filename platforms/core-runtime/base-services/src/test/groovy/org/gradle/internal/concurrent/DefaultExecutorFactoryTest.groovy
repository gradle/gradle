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
package org.gradle.internal.concurrent

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultExecutorFactoryTest extends ConcurrentSpec {

    def factory = new DefaultExecutorFactory()

    def cleanup() {
        factory.stop()
    }

    def fixedSizeExecutorRunsNoMoreThanRequestedNumberOfActionsConcurrently() {
        given:
        def action1 = {
            instant.started1
            thread.block()
            instant.completed1
        }
        def action2 = {
            instant.started2
            thread.blockUntil.started3
        }
        def action3 = {
            instant.started3
        }

        when:
        def executor = factory.create('test', 2)
        executor.execute(action1)
        executor.execute(action2)
        executor.execute(action3)
        thread.blockUntil.started3

        then:
        instant.started3 > instant.completed1
        instant.started3 > instant.started2

        cleanup:
        executor?.stop()
    }

    def stopBlocksUntilAllJobsAreComplete() {
        given:
        def action1 = {
            thread.block()
            instant.completed1
        }
        def action2 = {
            thread.block()
            instant.completed2
        }

        when:
        async {
            def executor = factory.create('test')
            executor.execute(action1)
            executor.execute(action2)
            executor.stop()
            instant.stopped
        }

        then:
        instant.stopped > instant.completed1
        instant.stopped > instant.completed2
    }

    def factoryStopBlocksUntilAllJobsAreComplete() {
        given:
        def action1 = {
            thread.block()
            instant.completed1
        }
        def action2 = {
            thread.block()
            instant.completed2
        }

        when:
        async {
            factory.create("1").execute(action1)
            factory.create("2").execute(action2)
            factory.stop()
            instant.stopped
        }

        then:
        instant.stopped > instant.completed1
        instant.stopped > instant.completed2
    }

    def cannotStopExecutorFromAnExecutorThread() {
        when:
        def executor = factory.create('<display-name>')
        def action = {
            executor.stop()
        }
        executor.execute(action)
        executor.stop()

        then:
        IllegalStateException e = thrown()
        e.message == 'Cannot stop this executor from an executor thread.'
    }

    def stopThrowsExceptionOnTimeout() {
        def timeoutMs = 100
        def latch = new CountDownLatch(1)

        def action = {
            latch.await()
        }

        when:
        def executor = factory.create('<display-name>')
        executor.execute(action)

        operation.stop {
            executor.stop(timeoutMs, TimeUnit.MILLISECONDS)
        }

        then:
        IllegalStateException e = thrown()
        e.message == 'Timeout waiting for concurrent jobs to complete.'

        and:
        operation.stop.duration in approx(timeoutMs)
    }

    def stopRethrowsFirstExecutionException() {
        given:
        def failure1 = new RuntimeException()
        def runnable1 = {
            instant.broken1
            throw failure1
        }

        def failure2 = new RuntimeException()
        def runnable2 = {
            instant.broken2
            throw failure2
        }

        when:
        def executor = factory.create('test')
        executor.execute(runnable1)
        thread.blockUntil.broken1
        thread.block()
        executor.execute(runnable2)
        thread.blockUntil.broken2

        then:
        noExceptionThrown()

        when:
        executor.stop()

        then:
        def ex = thrown(RuntimeException)
        ex.is(failure1)
    }

    def stopOfFixedSizedExecutorRethrowsFirstExecutionException() {
        given:
        def failure1 = new RuntimeException()
        def runnable1 = {
            throw failure1
        }

        def failure2 = new RuntimeException()
        def runnable2 = {
            instant.broken2
            throw failure2
        }

        when:
        def executor = factory.create('test', 1)
        executor.execute(runnable1)
        executor.execute(runnable2)
        thread.blockUntil.broken2

        then:
        noExceptionThrown()

        when:
        executor.stop()

        then:
        def ex = thrown(RuntimeException)
        ex.is(failure1)
    }

    def fixedSizeScheduledExecutorRunsNoMoreThanRequestedNumberOfActionsConcurrently() {
        given:
        def action1 = {
            instant.started1
            thread.block()
            instant.completed1
        }
        def action2 = {
            instant.started2
            thread.blockUntil.started3
        } as Callable<Void>
        def action3 = {
            instant.started3
        }

        when:
        def executor = factory.createScheduled('test', 2)
        executor.schedule(action1, 0, TimeUnit.SECONDS)
        executor.schedule(action2, 0, TimeUnit.SECONDS)
        executor.schedule(action3, 0, TimeUnit.SECONDS)
        thread.blockUntil.started3

        then:
        instant.started3 > instant.completed1
        instant.started3 > instant.started2
    }


    def stopBlocksUntilAllScheduledRunningJobsAreCompleted() {
        given:
        def action1 = {
            instant.started1
            thread.blockUntil.willStop
            thread.block()
            instant.completed1
        }
        def action2 = {
            instant.started2
            thread.blockUntil.willStop
            thread.block()
            instant.completed2
        } as Callable<Void>

        when:
        async {
            def executor = factory.createScheduled('test', 2)
            executor.schedule(action1, 0, TimeUnit.SECONDS)
            executor.schedule(action2, 0, TimeUnit.SECONDS)
            thread.blockUntil.started1
            thread.blockUntil.started2
            instant.willStop
            executor.stop()
            instant.stopped
        }

        then:
        instant.stopped > instant.completed1
        instant.stopped > instant.completed2
    }

    def factoryStopBlocksUntilAllScheduledRunningJobsAreCompleted() {
        given:
        def action1 = {
            instant.started1
            thread.blockUntil.willStop
            thread.block()
            instant.completed1
        }
        def action2 = {
            instant.started2
            thread.blockUntil.willStop
            thread.block()
            instant.completed2
        } as Callable<Void>

        when:
        async {
            factory.createScheduled("1", 1).schedule(action1, 0, TimeUnit.SECONDS)
            factory.createScheduled("2", 1).schedule(action2, 0, TimeUnit.SECONDS)
            thread.blockUntil.started1
            thread.blockUntil.started2
            instant.willStop
            factory.stop()
            instant.stopped
        }

        then:
        instant.stopped > instant.completed1
        instant.stopped > instant.completed2
    }

    def stopScheduledExecutorThrowsExceptionOnTimeout() {
        def stopLatch = new CountDownLatch(1)
        def stoppedLatch = new CountDownLatch(1)
        def action = {
            stopLatch.countDown()
            // Make sure we block the executor until it has been stopped
            stoppedLatch.await()
        }

        when:
        def executor = factory.createScheduled('<display-name>', 1)
        executor.schedule(action, 0, TimeUnit.SECONDS)
        operation.stop {
            stopLatch.await()
            try {
                executor.stop(50, TimeUnit.MILLISECONDS)
            } finally {
                stoppedLatch.countDown()
            }
        }

        then:
        IllegalStateException e = thrown()
        e.message == 'Timeout waiting for concurrent jobs to complete.'
    }

    def stopScheduledExecutorRethrowsFirstRunnableExecutionException() {
        given:
        def failure1 = new RuntimeException()
        def action1 = new Runnable() {
            void run() {
                instant.broken1
                throw failure1
            }
        }

        def failure2 = new RuntimeException()
        def action2 = new Runnable() {
            void run() {
                instant.broken2
                throw failure2
            }
        }

        expect:
        Runnable.isAssignableFrom(action1.class)
        Runnable.isAssignableFrom(action2.class)

        when:
        def executor = factory.createScheduled('test', 1)
        executor.schedule(action1, 0, TimeUnit.SECONDS)
        thread.blockUntil.broken1
        thread.block()
        executor.schedule(action2, 0, TimeUnit.SECONDS)
        thread.blockUntil.broken2

        then:
        noExceptionThrown()

        when:
        executor.stop()

        then:
        def ex = thrown(RuntimeException)
        ex.is(failure1)
    }

    def stopScheduledExecutorRethrowsFirstCallableExecutionException() {
        given:
        def failure1 = new RuntimeException()
        def action1 = new Callable<Void>() {
            Void call() {
                instant.broken1
                throw failure1
            }
        }
        def failure2 = new RuntimeException()
        def action2 = new Callable<Void>() {
            Void call() {
                instant.broken2
                throw failure2
            }
        }

        expect:
        Callable.isAssignableFrom(action1.class)
        Callable.isAssignableFrom(action2.class)

        when:
        def executor = factory.createScheduled('test', 1)
        executor.schedule(action1, 0, TimeUnit.SECONDS)
        thread.blockUntil.broken1
        thread.block()
        executor.schedule(action2, 0, TimeUnit.SECONDS)
        thread.blockUntil.broken2

        then:
        noExceptionThrown()

        when:
        executor.stop()

        then:
        def ex = thrown(RuntimeException)
        ex.is(failure1)
    }

    def stopOfFixedSizedScheduledExecutorRethrowsFirstExecutionException() {
        given:
        def failure1 = new RuntimeException()
        def action1 = {
            throw failure1
        }

        def failure2 = new RuntimeException()
        def action2 = {
            instant.broken2
            throw failure2
        }

        when:
        def executor = factory.createScheduled('test', 1)
        executor.schedule(action1, 0, TimeUnit.SECONDS)
        executor.schedule(action2, 0, TimeUnit.SECONDS)
        thread.blockUntil.broken2

        then:
        noExceptionThrown()

        when:
        executor.stop()

        then:
        def ex = thrown(RuntimeException)
        ex.is(failure1)
    }
}
