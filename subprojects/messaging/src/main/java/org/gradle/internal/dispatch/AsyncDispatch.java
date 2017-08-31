/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.dispatch;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.internal.operations.BuildOperationIdentifierPreservingRunnable;

import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>A {@link Dispatch} implementation which delivers messages asynchronously. Calls to
 * {@link #dispatch} queue the message. Worker threads deliver the messages in the order they have been received to one
 * of a pool of delegate {@link Dispatch} instances.</p>
 */
public class AsyncDispatch<T> implements Dispatch<T>, AsyncStoppable {
    private enum State {
        Init, Stopped
    }

    private static final int MAX_QUEUE_SIZE = 200;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final LinkedList<T> queue = new LinkedList<T>();
    private final Executor executor;
    private final int maxQueueSize;
    private int dispatchers;
    private State state;

    public AsyncDispatch(Executor executor) {
        this(executor, null, MAX_QUEUE_SIZE);
    }

    public AsyncDispatch(Executor executor, final Dispatch<? super T> dispatch) {
        this(executor, dispatch, MAX_QUEUE_SIZE);
    }

    public AsyncDispatch(Executor executor, final Dispatch<? super T> dispatch, int maxQueueSize) {
        this.executor = executor;
        this.maxQueueSize = maxQueueSize;
        state = State.Init;
        if (dispatch != null) {
            dispatchTo(dispatch);
        }
    }

    /**
     * Starts dispatching messages to the given handler. The handler does not need to be thread-safe.
     */
    public void dispatchTo(final Dispatch<? super T> dispatch) {
        onDispatchThreadStart();
        executor.execute(new BuildOperationIdentifierPreservingRunnable(new Runnable() {
            public void run() {
                try {
                    dispatchMessages(dispatch);
                } finally {
                    onDispatchThreadExit();
                }
            }
        }));
    }

    private void onDispatchThreadStart() {
        lock.lock();
        try {
            if (state != State.Init) {
                throw new IllegalStateException("This dispatch has been stopped.");
            }
            dispatchers++;
        } finally {
            lock.unlock();
        }
    }

    private void onDispatchThreadExit() {
        lock.lock();
        try {
            dispatchers--;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void setState(State state) {
        this.state = state;
        condition.signalAll();
    }

    private void dispatchMessages(Dispatch<? super T> dispatch) {
        while (true) {
            T message = null;
            lock.lock();
            try {
                while (state != State.Stopped && queue.isEmpty()) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw new UncheckedException(e);
                    }
                }
                if (!queue.isEmpty()) {
                    message = queue.remove();
                    condition.signalAll();
                }
            } finally {
                lock.unlock();
            }

            if (message == null) {
                // Have been stopped and nothing to deliver
                return;
            }

            dispatch.dispatch(message);
        }
    }

    public void dispatch(final T message) {
        lock.lock();
        try {
            while (state != State.Stopped && queue.size() >= maxQueueSize) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new UncheckedException(e);
                }
            }
            if (state == State.Stopped) {
                throw new IllegalStateException("Cannot dispatch message, as this message dispatch has been stopped. Message: " + message);
            }
            queue.add(message);
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Commences a shutdown of this dispatch.
     */
    public void requestStop() {
        lock.lock();
        try {
            doRequestStop();
        } finally {
            lock.unlock();
        }
    }

    private void doRequestStop() {
        setState(State.Stopped);
    }

    /**
     * Stops accepting new messages, and blocks until all queued messages have been dispatched.
     */
    public void stop() {
        lock.lock();
        try {
            setState(State.Stopped);
            while (dispatchers > 0) {
                condition.await();
            }

            if (!queue.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot wait for messages to be dispatched, as there are no dispatch threads running.");
            }
        } catch (InterruptedException e) {
            throw new UncheckedException(e);
        } finally {
            lock.unlock();
        }
    }
}
