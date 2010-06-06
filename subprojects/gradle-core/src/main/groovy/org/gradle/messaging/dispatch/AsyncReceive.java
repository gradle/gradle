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

import org.gradle.messaging.concurrent.AsyncStoppable;
import org.gradle.util.UncheckedException;

import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Receives messages asynchronously. One or more {@link Receive} instances can use used as a source of messages.
 * Messages are sent to a {@link Dispatch} </p>
 */
public class AsyncReceive<T> implements AsyncStoppable {
    private enum State {
        Init, Stopping, Stopped
    }

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Executor executor;
    private final Dispatch<? super T> dispatch;
    private int receivers;
    private State state = State.Init;

    public AsyncReceive(Executor executor, final Dispatch<? super T> dispatch) {
        this.executor = executor;
        this.dispatch = dispatch;
    }

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
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void receiveMessages(Receive<? extends T> receive) {
        while (true) {
            T message = receive.receive();
            if (message == null) {
                return;
            }

            dispatch.dispatch(message);

            lock.lock();
            try {
                if (state != State.Init) {
                    return;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void setState(State state) {
        this.state = state;
        condition.signalAll();
    }

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
