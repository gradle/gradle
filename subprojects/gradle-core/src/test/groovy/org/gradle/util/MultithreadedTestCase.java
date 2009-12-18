/*
 * Copyright 2009 the original author or authors.
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
    private final List<Throwable> failures = new ArrayList<Throwable>();
    private final ThreadContextImpl threadContext = new ThreadContextImpl();
    private int currentTick = 0;
    private final Condition clockChanged = lock.newCondition();
    private Throwable failure;

    protected Executor getExecutor() {
        if (executor == null) {
            executor = new ExecutorImpl();
        }
        return executor;
    }

    /**
     * Executes the given closure in another thread.
     */
    protected ThreadHandle thread(final Closure closure) {
        Runnable task = new Runnable() {
            public void run() {
                closure.call();
            }
        };

        return thread(task);
    }

    /**
     * Executes the given runnable in another thread.
     */
    protected ThreadHandle thread(final Runnable task) {
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
                    finished(this, failure);
                }
            }
        };

        started(thread);
        thread.start();
        return new ThreadHandle() {
            public void waitFor() {
                MultithreadedTestCase.this.waitFor(thread);
            }
        };
    }

    private void started(Thread thread) {
        lock.lock();
        try {
            LOGGER.debug("Started {}", thread);
            active.add(thread);
            activeChanged.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void finished(Thread thread, Throwable failure) {
        lock.lock();
        try {
            LOGGER.debug("Finished {}", thread);
            active.remove(thread);
            if (failure != null) {
                failures.add(failure);
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
     * Waits for all asynchronous activity to complete. Rethrows any exceptions which occurred.
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
                failure = failures.get(0);
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
     * Blocks until the clock has reached the given tick.
     */
    public void blockUntil(int tick) {
        threadContext.blockUntil(tick);
    }

    /**
     * Blocks until all threads have blocked, then advances the clock to the given tick.
     */
    public void moveTo(int tick) {
        threadContext.moveTo(tick);
    }

    /**
     * Asserts that the clock is at the given tick.
     */
    void shouldBeAt(int tick) {
        threadContext.shouldBeAt(tick);
    }

    private class ExecutorImpl implements Executor {
        public void execute(Runnable command) {
            thread(command);
        }
    }

    private class ThreadContextImpl {
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

        public void blockUntil(int tick) {
            LOGGER.debug("Blocking {} until {}", Thread.currentThread(), tick);

            lock.lock();
            try {
                if (tick != currentTick + 1) {
                    throw new RuntimeException(String.format("Cannot block until tick %d, as clock is currently at %s.",
                            tick, currentTick));
                }

                Date expiry = new Date(System.currentTimeMillis() + MAX_WAIT_TIME);
                while (currentTick != tick) {
                    try {
                        boolean signalled = clockChanged.awaitUntil(expiry);
                        if (!signalled) {
                            throw new RuntimeException(String.format(
                                    "Timeout waiting for all threads to reach tick %d. Currently at %d.", tick,
                                    currentTick));
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                clockChanged.signalAll();
            } finally {
                lock.unlock();
            }

            LOGGER.debug("Block {} done", Thread.currentThread());
        }

        private void moveTo(int tick) {
            LOGGER.debug("Signalling {} for {}", Thread.currentThread(), tick);

            Date expiry = new Date(System.currentTimeMillis() + MAX_WAIT_TIME);
            while (true) {
                if (allBlocked()) {
                    break;
                }
                try {
                    Thread.sleep(100);
                    if (System.currentTimeMillis() > expiry.getTime()) {
                        throw new RuntimeException(String.format(
                                "Timeout waiting for all threads to block for tick %d. Currently at %d.", tick,
                                currentTick));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            lock.lock();
            try {

                if (tick != currentTick + 1) {
                    throw new RuntimeException(String.format("Cannot block until tick %d, as clock is currently at %s.",
                            tick, currentTick));
                }

                currentTick++;
                clockChanged.signalAll();
            } finally {
                lock.unlock();
            }

            LOGGER.debug("Signal {} done", Thread.currentThread());
        }

        private boolean allBlocked() {
            lock.lock();
            try {
                Thread current = Thread.currentThread();
                for (Thread thread : active) {
                    if (thread == current) {
                        continue;
                    }
                    switch (thread.getState()) {
                        case NEW:
                        case RUNNABLE:
                        case TERMINATED:
                            return false;
                    }
                }
                return true;
            } finally {
                lock.unlock();
            }
        }
    }

    public interface ThreadHandle {
        void waitFor();
    }
}
