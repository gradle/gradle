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
package org.gradle.listener.dispatch;

import org.gradle.api.GradleException;

import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link org.gradle.listener.dispatch.Dispatch} implementation which delivers messages asynchronously. Calls to
 * {@link #dispatch} queue the message. Worker threads delivers the messages in order to one of a pool of delegate
 * {@link org.gradle.listener.dispatch.Dispatch} instances.
 */
public class AsyncDispatch<T> implements StoppableDispatch<T> {
    private enum State {
        Init, Stopped
    }

    private static final int MAX_QUEUE_SIZE = 200;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final LinkedList<T> queue = new LinkedList<T>();
    private final Executor executor;
    private final int maxQueueSize;
    private int targets;
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
            add(dispatch);
        }
    }

    public void add(final Dispatch<? super T> dispatch) {
        onDispatchThreadStart();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    dispatchMessages(dispatch);
                } finally {
                    onDispatchThreadExit();
                }
            }
        });
    }

    private void onDispatchThreadStart() {
        lock.lock();
        try {
            targets++;
        } finally {
            lock.unlock();
        }
    }

    private void onDispatchThreadExit() {
        lock.lock();
        try {
            targets--;
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
                while (state == State.Init && queue.isEmpty()) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw new GradleException(e);
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
            while (state == State.Init && queue.size() >= maxQueueSize) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new GradleException(e);
                }
            }
            if (state == State.Stopped) {
                throw new IllegalStateException("This message dispatch has been stopped.");
            }
            queue.add(message);
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        lock.lock();
        try {
            setState(State.Stopped);
            Date expiry = new Date(System.currentTimeMillis() + (120 * 1000));
            while (targets > 0) {
                try {
                    if (!condition.awaitUntil(expiry)) {
                        throw new IllegalStateException("Timeout waiting for messages to be flushed.");
                    }
                } catch (InterruptedException e) {
                    throw new GradleException(e);
                }
            }
            if (!queue.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot wait for messages to be flushed, as there are no dispatch threads.");
            }
        } finally {
            lock.unlock();
        }
    }
}
