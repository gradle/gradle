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

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * <p>A base class for writing specifications which exercise concurrent code.
 *
 * <p>See {@link ConcurrentSpecificationTest} for some examples.
 */
class ConcurrentSpecification extends Specification {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentSpecification.class)
    private final Lock lock = new ReentrantLock()
    private final Condition threadsChanged = lock.newCondition()
    private final Set<DeferredActionImpl> mocks = [] as Set
    private final Set<TestThread> threads = [] as Set
    private final List<Throwable> failures = []

    def cleanup() {
        finished()
    }

    /**
     * Creates an action which will be used by a mock object to synchronise with the SUT.
     *
     * @return The action.
     */
    DeferredAction later() {
        lock.lock()
        try {
            DeferredActionImpl mock = new DeferredActionImpl(this, lock)
            mocks << mock
            return mock
        } finally {
            lock.unlock()
        }
    }

    /**
     * Starts a thread which executes the given closure. Blocks until all deferred actions are activated. Does not wait for the thread to complete.
     *
     * @return A handle to the test thread.
     */
    TestParticipant start(Closure cl) {
        lock.lock()
        try {
            TestThread thread = new TestThread(this, lock, cl)
            thread.start()
            waitForAllMocks()
            return new TestParticipantImpl(this, thread)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Returns a composite participant, which you can use to perform atomic operations on.
     *
     * @return A handle to the composite participant.
     */
    TestParticipant all(TestParticipant... participants) {
        return new CompositeTestParticipant(this, lock, participants as List)
    }

    private void waitForAllMocks() {
        LOG.info("Waiting for all mocks to block.")
        Date timeout = shortTimeout()
        mocks.each { mock ->
            mock.waitUntilActivated(timeout)
        }
    }

    /**
     * Activates and executes all deferred actions and waits for all threads to complete. Asserts that the threads complete in a 'short' time. Rethrows any exceptions thrown by test threads.
     */
    void finished() {
        Date timeout = shortTimeout()
        lock.lock()
        try {
            LOG.info("Waiting for actions to complete.")
            mocks.each { mock ->
                mock.run(timeout)
            }

            LOG.info("Waiting for test threads to complete.")
            while (!threads.isEmpty()) {
                if (!threadsChanged.awaitUntil(timeout)) {
                    failures << new IllegalStateException("Timeout waiting for test threads to complete.")
                    break;
                }
            }
            threads.each { thread ->
                thread.interrupt()
            }

            LOG.info("Finishing up.")
            if (!failures.isEmpty()) {
                throw failures[0]
            }
        } finally {
            threads.clear()
            mocks.clear()
            failures.clear()
            lock.unlock()
        }

    }

    static Date shortTimeout() {
        return new Date(System.currentTimeMillis() + 5000)
    }

    void run(Closure cl, Date timeout) {
        def thread = new TestThread(this, lock, cl)
        thread.start()
        thread.completesBefore(timeout)
    }

    void onThreadStart(TestThread thread) {
        lock.lock()
        try {
            threads << thread
            threadsChanged.signalAll()
        } finally {
            lock.unlock()
        }
    }

    void onThreadComplete(TestThread thread, Throwable failure) {
        lock.lock()
        try {
            threads.remove(thread)
            if (failure) {
                failures << failure
            }
            threadsChanged.signalAll()
        } finally {
            lock.unlock()
        }
    }
}

class TestThread extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(TestThread.class)
    private final ConcurrentSpecification owner
    private final Runnable action
    private final Lock lock
    private final Condition stateChanged
    private boolean complete

    TestThread(ConcurrentSpecification owner, Lock lock, Runnable action) {
        this.owner = owner
        this.action = action
        this.lock = lock
        this.stateChanged = lock.newCondition()
    }

    @Override
    void start() {
        LOG.info("$this started.")

        lock.lock()
        try {
            owner.onThreadStart(this)
            stateChanged.signalAll()
        } finally {
            lock.unlock()
        }

        super.start()
    }

    void running() {
        lock.lock()
        try {
            if (complete) {
                throw new IllegalStateException("$this should still be running, but is not.")
            }
        } finally {
            lock.unlock()
        }
    }

    void completesBefore(Date timeout) {
        lock.lock()
        try {
            LOG.info("Waiting for $this to complete.")
            while (!complete) {
                if (!stateChanged.awaitUntil(timeout)) {
                    throw new IllegalStateException("Timeout waiting for $this to complete.")
                }
            }
            LOG.info("$this completed.")
        } finally {
            lock.unlock()
        }
    }

    @Override
    void run() {
        Throwable failure = null
        try {
            action.run()
        } catch (Throwable t) {
            failure = t
        }

        lock.lock()
        try {
            complete = true
            stateChanged.signalAll()
            owner.onThreadComplete(this, failure)
            LOG.info("$this completed.")
        } finally {
            lock.unlock()
        }
    }
}

/**
 * Some potentially long running operation.
 */
interface LongRunningAction {
    /**
     * Blocks until this action has completed. Asserts that the action completes in a 'short' time. Rethrows any exception from the action.
     */
    void completed()

    /**
     * Blocks until this action has completed. Asserts that the action completes within the specified time. Rethrows any exception from the action.
     */
    void completesWithin(long maxWaitValue, TimeUnit maxWaitUnits)

    /**
     * Blocks until this action has completed. Asserts that the action completes before the given time. Rethrows any exception from the action.
     */
    void completesBefore(Date timeout)
}

/**
 * An action which runs at some point in the future. A {@code DeferredAction} must be activated before it can run, by calling {@link DeferredAction#activate(Closure)}.
 */
interface DeferredAction extends LongRunningAction {
    /**
     * Registers that the target sync point has been reached, and this action is ready to execute. This method does not block.
     *
     * This action is started once this method has been called, and one of the following have been executed:
     *
     * <ul>
     * <li>{@link TestParticipant#waitsFor(LongRunningAction)}</li>
     * <li>{@link TestParticipant#doesNotWaitFor(LongRunningAction)}</li>
     * <li>{@link ConcurrentSpecification#finished}</li>
     * </ul>
     */
    void activate(Closure action)
}

interface TestParticipant extends LongRunningAction {
    /**
     * Asserts that this test participant is running.
     */
    void running()
    /**
     * Asserts that this test participant blocks until the given actions complete. If any action is a {@link DeferredAction}, the action is started.
     *
     * This method blocks until both this participant and the actions have completed, and asserts that everything completes in a 'short' time.
     */
    void waitsFor(LongRunningAction... targets)

    /**
     * Asserts that this test participant blocks until the given action completes.
     *
     * This method blocks until both this participant and the action have completed, and asserts that everything completes in a 'short' time.
     */
    void waitsFor(Closure action)

    /**
     * Asserts that this test participant does not block while the given actions are executing. If any action is a {@link DeferredAction}, it must be activated first.
     *
     * This method blocks until this participant has completed, and asserts that it completes in a 'short' time.
     */
    void doesNotWaitFor(LongRunningAction... targets)
}

abstract class AbstractAction implements LongRunningAction {
    void completed() {
        Date expiry = ConcurrentSpecification.shortTimeout()
        completesBefore(expiry)
    }

    void completesWithin(long maxWaitValue, TimeUnit maxWaitUnits) {
        Date expiry = new Date(System.currentTimeMillis() + maxWaitUnits.toMillis(maxWaitValue))
        completesBefore(expiry + 500)
    }

    abstract void completesBefore(Date timeout)
}

class DeferredActionImpl extends AbstractAction implements DeferredAction {
    private static final Logger LOG = LoggerFactory.getLogger(DeferredActionImpl.class)
    private final ConcurrentSpecification owner
    private final Lock lock
    private final Condition stateChange = lock.newCondition()
    private Closure action
    private boolean activated
    private boolean complete

    DeferredActionImpl(ConcurrentSpecification owner, Lock lock) {
        this.owner = owner
        this.lock = lock
    }

    void activated() {
        lock.lock()
        try {
            if (!activated) {
                throw new IllegalStateException("Action has not been activated.")
            }
        } finally {
            lock.unlock()
        }
    }

    @Override
    void completesBefore(Date timeout) {
        activated()
        run(timeout)
    }

    void run(Date timeout) {
        lock.lock()
        try {
            if (!activated || complete) {
                return
            }
        } finally {
            lock.unlock()
        }

        LOG.info("Running deferred action")
        owner.run(action, timeout)
        LOG.info("Deferred action complete")

        lock.lock()
        try {
            complete = true
            action = null
            stateChange.signalAll()
        } finally {
            lock.unlock()
        }
    }

    void waitUntilActivated(Date timeout) {
        lock.lock()
        try {
            while (!activated) {
                if (!stateChange.awaitUntil(timeout)) {
                    throw new IllegalStateException("Timeout waiting for action to be activated.")
                }
            }
        } finally {
            lock.unlock()
        }
    }

    void activate(Closure action) {
        lock.lock()
        try {
            if (activated) {
                throw new IllegalStateException("This action has already been activated.")
            }

            activated = true
            this.action = action
            stateChange.signalAll()
        } finally {
            lock.unlock()
        }
    }
}

abstract class AbstractTestParticipant extends AbstractAction implements TestParticipant {
    private final ConcurrentSpecification owner

    AbstractTestParticipant(ConcurrentSpecification owner) {
        this.owner = owner
    }

    void doesNotWaitFor(LongRunningAction... targets) {
        targets*.activated()
        completed()
    }

    void waitsFor(LongRunningAction... targets) {
        targets*.activated()
        Thread.sleep(500)
        running()
        targets*.completed()
        completed()
    }

    void waitsFor(Closure action) {
        Thread.sleep(500)
        running()
        owner.run(action, owner.shortTimeout())
        completed()
    }
}

class TestParticipantImpl extends AbstractTestParticipant {
    private final TestThread thread

    TestParticipantImpl(ConcurrentSpecification owner, TestThread thread) {
        super(owner)
        this.thread = thread
    }

    @Override
    void completesBefore(Date timeout) {
        thread.completesBefore(timeout)
    }

    void running() {
        thread.running()
    }
}

class CompositeTestParticipant extends AbstractTestParticipant {
    private final List<TestParticipant> participants
    private final Lock lock

    CompositeTestParticipant(ConcurrentSpecification owner, Lock lock, List<TestParticipant> participants) {
        super(owner)
        this.participants = participants
        this.lock = lock
    }

    void running() {
        lock.lock()
        try {
            participants*.running()
        } finally {
            lock.unlock()
        }
    }

    @Override
    void completesBefore(Date timeout) {
        lock.lock()
        try {
            participants*.completesBefore(timeout)
        } finally {
            lock.unlock()
        }
    }
}
