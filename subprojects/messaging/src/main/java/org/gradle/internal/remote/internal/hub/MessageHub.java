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

package org.gradle.internal.remote.internal.hub;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.dispatch.BoundedDispatch;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.remote.internal.Connection;
import org.gradle.internal.remote.internal.RecoverableMessageIOException;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.remote.internal.hub.protocol.*;
import org.gradle.internal.remote.internal.hub.queue.EndPointQueue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A multi-channel message router.
 *
 * Use {@link #getOutgoing(String, Class)} to create a {@link Dispatch} to send unicast messages on a given channel.
 * Use {@link #addHandler(String, Object)} to create a worker for incoming messages on a given channel.
 * Use {@link #addConnection(RemoteConnection)} to attach another router to this router.
 *
 */
public class MessageHub implements AsyncStoppable {
    private enum State {Running, Stopping, Stopped}

    private static final Discard DISCARD = new Discard();
    private final StoppableExecutor workers;
    private final String displayName;
    private final Action<? super Throwable> errorHandler;
    private final Lock lock = new ReentrantLock();
    private State state = State.Running;
    private final IncomingQueue incomingQueue = new IncomingQueue(lock);
    private final OutgoingQueue outgoingQueue = new OutgoingQueue(incomingQueue, lock);
    private final ConnectionSet connections = new ConnectionSet(incomingQueue, outgoingQueue);

    /**
     * @param errorHandler Notified when some asynch. activity fails. Must be thread-safe.
     */
    public MessageHub(String displayName, ExecutorFactory executorFactory, Action<? super Throwable> errorHandler) {
        this.displayName = displayName;
        this.errorHandler = errorHandler;
        workers = executorFactory.create(displayName + " workers");
    }

    /**
     * <p>Adds a {@link Dispatch} implementation that can be used to send outgoing unicast messages on the given channel. Messages are queued in the order that they are
     * dispatched, and are forwarded to at most one handler.</p>
     *
     * <p>The returned value is thread-safe.</p>
     */
    public <T> Dispatch<T> getOutgoing(final String channelName, final Class<T> type) {
        lock.lock();
        try {
            assertRunning("create outgoing dispatch");
            return new ChannelDispatch<T>(type, new ChannelIdentifier(channelName));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a handler for messages on the given channel. The handler may implement any of the following:
     *
     * <ul>
     *
     * <li>{@link Dispatch} to handle incoming messages received from any connections attached to this hub. Each incoming message is passed to exactly one handler associated to the given channel.
     * </li>
     *
     * <li>{@link RejectedMessageListener} to receive notifications of outgoing messages that cannot be sent on the given channel.</li>
     *
     * <li>{@link BoundedDispatch} to receive notifications of the end of incoming messages.</li>
     *
     * </ul>
     *
     * <p>The given handler does not need to be thread-safe, and is notified by at most one thread at a time. Multiple handlers can be added for a given channel.</p>
     *
     * <p>NOTE: If any method of the handler fails with an exception, the handler is discarded and will receive no further notifications.</p>
     */
    public void addHandler(String channelName, Object handler) {
        lock.lock();
        try {
            assertRunning("add handler");

            RejectedMessageListener rejectedMessageListener;
            if (handler instanceof RejectedMessageListener) {
                rejectedMessageListener = (RejectedMessageListener) handler;
            } else {
                rejectedMessageListener = DISCARD;
            }
            Dispatch<Object> dispatch;
            if (handler instanceof Dispatch) {
                dispatch = (Dispatch) handler;
            } else {
                dispatch = DISCARD;
            }
            BoundedDispatch<Object> boundedDispatch;
            if (dispatch instanceof BoundedDispatch) {
                boundedDispatch = (BoundedDispatch) dispatch;
            } else {
                boundedDispatch = DISCARD;
            }
            StreamFailureHandler streamFailureHandler;
            if (handler instanceof StreamFailureHandler) {
                streamFailureHandler = (StreamFailureHandler) handler;
            } else {
                streamFailureHandler = DISCARD;
            }
            ChannelIdentifier identifier = new ChannelIdentifier(channelName);
            EndPointQueue queue = incomingQueue.getChannel(identifier).newEndpoint();
            workers.execute(new Handler(queue, dispatch, boundedDispatch, rejectedMessageListener, streamFailureHandler));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a connection to some other message hub. Outgoing messages are forwarded to this connection, and incoming messages are received from it.
     *
     * <p>Does not cleanup connections on stop or disconnect. It is the caller's responsibility to manage the connection lifecycle.</p>
     */
    public void addConnection(RemoteConnection<InterHubMessage> connection) {
        lock.lock();
        try {
            assertRunning("add connection");
            ConnectionState connectionState = connections.add(connection);
            workers.execute(new ConnectionDispatch(connectionState));
            workers.execute(new ConnectionReceive(connectionState));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Signals that no further connections will be added.
     */
    public void noFurtherConnections() {
        lock.lock();
        try {
            connections.noFurtherConnections();
        } finally {
            lock.unlock();
        }
    }

    private void assertRunning(String action) {
        if (state != State.Running) {
            throw new IllegalStateException(String.format("Cannot %s, as %s has been stopped.", action, displayName));
        }
    }

    /**
     * Requests that this message hub commence shutting down. Does the following:
     *
     * <ul>
     *
     * <li>Stops accepting any further outgoing messages.</li>
     *
     * <li>If no connections are available, dispatches queued messages to any handlers that implement {@link RejectedMessageListener}.</li>
     *
     * </ul>
     */
    public void requestStop() {
        lock.lock();
        try {
            if (state != State.Running) {
                return;
            }
            try {
                outgoingQueue.endOutput();
                connections.noFurtherConnections();
            } finally {
                state = State.Stopping;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Requests that this message hub stop. First requests stop as per {@link #requestStop()}, then blocks until stop has completed. This means that:
     *
     * <ul>
     *
     * <li>All calls to {@link Dispatch#dispatch(Object)} for outgoing messages have returned.</li>
     *
     * <li>All dispatches to handlers have completed.</li>
     *
     * <li>All internal threads have completed.</li>
     *
     * </ul>
     */
    public void stop() {
        try {
            lock.lock();
            try {
                requestStop();
            } finally {
                lock.unlock();
            }
            workers.stop();
        } finally {
            lock.lock();
            try {
                state = State.Stopped;
            } finally {
                lock.unlock();
            }
        }
    }

    private static class Discard implements BoundedDispatch<Object>, RejectedMessageListener, StreamFailureHandler {
        public void dispatch(Object message) {
        }

        @Override
        public void endStream() {
        }

        public void messageDiscarded(Object message) {
        }

        @Override
        public void handleStreamFailure(Throwable t) {
        }
    }

    private class ConnectionReceive implements Runnable {
        private final Connection<InterHubMessage> connection;
        private final ConnectionState connectionState;

        public ConnectionReceive(ConnectionState connectionState) {
            this.connection = connectionState.getConnection();
            this.connectionState = connectionState;
        }

        public void run() {
            try {
                try {
                    while (true) {
                        InterHubMessage message;
                        try {
                            message = connection.receive();
                        } catch (RecoverableMessageIOException e) {
                            addToIncoming(new StreamFailureMessage(e));
                            continue;
                        }
                        if (message == null || message instanceof EndOfStream) {
                            return;
                        }
                        addToIncoming(message);
                    }
                } finally {
                    lock.lock();
                    try {
                        connectionState.receiveFinished();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Throwable e) {
                errorHandler.execute(e);
            }
        }
    }

    private void addToIncoming(InterHubMessage message) {
        lock.lock();
        try {
            incomingQueue.queue(message);
        } finally {
            lock.unlock();
        }
    }

    private class ConnectionDispatch implements Runnable {
        private final RemoteConnection<InterHubMessage> connection;
        private final EndPointQueue queue;
        private final ConnectionState connectionState;

        private ConnectionDispatch(ConnectionState connectionState) {
            this.connection = connectionState.getConnection();
            this.queue = connectionState.getDispatchQueue();
            this.connectionState = connectionState;
        }

        public void run() {
            try {
                List<InterHubMessage> messages = new ArrayList<InterHubMessage>();
                try {
                    while (true) {
                        lock.lock();
                        try {
                            queue.take(messages);
                        } finally {
                            lock.unlock();
                        }
                        for (InterHubMessage message : messages) {
                            try {
                                connection.dispatch(message);
                            } catch (RecoverableMessageIOException e) {
                                addToIncoming(new StreamFailureMessage(e));
                            }
                            if (message instanceof EndOfStream) {
                                connection.flush();
                                return;
                            }
                        }
                        connection.flush();
                        messages.clear();
                    }
                } finally {
                    lock.lock();
                    try {
                        connectionState.dispatchFinished();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Throwable t) {
                errorHandler.execute(t);
            }
        }
    }

    private class ChannelDispatch<T> implements Dispatch<T> {
        private final Class<T> type;
        private final ChannelIdentifier channelIdentifier;

        public ChannelDispatch(Class<T> type, ChannelIdentifier channelIdentifier) {
            this.type = type;
            this.channelIdentifier = channelIdentifier;
        }

        @Override
        public String toString() {
            return "Dispatch " + type.getSimpleName() + " to " + displayName + " channel " + channelIdentifier;
        }

        public void dispatch(T message) {
            lock.lock();
            try {
                assertRunning("dispatch message");
                outgoingQueue.dispatch(new ChannelMessage(channelIdentifier, message));
            } finally {
                lock.unlock();
            }
        }
    }

    private class Handler implements Runnable {
        private final EndPointQueue queue;
        private final Dispatch<Object> dispatch;
        private final BoundedDispatch<Object> boundedDispatch;
        private final RejectedMessageListener listener;
        private final StreamFailureHandler streamFailureHandler;

        public Handler(EndPointQueue queue, Dispatch<Object> dispatch, BoundedDispatch<Object> boundedDispatch, RejectedMessageListener listener, StreamFailureHandler streamFailureHandler) {
            this.queue = queue;
            this.dispatch = dispatch;
            this.boundedDispatch = boundedDispatch;
            this.listener = listener;
            this.streamFailureHandler = streamFailureHandler;
        }

        public void run() {
            try {
                List<InterHubMessage> messages = new ArrayList<InterHubMessage>();
                try {
                    while (true) {
                        lock.lock();
                        try {
                            queue.take(messages);
                        } finally {
                            lock.unlock();
                        }
                        for (InterHubMessage message : messages) {
                            if (message instanceof EndOfStream) {
                                boundedDispatch.endStream();
                                return;
                            }
                            if (message instanceof ChannelMessage) {
                                ChannelMessage channelMessage = (ChannelMessage) message;
                                dispatch.dispatch(channelMessage.getPayload());
                            } else if (message instanceof RejectedMessage) {
                                RejectedMessage rejectedMessage = (RejectedMessage) message;
                                listener.messageDiscarded(rejectedMessage.getPayload());
                            } else if (message instanceof StreamFailureMessage){
                                StreamFailureMessage streamFailureMessage = (StreamFailureMessage) message;
                                streamFailureHandler.handleStreamFailure(streamFailureMessage.getFailure());
                            } else {
                                throw new IllegalArgumentException(String.format("Don't know how to handle message %s", message));
                            }
                        }
                        messages.clear();
                    }
                } finally {
                    lock.lock();
                    try {
                        queue.stop();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (Throwable t) {
                errorHandler.execute(t);
            }
        }
    }
}
