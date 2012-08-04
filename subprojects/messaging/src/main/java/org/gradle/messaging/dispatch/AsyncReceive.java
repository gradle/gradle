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

package org.gradle.messaging.dispatch;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.AsyncStoppable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Receives messages asynchronously. One or more {@link Receive} instances can use used as a source of messages. Messages are sent to a {@link Dispatch} </p>
 * 
 * <p>It is also possible to specify an <code>onReceiversExhausted</code> Runnable callback that will be run when all of the given receivers
 * have been exhausted of messages. However, the current implementation is flawed in that this may erroneously fire if the first receiver
 * is exhausted before the second starts.
 */
public class AsyncReceive<T> implements AsyncStoppable {
    private enum State {
        Init, Stopping, Stopped
    }

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Executor executor;
    private final List<Dispatch<? super T>> dispatches = new ArrayList<Dispatch<? super T>>();
    private final Runnable onReceiversExhausted;
    private int receivers;
    private State state = State.Init;

    public AsyncReceive(Executor executor) {
        this(executor, (Runnable)null);
    }

    public AsyncReceive(Executor executor, Runnable onReceiversExhausted) {
        this.executor = executor;
        this.onReceiversExhausted = onReceiversExhausted;
    }

    public AsyncReceive(Executor executor, final Dispatch<? super T> dispatch) {
        this(executor, dispatch, null);
    }

    public AsyncReceive(Executor executor, final Dispatch<? super T> dispatch, Runnable onReceiversExhausted) {
        this(executor, onReceiversExhausted);
        dispatchTo(dispatch);
    }

    /**
     * Starts dispatching to the given dispatch. The dispatch does not need be thread-safe.
     */
    public void dispatchTo(final Dispatch<? super T> dispatch) {
        lock.lock();
        try {
            dispatches.add(dispatch);
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Starts receiving from the given receive. The receive does not need to be thread-safe.
     */
    public void receiveFrom(final Receive<? extends T> receive) {
        onReceiveThreadStart();
        executor.execute(new Runnable() {
            public void run() {
                try {
                    receiveMessages(receive);
                } finally {
                    onReceiveThreadExit();
                }
            }
        });
    }

    private void onReceiveThreadStart() {
        lock.lock();
        try {
            if (state != State.Init) {
                throw new IllegalStateException("This receiver has been stopped.");
            }
            receivers++;
        } finally {
            lock.unlock();
        }
    }

    private void onReceiveThreadExit() {
        lock.lock();
        try {
            receivers--;
            if (receivers == 0 && onReceiversExhausted != null) {
                onReceiversExhausted.run();
            }
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void receiveMessages(Receive<? extends T> receive) {
        while (true) {
            Dispatch<? super T> dispatch;
            lock.lock();
            try {
                while (dispatches.isEmpty() && state == State.Init) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                if (state != State.Init) {
                    return;
                }
                dispatch = dispatches.remove(0);
            } finally {
                lock.unlock();
            }

            try {
                T message = receive.receive();
                if (message == null) {
                    return;
                }

                dispatch.dispatch(message);
            } finally {
                lock.lock();
                try {
                    dispatches.add(dispatch);
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private void setState(State state) {
        this.state = state;
        condition.signalAll();
    }

    /**
     * Stops receiving new messages.
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
        if (receivers > 0) {
            setState(State.Stopping);
        } else {
            setState(State.Stopped);
        }
    }

    /**
     * Stops receiving new messages. Blocks until all queued messages have been delivered.
     */
    public void stop() {
        lock.lock();
        try {
            doRequestStop();

            while (receivers > 0) {
                condition.await();
            }

            setState(State.Stopped);
        } catch (InterruptedException e) {
            throw new UncheckedException(e);
        } finally {
            lock.unlock();
        }
    }
}
