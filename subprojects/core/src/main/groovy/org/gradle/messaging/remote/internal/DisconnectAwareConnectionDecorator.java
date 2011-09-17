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
package org.gradle.messaging.remote.internal;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import org.gradle.api.Action;
import org.gradle.util.UncheckedException;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.List;
import java.util.LinkedList;

/**
 * A {@link DisconnectAwareConnection} implementation that decorates an existing connection, adding disconnect awareness.
 * <p>
 * A side effect of using this decorator is that messages will be consumed as quickly as possible into an in memory
 * buffer, which the receive() method collects from.
 */
public class DisconnectAwareConnectionDecorator<T> extends DelegatingConnection<T> implements DisconnectAwareConnection<T> {

    private static final Logger LOGGER = Logging.getLogger(DisconnectAwareConnectionDecorator.class);

    private final Lock actionLock = new ReentrantLock();
    private final Lock receiveLock = new ReentrantLock();

    private final StoppableExecutor consumerExecutor = new DefaultExecutorFactory().create("DisconnectAwareConnectionDecorator Consumer");

    private volatile boolean stopped;

    /**
     * A wrapper for messages, as a way to have an end-of-stream sentinel (i.e. a MessageWrapper with a null message)
     * Necessary to communicate to the receive end that there's no more coming.
     */
    private class MessageWrapper<T> {
        public final T message;

        MessageWrapper(T message) {
            this.message = message;
        }
    }

    private final LinkedBlockingQueue<MessageWrapper<T>> messages = new LinkedBlockingQueue<MessageWrapper<T>>();

    private Action<Disconnection<T>> disconnectAction;

    public DisconnectAwareConnectionDecorator(Connection<T> connection) {
        super(connection);
        startConsuming();
    }

    public Action<Disconnection<T>> onDisconnect(Action<Disconnection<T>> disconnectAction) {
        actionLock.lock();
        try {
            Action<Disconnection<T>> previous = disconnectAction;
            this.disconnectAction = disconnectAction;
            return previous;
        } finally {
            actionLock.unlock();
        }
    }

    public T receive() {
        receiveLock.lock();
        try {
            // acquire lock and relenquish in order to wait for onDisconnect() to complete
            // if it's in progress (to ensure the disconnect handler fires before we receive the null)
            actionLock.lock();
            actionLock.unlock();

            T received = take();
            if (received == null) {
                assert messages.size() == 0; // There are no more messages coming, there should be no more messages
                put(null); // Put this one back for the next receive() call
                return null;
            } else {
                return received;
            }
        } finally {
            receiveLock.unlock();
        }
    }

    private T take() {
        try {
            return messages.take().message;
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private void put(T message) {
        try {
            messages.put(new MessageWrapper(message));
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    private void startConsuming() {
        consumerExecutor.execute(new Runnable() {
            public void run() {
                LOGGER.info("starting disconnect aware consumer {}", this);
                while (true) {
                    T message = DisconnectAwareConnectionDecorator.super.receive(); // will return null if underlying connection stopped
                    if (message == null) {
                        List<T> uncollected = new LinkedList<T>();
                        if (!stopped) {
                            List<MessageWrapper<T>> wrappers = new LinkedList<MessageWrapper<T>>();
                            messages.drainTo(wrappers);
                            for (MessageWrapper<T> wrapper : wrappers) {
                                uncollected.add(wrapper.message);
                            }
                        }

                        // put the end-of-stream sentinel on the queue
                        put(null);

                        if (!stopped) {
                            invokeDisconnectAction(uncollected);
                        }

                        break;
                    } else {
                        put(message);
                    }
                }
            }
        });
    }

    private void invokeDisconnectAction(final List<T> uncollectedMessages) {
        actionLock.lock();
        try {
            if (disconnectAction != null) {
                disconnectAction.execute(new Disconnection() {
                    public Connection<T> getConnection() { return DisconnectAwareConnectionDecorator.this; }
                    public List<T> getUncollectedMessages() { return uncollectedMessages; }
                });
            }
        } finally {
            actionLock.unlock();
        }
    }

    public void requestStop() {
        stopped = true;
        super.requestStop();
    }

    public void stop() {
        stopped = true;
        super.stop();
    }

}