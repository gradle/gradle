/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.util.internal;

import groovy.lang.Closure;
import junit.framework.AssertionFailedError;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>A rule for testing concurrent code.</p>
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
public class MultithreadedTestRule extends ExternalResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(MultithreadedTestRule.class);
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
    private final SyncPoint syncPoint = new SyncPoint();

    /**
     * Creates an Executor which the test can control.
     */
    protected ExecutorService getExecutor() {
        if (executor == null) {
            executor = new ExecutorImpl();
        }
        return executor;
    }

    /**
     * Creates an ExecutorFactory for the test to use.
     */
    protected ExecutorFactory getExecutorFactory() {
        return new DefaultExecutorFactory() {
            @Override
            protected ExecutorService createExecutor(String displayName) {
                return new ExecutorImpl();
            }
        };
    }

    /**
     * Executes the given closure in a test thread.
     */
    protected ThreadHandle start(final Closure closure) {
        Runnable task = new Runnable() {
            @Override
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
            @Override
            public void run() {
                closure.call();
            }
        };

        return start(task).waitFor();
    }

    public ThreadHandle expectTimesOut(int value, TimeUnit units, Closure closure) {
        Date start = new Date();
        ThreadHandle threadHandle = start(closure);
        threadHandle.waitFor();
        Date end = new Date();
        long actual = end.getTime() - start.getTime();
        long expected = units.toMillis(value);
        if (actual < expected - 200) {
            throw new RuntimeException(String.format(
                    "Action did not block for expected time. Expected ~ %d ms, was %d ms.", expected, actual));
        }
        if (actual > expected + 1200) {
            throw new RuntimeException(String.format(
                    "Action did not complete within expected time. Expected ~ %d ms, was %d ms.", expected, actual));
        }
        return threadHandle;
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

            @Override
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
        return new ThreadHandleImpl(thread);
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
                        failures.add(new RuntimeException("Timeout waiting for threads to finish."));
                        break;
                    }
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
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

    @Override
    protected void after() {
        waitForStop();
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
    public void expectLater(final int tick) {
        final Thread targetThread = Thread.currentThread();
        LOGGER.debug("Thread {} expecting tick {}", targetThread, tick);
        start(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                lock.lock();
                try {
                    ClockTickImpl clockTick = getTick(tick);
                    if (!clockTick.isImmediatelyAfter(currentTick)) {
                        throw new RuntimeException(String.format("Cannot wait for %s, as clock is currently at %s.",
                                clockTick, currentTick));
                    }
                    if (!active.contains(targetThread)) {
                        throw new RuntimeException(
                                "Cannot wait for clock tick from a thread which is not a test thread.");
                    }

                    synching.add(targetThread);
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        });
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

    /**
     * Executes the given action in another thread, and asserts that the action blocks until all actions provided to
     * {@link #expectUnblocks(groovy.lang.Closure)} have been executed.
     *
     * @param action The action to execute.
     */
    public void expectBlocks(Closure action) {
        syncPoint.expectBlocks(action);
    }

    /**
     * Executes the given action, asserting that it unblocks all actions provided to {@link
     * #expectBlocks(groovy.lang.Closure)}
     *
     * @param action The action to execute.
     */
    public void expectUnblocks(Closure action) {
        syncPoint.expectUnblocks(action);
    }

    private class ExecutorImpl extends AbstractExecutorService {
        private final Set<ThreadHandle> threads = new CopyOnWriteArraySet<ThreadHandle>();

        @Override
        public void execute(Runnable command) {
            threads.add(start(command));
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
            Date expiry = new Date(System.currentTimeMillis() + unit.toMillis(timeout));
            for (ThreadHandle thread : threads) {
                if (!thread.waitUntil(expiry)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public List<Runnable> shutdownNow() {
            return new ArrayList<Runnable>();
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException();
        }
    }

    public interface ThreadHandle {
        ThreadHandle waitFor();

        boolean waitUntil(Date expiry);

        boolean isCurrentThread();

        void waitUntilBlocked();
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

        @Override
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

    private enum State {
        Idle, Blocking, Blocked, Unblocking, Unblocked, Failed
    }

    private class SyncPoint {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private State state = State.Idle;
        private ThreadHandle blockingThread;

        public void expectBlocks(Closure action) {
            try {
                setState(State.Idle, State.Blocking);
                setBlockingThread(start(action));
                setState(State.Blocking, State.Blocked);
                waitForState(State.Unblocked, State.Failed);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        public void expectUnblocks(Closure action) {
            try {
                waitForState(State.Blocked);

                ThreadHandle thread = getBlockingThread();
                if (thread.isCurrentThread()) {
                    throw new IllegalStateException("The blocking thread cannot unblock itself.");
                }

                setState(State.Blocked, State.Unblocking);
                try {
                    thread.waitUntilBlocked();
                    action.call();
                    boolean completed = thread.waitUntil(new Date(System.currentTimeMillis() + 500L));
                    if (!completed) {
                        throw new IllegalStateException("Expected blocking action to unblock, but it did not.");
                    }
                    setState(State.Unblocking, State.Unblocked);
                } catch (Throwable e) {
                    setState(State.Unblocking, State.Failed);
                    throw UncheckedException.throwAsUncheckedException(e);
                } finally {
                    setBlockingThread(null);
                }
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        private ThreadHandle getBlockingThread() {
            lock.lock();
            try {
                return blockingThread;
            } finally {
                lock.unlock();
            }
        }

        private void setBlockingThread(ThreadHandle thread) {
            lock.lock();
            try {
                blockingThread = thread;
            } finally {
                lock.unlock();
            }
        }

        private State waitForState(State... states) throws InterruptedException {
            Date expiry = new Date(System.currentTimeMillis() + 4000L);
            Collection<State> expectedStates = Arrays.asList(states);
            lock.lock();
            try {
                while (!expectedStates.contains(state)) {
                    if (!condition.awaitUntil(expiry)) {
                        throw new IllegalStateException(String.format("Timeout waiting for one of: %s",
                                expectedStates));
                    }
                }
                return state;
            } finally {
                lock.unlock();
            }
        }

        private void setState(State expected, State newState) {
            lock.lock();
            try {
                if (state != expected) {
                    throw new IllegalStateException(String.format("In unexpected state. Expected %s, actual %s",
                            expected, state));
                }
                state = newState;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private class ThreadHandleImpl implements ThreadHandle {
        private final Thread thread;
        private final Set<Thread.State> blockedStates = EnumSet.of(Thread.State.BLOCKED, Thread.State.TIMED_WAITING,
                Thread.State.WAITING);

        public ThreadHandleImpl(Thread thread) {
            this.thread = thread;
        }

        @Override
        public ThreadHandle waitFor() {
            Date expiry = new Date(System.currentTimeMillis() + 2 * MAX_WAIT_TIME);
            if (!waitUntil(expiry)) {
                throw new RuntimeException("timeout waiting for test thread to stop.");
            }
            return this;
        }

        @Override
        public boolean waitUntil(Date expiry) {
            if (isCurrentThread()) {
                throw new RuntimeException("A test thread cannot wait for itself to complete.");
            }

            lock.lock();
            try {
                while (active.contains(thread)) {
                    try {
                        boolean signalled = condition.awaitUntil(expiry);
                        if (!signalled) {
                            return false;
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                lock.unlock();
            }

            return true;
        }

        @Override
        public boolean isCurrentThread() {
            return Thread.currentThread() == thread;
        }

        public boolean isBlocked() {
            return blockedStates.contains(thread.getState());
        }

        @Override
        public void waitUntilBlocked() {
            long expiry = System.currentTimeMillis() + 2000L;
            while (!isBlocked()) {
                if (System.currentTimeMillis() > expiry) {
                    throw new IllegalStateException("Timeout waiting for thread to block.");
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
    }
}
