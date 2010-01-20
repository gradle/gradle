/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MultithreadedTestCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedTestCase.class);
    private static final int MAX_WAIT_TIME = 5000;
    private ExecutorImpl executor;
    private final Lock lock = new ReentrantLock();
    private final Condition activeChanged = lock.newCondition();
    private final Set<Thread> active = new HashSet<Thread>();
    private final Set<Thread> synching = new HashSet<Thread>();
    private final List<Throwable> failures = new ArrayList<Throwable>();
    private int currentTick = 0;
    private final Condition synchingChanged = lock.newCondition();

    /**
     * Creates an Executor which the test can control.
     */
    protected Executor getExecutor() {
        if (executor == null) {
            executor = new ExecutorImpl();
        }
        return executor;
    }

    /**
     * Executes the given closure in a test thread.
     */
    protected ThreadHandle start(final Closure closure) {
        Runnable task = new Runnable() {
            public void run() {
                closure.call();
            }
        };

        return start(task);
    }

    /**
     * Executes the given closure in a test thread and waits for it to complete.
     */
    protected ThreadHandle run(final Closure closure) {
        Runnable task = new Runnable() {
            public void run() {
                closure.call();
            }
        };

        return start(task).waitFor();
    }

    /**
     * Executes the given runnable in a test thread.
     */
    protected ThreadHandle start(final Runnable task) {
        final Thread thread = new Thread() {
            @Override
            public String toString() {
                return "test thread " + getId();
            }

            public void run() {
                Throwable failure = null;
                try {
                    try {
                        task.run();
                    } catch (InvokerInvocationException e) {
                        failure = e.getCause();
                    } catch (Throwable throwable) {
                        failure = throwable;
                    }
                } finally {
                    testThreadFinished(this, failure);
                }
            }
        };

        testThreadStarted(thread);
        thread.start();
        return new ThreadHandle() {
            public ThreadHandle waitFor() {
                MultithreadedTestCase.this.waitFor(thread);
                return this;
            }
        };
    }

    private void testThreadStarted(Thread thread) {
        lock.lock();
        try {
            LOGGER.debug("Started {}", thread);
            active.add(thread);
            activeChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void testThreadFinished(Thread thread, Throwable failure) {
        lock.lock();
        try {
            active.remove(thread);
            if (failure != null) {
                LOGGER.debug(String.format("Failure in %s", thread), failure);
                failures.add(failure);
            } else {
                LOGGER.debug("Finished {}", thread);
            }
            activeChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void waitFor(Thread thread) {
        if (Thread.currentThread() == thread) {
            throw new RuntimeException("A test thread cannot wait for itself to complete.");
        }

        Date expiry = new Date(System.currentTimeMillis() + 2 * MAX_WAIT_TIME);
        lock.lock();
        try {
            while (active.contains(thread)) {
                try {
                    boolean signalled = activeChanged.awaitUntil(expiry);
                    if (!signalled) {
                        throw new RuntimeException("timeout waiting for test thread to stop.");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits for all asynchronous activity to complete. Re-throws any exceptions which occurred.
     */
    public void waitForAll() {
        Date expiry = new Date(System.currentTimeMillis() + 2 * MAX_WAIT_TIME);
        lock.lock();
        try {
            LOGGER.debug("Waiting for test threads complete.");

            if (active.contains(Thread.currentThread())) {
                throw new RuntimeException("A test thread cannot wait for test threads to complete.");
            }
            try {
                while (!active.isEmpty()) {
                    boolean signaled = activeChanged.awaitUntil(expiry);
                    if (!signaled) {
                        throw new RuntimeException("Timeout waiting for threads to finish.");
                    }
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            LOGGER.debug("All test threads complete.");

            if (!failures.isEmpty()) {
                Throwable failure = failures.get(0);
                failures.clear();
                throw new RuntimeException("An exception occurred in a test thread.", failure);
            }
        } finally {
            lock.unlock();
        }
    }

    @After
    public void waitForStop() {
        waitForAll();
    }

    /**
     * Blocks until the clock has reached the given tick. The clock advances to the given tick when all test threads
     * have called {@link #syncAt(int)} or {@link #expectLater(int)} with the given tick, and there are least 2 test
     * threads.
     *
     * @param tick The expected clock tick
     */
    public void syncAt(int tick) {
        LOGGER.debug("Thread {} synching at tick {}", Thread.currentThread(), tick);

        lock.lock();
        try {
            if (tick != currentTick + 1) {
                throw new RuntimeException(String.format("Cannot wait for clock tick %d, as clock is currently at %s.",
                        tick, currentTick));
            }
            if (!active.contains(Thread.currentThread())) {
                throw new RuntimeException("Cannot wait for clock tick from a thread which is not a test thread.");
            }

            Date expiry = new Date(System.currentTimeMillis() + MAX_WAIT_TIME);
            synching.add(Thread.currentThread());
            synchingChanged.signalAll();
            while (currentTick != tick && (!synching.equals(active) || synching.size() == 1)) {
                try {
                    boolean signalled = synchingChanged.awaitUntil(expiry);
                    if (!signalled) {
                        throw new RuntimeException(String.format(
                                "Timeout waiting for all threads to reach tick %d. Currently at %d.", tick,
                                currentTick));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (currentTick + 1 == tick) {
                currentTick = tick;
                synching.clear();
            }
        } finally {
            lock.unlock();
        }

        LOGGER.debug("Thread {} sync done", Thread.currentThread());
    }

    /**
     * Expects that the given tick will be reached at some point in the future. Does not block until the tick has been
     * reached.
     *
     * @param tick The expected clock tick.
     */
    public void expectLater(int tick) {
        LOGGER.debug("Thread {} expecting tick {}", Thread.currentThread(), tick);

        lock.lock();
        try {
            if (tick != currentTick + 1) {
                throw new RuntimeException(String.format("Cannot wait for clock tick %d, as clock is currently at %s.",
                        tick, currentTick));
            }
            if (!active.contains(Thread.currentThread())) {
                throw new RuntimeException("Cannot wait for clock tick from a thread which is not a test thread.");
            }

            synching.add(Thread.currentThread());
            synchingChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Asserts that the clock is at the given tick.
     *
     * @param tick The expected clock tick.
     */
    public void shouldBeAt(int tick) {
        lock.lock();
        try {
            if (currentTick != tick) {
                throw new RuntimeException(String.format("Expected clock to be at tick %d, but is at %d.", tick,
                        currentTick));
            }
        } finally {
            lock.unlock();
        }
    }

    private class ExecutorImpl implements Executor {
        public void execute(Runnable command) {
            start(command);
        }
    }

    public interface ThreadHandle {
        ThreadHandle waitFor();
    }
}
