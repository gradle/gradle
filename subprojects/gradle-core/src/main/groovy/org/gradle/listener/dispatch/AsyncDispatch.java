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
package org.gradle.listener.dispatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AsyncDispatch<T extends Message> implements CloseableDispatch<T> {
    private static final int MAX_QUEUE_SIZE = 200;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final LinkedList<T> queue = new LinkedList<T>();
    private final int maxQueueSize;
    private boolean closed;
    private boolean dispatching = true;

    public AsyncDispatch(Executor executor, final Dispatch<? super T> dispatch) {
        this(executor, dispatch, MAX_QUEUE_SIZE);
    }

    public AsyncDispatch(Executor executor, final Dispatch<? super T> dispatch, int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        executor.execute(new Runnable() {
            public void run() {
                dispatchThreadMain(dispatch);
            }
        });
    }

    private void dispatchThreadMain(Dispatch<? super T> dispatch) {
        try {
            dispatchMessages(dispatch);
        } finally {
            onDispatchThreadExit();
        }
    }

    private void onDispatchThreadExit() {
        lock.lock();
        try {
            dispatching = false;
        } finally {
            lock.unlock();
        }
    }

    private void dispatchMessages(Dispatch<? super T> dispatch) {
        while (true) {
            List<T> messages = new ArrayList<T>();
            lock.lock();
            try {
                while (!closed && queue.isEmpty()) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                if (!queue.isEmpty()) {
                    messages.addAll(queue);
                }
            } finally {
                lock.unlock();
            }

            if (messages.isEmpty()) {
                // Have been closed
                return;
            }

            for (T message : messages) {
                dispatch.dispatch(message);
            }

            lock.lock();
            try {
                queue.subList(0, messages.size()).clear();
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    public void dispatch(final T message) {
        lock.lock();
        try {
            while (dispatching && !closed && queue.size() >= maxQueueSize) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (closed) {
                throw new IllegalStateException("This message dispatch has been closed.");
            }
            if (!dispatching) {
                throw new IllegalStateException("Cannot dispatch messages, as the dispatch thread has exited.");
            }
            queue.add(message);
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void close() {
        lock.lock();
        try {
            closed = true;
            condition.signalAll();
            Date expiry = new Date(System.currentTimeMillis() + (120 * 1000));
            while (dispatching && !queue.isEmpty()) {
                try {
                    if (!condition.awaitUntil(expiry)) {
                        throw new IllegalStateException("Timeout waiting for messages to be flushed.");
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            if (!queue.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot wait for messages to be flushed, as the dispatch thread has exited.");
            }
        } finally {
            lock.unlock();
        }
    }
}
