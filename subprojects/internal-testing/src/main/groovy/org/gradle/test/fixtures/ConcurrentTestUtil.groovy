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
package org.gradle.test.fixtures

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.StoppableExecutor
import org.gradle.internal.concurrent.StoppableScheduledExecutor
import org.junit.rules.ExternalResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * <p>A base class for writing specifications which exercise concurrent code.
 *
 * <p>See {@link org.gradle.util.ConcurrentSpecificationTest} for some examples.
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
class ConcurrentTestUtil extends ExternalResource {
    private static final Logger LOG = LoggerFactory.getLogger(ConcurrentTestUtil.class)

    private Lock lock = new ReentrantLock()
    private Condition threadsChanged = lock.newCondition()
    private Set<TestThread> threads = [] as Set
    private Closure failureHandler
    private List<Throwable> failures = []
    private timeout = 5000

    ConcurrentTestUtil() {}

    ConcurrentTestUtil(int timeout) {
        this.timeout = timeout
    }

    @Override
    protected void after() {
        finished()
    }

    //simplistic polling assertion. attempts asserting every x millis up to some max timeout
    static void poll(double timeout = 10, double initialDelay = 0, Closure assertion) {
        def start = monotonicClockMillis()
        Thread.sleep(toMillis(initialDelay))
        def expiry = start + toMillis(timeout) // convert to ms
        long sleepTime = 100
        while(true) {
            try {
                assertion()
                return
            } catch (Throwable t) {
                if (monotonicClockMillis() > expiry) {
                    throw t
                }
                sleepTime = Math.min(250, (long) (sleepTime * 1.2))
                Thread.sleep(sleepTime);
            }
        }
    }

    static long monotonicClockMillis() {
        System.nanoTime() / 1000000L
    }

    static long toMillis(double seconds) {
        return (long) (seconds * 1000);
    }

    void setShortTimeout(int millis) {
        this.timeout = millis
    }

    ExecutorFactory getExecutorFactory() {
        return new ExecutorFactory() {
            StoppableExecutor create(String displayName) {
                return new StoppableExecutorStub(ConcurrentTestUtil.this)
            }

            StoppableExecutor create(String displayName, int fixedSize) {
                // Ignores size of thread pool
                return new StoppableExecutorStub(ConcurrentTestUtil.this)
            }

            StoppableScheduledExecutor createScheduled(String displayName, int fixedSize) {
                throw new UnsupportedOperationException()
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
    StartAsyncAction startsAsyncAction() {
        return new StartAsyncAction(this)
    }

    /**
     * Creates a new blocking action.
     */
    WaitForAsyncCallback waitsForAsyncCallback() {
        return new WaitForAsyncCallback(this)
    }

    /**
     * Creates a new action which waits until an async. action is complete.
     */
    WaitForAsyncAction waitsForAsyncActionToComplete() {
        return new WaitForAsyncAction(this)
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

    private void failed(Throwable t) {
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
                    failed(new IllegalStateException("Timeout waiting for test threads to complete."))
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

    Date shortTimeout() {
        return new Date(System.currentTimeMillis() + timeout)
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
                failed(failure)
            }
            threadsChanged.signalAll()
        } finally {
            lock.unlock()
        }
    }
}

class TestThread extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(TestThread.class)
    private final ConcurrentTestUtil owner
    private final Runnable action
    private final Lock lock
    private final Condition stateChanged
    private boolean complete

    TestThread(ConcurrentTestUtil owner, Lock lock, Runnable action) {
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

    Date defaultExpiry

    AbstractAction(Date defaultExpiry) {
        this.defaultExpiry = defaultExpiry
    }

    void completed() {
        completesBefore(defaultExpiry)
    }

    void completesWithin(long maxWaitValue, TimeUnit maxWaitUnits) {
        Date expiry = new Date(System.currentTimeMillis() + maxWaitUnits.toMillis(maxWaitValue))
        completesBefore(expiry + 500)
    }

    abstract void completesBefore(Date timeout)
}

abstract class AbstractTestParticipant extends AbstractAction implements TestParticipant {
    private final ConcurrentTestUtil owner

    AbstractTestParticipant(ConcurrentTestUtil owner) {
        super(owner.shortTimeout())
        this.owner = owner
    }
}

class TestParticipantImpl extends AbstractTestParticipant {
    private final TestThread thread

    TestParticipantImpl(ConcurrentTestUtil owner, TestThread thread) {
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

    CompositeTestParticipant(ConcurrentTestUtil owner, Lock lock, List<TestParticipant> participants) {
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

class StoppableExecutorStub extends AbstractExecutorService implements StoppableExecutor {
    final ConcurrentTestUtil owner
    final Set<TestThread> threads = new CopyOnWriteArraySet<TestThread>()

    StoppableExecutorStub(ConcurrentTestUtil owner) {
        this.owner = owner
    }

    void stop() {
        def timeout = owner.shortTimeout()
        threads.each { it.completesBefore(timeout) }
    }

    void stop(int timeoutValue, TimeUnit timeoutUnits) {
        throw new UnsupportedOperationException()
    }

    void requestStop() {
        throw new UnsupportedOperationException()
    }

    void execute(Runnable runnable) {
        threads.add(owner.startThread(runnable))
    }

    void shutdown() {
        throw new UnsupportedOperationException()
    }

    List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException()
    }

    boolean isShutdown() {
        throw new UnsupportedOperationException()
    }

    boolean isTerminated() {
        throw new UnsupportedOperationException()
    }

    boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        throw new UnsupportedOperationException()
    }
}

class AbstractAsyncAction {
    protected final ConcurrentTestUtil owner
    private final Lock lock = new ReentrantLock()
    protected final Condition condition = lock.newCondition()
    protected Throwable failure

    AbstractAsyncAction(ConcurrentTestUtil owner) {
        this.owner = owner
    }

    protected Date shortTimeout() {
        return owner.shortTimeout()
    }

    protected void onFailure(Throwable throwable) {
        withLock {
            failure = throwable
            condition.signalAll()
        }
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

class StartAsyncAction extends AbstractAsyncAction {
    private boolean started
    private boolean completed
    private Thread startThread

    StartAsyncAction(ConcurrentTestUtil owner) {
        super(owner)
    }

    /**
     * Runs the given action, and then waits until another thread calls {@link #done()}.  Asserts that the start action does not block waiting for
     * the async action to complete.
     *
     * @param action The start action
     * @return this
     */
    StartAsyncAction started(Runnable action) {
        owner.onFailure this.&onFailure
        doStart(action)
        waitForStartToComplete()
        waitForFinish()
        return this
    }

    /**
     * Marks that the async. action is now finished.
     */
    void done() {
        waitForStartToComplete()
        doFinish()
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
                throw new IllegalStateException("Cannot run async action multiple times.")
            }
            completed = true
            condition.signalAll()
        }
    }

    private void waitForStartToComplete() {
        Date timeout = shortTimeout()
        withLock {
            if (startThread == null) {
                def e = new IllegalStateException("Action has not been started.")
                e.printStackTrace()
                throw e
            }
            if (Thread.currentThread() == startThread) {
                def e = new IllegalStateException("Cannot wait for action to complete from the thread that is executing it.")
                e.printStackTrace()
                throw e
            }
            while (!started && !failure) {
                if (!condition.awaitUntil(timeout)) {
                    throw new IllegalStateException("Expected action to complete quickly, but it did not.")
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

abstract class AbstractWaitAction extends AbstractAsyncAction {
    protected boolean started
    protected boolean completed

    AbstractWaitAction(ConcurrentTestUtil owner) {
        super(owner)
    }

    protected void waitForBlockingActionToComplete() {
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

    protected void startBlockingAction(Runnable action) {
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

    protected void assertBlocked() {
        withLock {
            if (completed) {
                throw new IllegalStateException("Expected action to block, but it did not.")
            }
        }
    }
}

class WaitForAsyncCallback extends AbstractWaitAction {
    private boolean callbackCompleted
    private Runnable callback

    WaitForAsyncCallback(ConcurrentTestUtil owner) {
        super(owner)
    }

    /**
     * Runs the given action. Asserts that it blocks until after asynchronous callback is made. The action must register the callback using {@link #callbackLater(Runnable)}.
     */
    WaitForAsyncCallback start(Runnable action) {
        owner.onFailure this.&onFailure

        startBlockingAction(action)
        waitForCallbackToBeRegistered()

        Thread.sleep(500)

        assertBlocked()
        runCallbackAction()
        waitForBlockingActionToComplete()

        return this
    }

    /**
     * Registers the callback which will unblock the action.
     */
    public void callbackLater(Runnable action) {
        withLock {
            if (callback) {
                throw new IllegalStateException("Cannot register callback action multiple times.")
            }
            if (!started) {
                throw new IllegalStateException("Action has not been started.")
            }
            callback = action
            condition.signalAll()
        }
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
}

class WaitForAsyncAction extends AbstractWaitAction {
    boolean asyncActionComplete

    WaitForAsyncAction(ConcurrentTestUtil owner) {
        super(owner)
    }

    WaitForAsyncAction start(Runnable action) {
        owner.onFailure this.&onFailure
        startBlockingAction(action)
        waitForAsyncAction()
        waitForBlockingActionToComplete()
        return this
    }

    WaitForAsyncAction done() {
        Thread.sleep(500)
        assertBlocked()

        withLock {
            asyncActionComplete = true
            condition.signalAll()
        }

        return this
    }

    def waitForAsyncAction() {
        Date expiry = shortTimeout()
        withLock {
            while (!asyncActionComplete && !completed && !failure) {
                if (!condition.awaitUntil(expiry)) {
                    throw new IllegalStateException("Expected async action to be started, but it was not.")
                }
            }
            if (failure) {
                throw failure
            }
            if (!asyncActionComplete && completed) {
                throw new IllegalStateException("Expected action to block, but it did not.")
            }
        }
    }
}
