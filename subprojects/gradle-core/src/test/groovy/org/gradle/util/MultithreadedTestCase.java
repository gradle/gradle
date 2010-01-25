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
import junit.framework.AssertionFailedError;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.hamcrest.Matcher;
import org.junit.After;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>A base class for testing concurrent code.</p>
 *
 * <p>Provides several ways to start and manage threads. You can use the {@link #start(groovy.lang.Closure)} or {@link
 * #run(groovy.lang.Closure)} methods to execute test code in other threads. You can use {@link #waitForAll()} to wait
 * for all test threads to complete. In addition, the test tear-down method blocks until all test threads have stopped
 * and ensures that no exceptions were thrown in any test threads.</p>
 *
 * <p>Provides an {@link java.util.concurrent.Executor} implementation, which uses test threads to execute any tasks
 * submitted to it.</p>
 *
 * <p>You can use {@link #syncAt(int)} and {@link #expectBlocksUntil(int, groovy.lang.Closure)} to synchronise between
 * test threads.</p>
 */
public class MultithreadedTestCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedTestCase.class);
    private static final int MAX_WAIT_TIME = 5000;
    private ExecutorImpl executor;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<Thread> active = new HashSet<Thread>();
    private final Set<Thread> synching = new HashSet<Thread>();
    private final List<Throwable> failures = new ArrayList<Throwable>();
    private final Map<Integer, ClockTickImpl> ticks = new HashMap<Integer, ClockTickImpl>();
    private ClockTickImpl currentTick = getTick(0);
    private boolean stopped;
    private final ThreadLocal<Matcher<? extends Throwable>> expectedFailure
            = new ThreadLocal<Matcher<? extends Throwable>>();

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
            if (stopped) {
                throw new IllegalStateException("Cannot start new threads, as this test case has been stopped.");
            }
            LOGGER.debug("Started {}", thread);
            active.add(thread);
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void testThreadFinished(Thread thread, Throwable failure) {
        lock.lock();
        try {
            active.remove(thread);
            Matcher<? extends Throwable> matcher = expectedFailure.get();
            if (failure != null) {
                if (matcher != null && matcher.matches(failure)) {
                    LOGGER.debug("Finished {} with expected failure.", thread);
                } else {
                    LOGGER.error(String.format("Failure in %s", thread), failure);
                    failures.add(failure);
                }
            } else {
                if (matcher != null) {
                    String message = String.format("Did not get expected failure in %s", thread);
                    LOGGER.error(message);
                    failures.add(new AssertionFailedError(message));
                } else {
                    LOGGER.debug("Finished {}", thread);
                }
            }
            condition.signalAll();
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
                    boolean signalled = condition.awaitUntil(expiry);
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
     * Waits for all asynchronous activity to complete. Applies a timeout, and re-throws any exceptions which occurred.
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
                    boolean signaled = condition.awaitUntil(expiry);
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
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                throw new RuntimeException("An unexpected exception occurred in a test thread.", failure);
            }
        } finally {
            lock.unlock();
        }
    }

    @After
    public void waitForStop() {
        lock.lock();
        try {
            stopped = true;
        } finally {
            lock.unlock();
        }
        waitForAll();
    }

    /**
     * Returns the meta-info for the given clock tick.
     */
    public ClockTick clockTick(int tick) {
        lock.lock();
        try {
            return getTick(tick);
        } finally {
            lock.unlock();
        }
    }

    private ClockTickImpl getTick(int tick) {
        ClockTickImpl clockTick = ticks.get(tick);
        if (clockTick == null) {
            clockTick = new ClockTickImpl(tick);
            ticks.put(tick, clockTick);
        }
        return clockTick;
    }

    /**
     * Blocks until the clock has reached the given tick. The clock advances to the given tick when all test threads
     * have called {@link #syncAt(int)} or {@link #expectBlocksUntil(int, groovy.lang.Closure)} with the given tick, and
     * there are least 2 test threads.
     *
     * @param tick The expected clock tick
     */
    public void syncAt(int tick) {
        LOGGER.debug("Thread {} synching at tick {}", Thread.currentThread(), tick);

        lock.lock();
        try {
            ClockTickImpl clockTick = getTick(tick);
            if (!clockTick.isImmediatelyAfter(currentTick)) {
                throw new RuntimeException(String.format("Cannot wait for %s, as clock is currently at %s.", clockTick,
                        currentTick));
            }
            if (!active.contains(Thread.currentThread())) {
                throw new RuntimeException("Cannot wait for clock tick from a thread which is not a test thread.");
            }

            Date expiry = new Date(System.currentTimeMillis() + MAX_WAIT_TIME);
            synching.add(Thread.currentThread());
            condition.signalAll();
            while (failures.isEmpty() && currentTick != clockTick && !clockTick.allThreadsSynced(synching, active)) {
                try {
                    boolean signalled = condition.awaitUntil(expiry);
                    if (!signalled) {
                        throw new RuntimeException(String.format(
                                "Timeout waiting for all threads to reach %s. Currently at %s.", clockTick,
                                currentTick));
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!failures.isEmpty()) {
                throw new RuntimeException(String.format(
                        "Could not wait for all threads to reach %s, as a failure has occurred in another test thread.",
                        clockTick));
            }
            if (clockTick.isImmediatelyAfter(currentTick)) {
                currentTick = clockTick;
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
    private void expectLater(int tick) {
        LOGGER.debug("Thread {} expecting tick {}", Thread.currentThread(), tick);

        lock.lock();
        try {
            ClockTickImpl clockTick = getTick(tick);
            if (!clockTick.isImmediatelyAfter(currentTick)) {
                throw new RuntimeException(String.format("Cannot wait for %s, as clock is currently at %s.", clockTick,
                        currentTick));
            }
            if (!active.contains(Thread.currentThread())) {
                throw new RuntimeException("Cannot wait for clock tick from a thread which is not a test thread.");
            }

            synching.add(Thread.currentThread());
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Asserts that the given closure blocks until the given clock tick is reached.
     *
     * @param tick The expected clock tick when the closure completes.
     * @param closure The closure to execute.
     */
    public void expectBlocksUntil(int tick, Closure closure) {
        expectLater(tick);
        closure.call();
        shouldBeAt(tick);
    }

    /**
     * Asserts that the clock is at the given tick.
     *
     * @param tick The expected clock tick.
     */
    public void shouldBeAt(int tick) {
        lock.lock();
        try {
            if (currentTick != getTick(tick)) {
                throw new RuntimeException(String.format("Expected clock to be at %s, but is at %s.", tick,
                        currentTick));
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Indicates that the current test thread will fail with an exception that matches the given criteria.
     */
    public void willFailWith(Matcher<? extends Throwable> matcher) {
        expectedFailure.set(matcher);
    }

    private class ExecutorImpl implements Executor {
        public void execute(Runnable command) {
            start(command);
        }
    }

    public interface ThreadHandle {
        ThreadHandle waitFor();
    }

    public interface ClockTick {
        ClockTick hasParticipants(int count);
    }

    private static class ClockTickImpl implements ClockTick {
        private final int number;
        private int participants;

        private ClockTickImpl(int number) {
            this.number = number;
        }

        @Override
        public String toString() {
            return String.format("tick %d", number);
        }

        public ClockTick hasParticipants(int count) {
            participants = count;
            return this;
        }

        public boolean allThreadsSynced(Set<Thread> synching, Set<Thread> active) {
            if (participants > 0) {
                return synching.size() == participants;
            }
            return synching.equals(active) && synching.size() > 1;
        }

        public boolean isImmediatelyAfter(ClockTickImpl other) {
            return number == other.number + 1;
        }
    }
}
