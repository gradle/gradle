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

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.StoppableExecutor
import spock.lang.Specification
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.Condition

class ConcurrentSpec extends Specification {
    private final TestExecutor executor = new TestExecutor()
    private final TestExecutorFactory executorFactory = new TestExecutorFactory(executor)

    /**
     * An object that allows instants to be defined and queried.
     */
    final Instants instant = new Instants(executor)

    /**
     * An object that allows control over the current thread.
     */
    final TestThread thread = new TestThread(instant)

    /**
     * Returns an Executor that should be used for running asynchronous actions.
     */
    Executor getExecutor() {
        return executor
    }

    /**
     * Returns an ExecutorFactory that should be used for running asynchronous actions.
     */
    ExecutorFactory getExecutorFactory() {
        return executorFactory
    }

    def cleanup() {
        executor.stop(new Date(System.currentTimeMillis() + 5000))
    }

    /**
     * Executes the given action and then blocks until all test threads have completed. The action may define instants for later querying outside the block.
     */
    void async(Closure action) {
        Date timeout = new Date(System.currentTimeMillis() + 20000)
        executor.start()
        executor.execute {
            instant.start
            try {
                action.call()
            } finally {
                instant.end
            }
        }
        executor.stop(timeout)
    }

    /**
     * Returns a range that contains the given milliseconds +/- some error margin
     */
    Range approx(long millis) {
        return new Range(millis)
    }

    private static class TestExecutorFactory implements ExecutorFactory {
        private final TestExecutor executor;

        TestExecutorFactory(TestExecutor executor) {
            this.executor = executor
        }

        StoppableExecutor create(String displayName) {
            return new TestStoppableExecutor(executor)
        }
    }

    private static class TestStoppableExecutor implements StoppableExecutor {
        private final Lock lock = new ReentrantLock()
        private final Condition condition = lock.newCondition()
        private int count
        private final TestExecutor executor;

        TestStoppableExecutor(TestExecutor executor) {
            this.executor = executor
        }

        void execute(Runnable command) {
            lock.lock()
            try {
                count++
            } finally {
                lock.unlock()
            }

            executor.execute {
                try {
                    command.run()
                } finally {
                    lock.lock()
                    try {
                        count--
                        condition.signalAll()
                    } finally {
                        lock.unlock()
                    }
                }
            }
        }

        void requestStop() {
        }

        void stop() {
            lock.lock()
            try {
                while (count > 0) {
                    condition.await()
                }
            } finally {
                lock.unlock()
            }
        }

        void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException {
            throw new UnsupportedOperationException()
        }
    }


    private static class TestExecutor implements Executor {
        private final Lock lock = new ReentrantLock()
        private final Condition condition = lock.newCondition()
        private final Set<Thread> threads = new HashSet<Thread>()
        private Thread owner
        private Throwable failure
        private int threadNum

        boolean isTestThread() {
            lock.lock()
            try {
                return threads.contains(Thread.currentThread())
            } finally {
                lock.unlock()
            }
        }

        void start() {
            lock.lock()
            try {
                if (owner != null) {
                    throw new IllegalStateException("Cannot nest async { } blocks.")
                }
                owner = Thread.currentThread()
            } finally {
                lock.unlock()
            }
        }

        void execute(Runnable runnable) {
            def thread = new Thread() {
                @Override
                void run() {
                    try {
                        println "* ${Thread.currentThread().name} running"
                        runnable.run()
                    } catch (Throwable throwable) {
                        println "* ${Thread.currentThread().name} failed"
                        lock.lock()
                        try {
                            if (failure == null) {
                                failure = throwable
                            }
                        } finally {
                            lock.unlock()
                        }
                    } finally {
                        println "* ${Thread.currentThread().name} finished"
                        lock.lock()
                        try {
                            threads.remove(Thread.currentThread())
                            condition.signalAll()
                        } finally {
                            lock.unlock()
                        }
                    }
                }
            }

            lock.lock()
            try {
                threads << thread
                thread.name = "Test thread ${++threadNum}"
            } finally {
                lock.unlock()
            }

            thread.start()
        }

        void stop(Date expiry) {
            lock.lock()
            try {
                if (!threads.isEmpty()) {
                    println "* waiting for ${threads.size()} test threads to complete."
                }

                while (!threads.isEmpty()) {
                    if (!condition.awaitUntil(expiry)) {
                        break;
                    }
                }

                if (!threads.isEmpty()) {
                    println "* timeout waiting for threads ${threads.size()} to complete"
                    threads.each { thread ->
                        IllegalStateException e = new IllegalStateException("Timeout waiting for ${thread.name} to complete.")
                        e.stackTrace = thread.stackTrace
                        if (failure == null) {
                            failure = e
                        } else {
                            e.printStackTrace()
                        }
                        thread.interrupt()
                    }
                }
                threads.clear()
                if (failure != null) {
                    throw failure
                }
            } finally {
                owner = null
                failure = null
                lock.unlock()
            }
        }
    }

    static class BlockTarget {
        private final Instants instants

        BlockTarget(Instants instants) {
            this.instants = instants
        }

        def getProperty(String name) {
            instants.waitFor(name)
            Thread.sleep(500)
            return null
        }
    }

    static class TestThread {
        private final Instants instants

        TestThread(Instants instants) {
            this.instants = instants
        }

        void block() {
            Thread.sleep(500)
        }

        BlockTarget getBlockUntil() {
            return new BlockTarget(instants)
        }
    }

    static class Duration {
        private final long nanos

        Duration(long nanos) {
            this.nanos = nanos
        }

        long getMillis() {
            return nanos / 1000000
        }

        @Override
        String toString() {
            return "[$nanos nanos]"
        }
    }

    static class Range {
        private final long millis

        Range(long millis) {
            this.millis = millis
        }

        @Override
        String toString() {
            return "[approx $millis millis]"
        }

        boolean isCase(Duration duration) {
            def actualMillis = duration.millis
            return actualMillis > millis - 500 && actualMillis < millis + 2000
        }
    }

    static class Instant implements Comparable<Instant> {
        final long nanos

        Instant(long nanos) {
            this.nanos = nanos
        }

        @Override
        String toString() {
            return "[instant at $nanos]"
        }

        int compareTo(Instant t) {
            return nanos.compareTo(t.nanos)
        }

        Instant plus(long millis) {
            return new Instant(nanos + millis * 1000)
        }

        Duration minus(Instant t) {
            return new Duration(nanos - t.nanos)
        }
    }

    static class NamedInstant extends Instant {
        private final String name
        private final int sequenceNumber

        NamedInstant(String name, long time, int sequenceNumber) {
            super(time)
            this.name = name
            this.sequenceNumber = sequenceNumber
        }

        @Override
        String toString() {
            return "[instant ${name} #${sequenceNumber} at ${nanos}]"
        }
    }

    /**
     * A dynamic collection of Instants. When accessed from a test thread, defines a new time point. When
     * accessed from the main thread, queries an existing time point.
     */
    static class Instants {
        private final Object lock = new Object()
        private final Map<String, NamedInstant> timePoints = [:]
        private final TestExecutor executor

        Instants(TestExecutor executor) {
            this.executor = executor
        }

        @Override
        String toString() {
            return "instants"
        }

        void waitFor(String name) {
            synchronized (lock) {
                while (!timePoints.containsKey(name)) {
                    lock.wait()
                }
            }
        }

        def getProperty(String name) {
            def testThread = executor.testThread
            synchronized (lock) {
                def time = timePoints[name]
                if (testThread) {
                    if (time != null) {
                        throw new IllegalStateException("Instant '$name' has already been defined by another test thread.")
                    }
                    def now = System.nanoTime()
                    time = new NamedInstant(name, now, timePoints.size())
                    timePoints[name] = time
                    lock.notifyAll()
                    println "* instant $name reached"
                    return time
                } else {
                    if (time == null) {
                        throw new IllegalStateException("Instant '$name' has not been defined by any test thread.")
                    }
                    return time
                }
            }
        }
    }
}
