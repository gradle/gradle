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

import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import org.gradle.messaging.concurrent.ExecutorFactory
import org.gradle.messaging.concurrent.StoppableExecutor
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * <p>A base class for writing specifications which exercise concurrent code.
 *
 * <p>See {@link ConcurrentSpecificationTest} for some examples.
 *
 * <p>Provides {@link Executor} and {@link ExecutorFactory} implementations for use during the test. These provide real concurrency.
 * The test threads are cleaned up at the end of the test, and any exceptions thrown by those tests are propagated.
 *
 * <p>Provides some fixtures for testing:</p>
 *
 * <ul>
 * <li>An action starts another action asynchronously without waiting for the result.</li>
 * <li>An action starts another action asynchronously and waits for the result.</li>
 * </ul>
 */
class ConcurrentSpecification extends Specification {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentSpecification.class)
    private final Lock lock = new ReentrantLock()
    private final Condition threadsChanged = lock.newCondition()
    private final Set<TestThread> threads = [] as Set
    private Closure failureHandler
    private final List<Throwable> failures = []

    def cleanup() {
        finished()
    }

    ExecutorFactory getExecutorFactory() {
        return new ExecutorFactory() {
            StoppableExecutor create(String displayName) {
                return new StoppableExecutorStub(ConcurrentSpecification.this)
            }
        }
    }

    Executor getExecutor() {
        return new Executor() {
            void execute(Runnable runnable) {
                startThread(runnable)
            }
        }
    }

    TestThread startThread(Runnable cl) {
        lock.lock()
        try {
            TestThread thread = new TestThread(this, lock, cl)
            thread.start()
            return thread
        } finally {
            lock.unlock()
        }
    }

    /**
     * Starts a thread which executes the given action/closure. Does not wait for the thread to complete.
     *
     * @return A handle to the test thread.
     */
    TestParticipant start(Runnable cl) {
        lock.lock()
        try {
            TestThread thread = new TestThread(this, lock, cl)
            thread.start()
            return new TestParticipantImpl(this, thread)
        } finally {
            lock.unlock()
        }
    }

    /**
     * Creates a new asynchronous action.
     */
    AsyncAction asyncAction() {
        return new AsyncAction(this)
    }

    /**
     * Creates a new blocking action.
     */
    BlockingAction blockingAction() {
        return new BlockingAction(this)
    }

    /**
     * Returns a composite participant, which you can use to perform atomic operations on.
     *
     * @return A handle to the composite participant.
     */
    TestParticipant all(TestParticipant... participants) {
        return new CompositeTestParticipant(this, lock, participants as List)
    }

    void onFailure(Closure cl) {
        lock.lock()
        try {
            failureHandler = cl
        } finally {
            lock.unlock()
        }
    }

    private void onFailure(Throwable t) {
        lock.lock()
        try {
            if (failureHandler != null) {
                failureHandler.call(t)
            } else {
                failures << t
            }
        } finally {
            lock.unlock()
        }
    }

    /**
     * Waits for all threads to complete. Asserts that the threads complete in a 'short' time. Rethrows any exceptions thrown by test threads.
     */
    void finished() {
        Date timeout = shortTimeout()
        lock.lock()
        try {
            LOG.info("Waiting for test threads to complete.")
            while (!threads.isEmpty()) {
                if (!threadsChanged.awaitUntil(timeout)) {
                    onFailure(new IllegalStateException("Timeout waiting for test threads to complete."))
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
            failureHandler = null
            threads.clear()
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
                onFailure(failure)
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
                    interrupt()
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

interface TestParticipant extends LongRunningAction {
    /**
     * Asserts that this test participant is running.
     */
    void running()
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

abstract class AbstractTestParticipant extends AbstractAction implements TestParticipant {
    private final ConcurrentSpecification owner

    AbstractTestParticipant(ConcurrentSpecification owner) {
        this.owner = owner
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

class StoppableExecutorStub implements StoppableExecutor {
    final ConcurrentSpecification owner

    StoppableExecutorStub(ConcurrentSpecification owner) {
        this.owner = owner
    }

    void stop() {
        throw new UnsupportedOperationException()
    }

    void stop(int timeoutValue, TimeUnit timeoutUnits) {
        throw new UnsupportedOperationException()
    }

    void requestStop() {
        throw new UnsupportedOperationException()
    }

    void execute(Runnable runnable) {
        owner.startThread(runnable)
    }
}

class AbstractAsyncAction {
    protected final ConcurrentSpecification owner
    private final Lock lock = new ReentrantLock()
    protected final Condition condition = lock.newCondition()

    AbstractAsyncAction(ConcurrentSpecification owner) {
        this.owner = owner
    }

    protected Date shortTimeout() {
        return ConcurrentSpecification.shortTimeout()
    }

    protected def withLock(Closure cl) {
        lock.lock()
        try {
            return cl.call()
        } finally {
            lock.unlock()
        }
    }
}

class AsyncAction extends AbstractAsyncAction {
    private boolean started
    private boolean completed
    private Thread startThread
    private Throwable failure

    AsyncAction(ConcurrentSpecification owner) {
        super(owner)
    }

    /**
     * Runs the given action, and then waits until another another thread calls {@link #done()}.  Asserts that the start action does
     * not block waiting for this async action to complete.
     *
     * @param action The start action
     * @return this
     */
    AsyncAction startedBy(Runnable action) {
        owner.onFailure this.&onFailure
        doStart(action)
        waitForStartToComplete()
        waitForFinish()
        return this
    }

    /**
     * Marks that this async. action is now finished.
     */
    void done() {
        waitForStartToComplete()
        doFinish()
    }

    private void onFailure(Throwable throwable) {
        withLock {
            failure = throwable
            condition.signalAll()
        }
    }

    private void doStart(Runnable action) {
        owner.startThread {
            withLock {
                if (startThread != null) {
                    throw new IllegalStateException("Cannot start action multiple times.")
                }
                startThread = Thread.currentThread()
                condition.signalAll()
            }

            action.run()

            withLock {
                started = true
                condition.signalAll()
            }
        }

        withLock {
            while (startThread == null) {
                condition.await()
            }
        }
    }

    private void doFinish() {
        withLock {
            if (completed) {
                throw new IllegalStateException("Cannot complete action multiple times.")
            }
            completed = true
            condition.signalAll()
        }
    }

    private void waitForStartToComplete() {
        Date timeout = shortTimeout()
        withLock {
            if (startThread == null) {
                throw new IllegalStateException("Action has not been started by calling startedBy().")
            }
            if (Thread.currentThread() == startThread) {
                throw new IllegalStateException("Cannot wait for start action to complete from the start action.")
            }
            while (!started && !failure) {
                if (!condition.awaitUntil(timeout)) {
                    throw new IllegalStateException("Expected start action to complete quickly, but it did not.")
                }
            }
            if (failure) {
                throw failure
            }
        }
    }

    private void waitForFinish() {
        Date timeout = shortTimeout()
        withLock {
            while (!completed && !failure) {
                if (!condition.awaitUntil(timeout)) {
                    throw new IllegalStateException("Expected async action to complete, but it did not.")
                }
            }
            if (failure) {
                throw failure
            }
        }
    }
}

class BlockingAction extends AbstractAsyncAction {
    private boolean started
    private boolean completed
    private boolean callbackCompleted
    private Runnable callback
    private Throwable failure

    BlockingAction(ConcurrentSpecification owner) {
        super(owner)
    }

    BlockingAction blocksUntilCallback(Runnable action) {
        owner.onFailure this.&onFailure

        startBlockingAction(action)
        waitForCallbackToBeRegistered()

        Thread.sleep(500)

        assertBlocked()
        runCallbackAction()
        waitForBlockingActionToComplete()

        return this
    }

    private def runCallbackAction() {
        owner.startThread {
            callback.run()

            withLock {
                callbackCompleted = true
                condition.signalAll()
            }
        }

        Date timeout = shortTimeout()
        withLock {
            while (!callbackCompleted && !failure) {
                if (!condition.awaitUntil(timeout)) {
                    throw new IllegalStateException("Expected callback action to complete, but it did not.")
                }
            }
            if (failure) {
                throw failure
            }
        }
    }

    void callbackLater(Runnable action) {
        withLock {
            if (callback) {
                throw new IllegalStateException("Cannot register callback action multiple times.")
            }
            if (!started) {
                throw new IllegalStateException("Action has not been started by calling blocksUntilCallback().")
            }
            callback = action
            condition.signalAll()
        }
    }

    private void waitForBlockingActionToComplete() {
        Date expiry = shortTimeout()
        withLock {
            while (!completed && !failure) {
                if (!condition.awaitUntil(expiry)) {
                    throw new IllegalStateException("Expected action to unblock, but it did not.")
                }
            }
            if (failure) {
                throw failure
            }
        }
    }

    private void startBlockingAction(Runnable action) {
        owner.startThread {
            withLock {
                started = true
                condition.signalAll()
            }

            action.run()

            withLock {
                completed = true
                condition.signalAll()
            }
        }

        withLock {
            while (!started) {
                condition.await()
            }
        }
    }

    private void onFailure(Throwable t) {
        withLock {
            failure = t
            condition.signalAll()
        }
    }

    private void waitForCallbackToBeRegistered() {
        Date expiry = shortTimeout()
        withLock {
            while (!callback && !failure && !completed) {
                if (!condition.awaitUntil(expiry)) {
                    throw new IllegalStateException("Expected action to register a callback action, but it did not.")
                }
            }
            if (failure) {
                throw failure
            }
            if (completed) {
                throw new IllegalStateException("Expected action to block, but it did not.")
            }
        }
    }

    private void assertBlocked() {
        withLock {
            if (completed) {
                throw new IllegalStateException("Expected action to block, but it did not.")
            }
        }
    }
}
