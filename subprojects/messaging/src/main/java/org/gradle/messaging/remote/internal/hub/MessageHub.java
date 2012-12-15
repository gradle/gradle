package org.gradle.messaging.remote.internal.hub;

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelIdentifier;
import org.gradle.messaging.remote.internal.hub.protocol.ChannelMessage;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A multi-channel message dispatcher.
 */
public class MessageHub implements AsyncStoppable {
    private enum State {Running, Stopping, Stopped}

    private static final Object END_OF_STREAM = new Object();
    private final StoppableExecutor workers;
    private final String displayName;
    private final Action<? super Throwable> errorHandler;
    private final Lock lock = new ReentrantLock();
    private State state = State.Running;
    private final OutgoingQueue outgoingQueue = new OutgoingQueue();

    public MessageHub(String displayName, ExecutorFactory executorFactory, Action<? super Throwable> errorHandler) {
        this.displayName = displayName;
        this.errorHandler = errorHandler;
        workers = executorFactory.create(String.format("%s workers", displayName));
    }

    /**
     * <p>Adds a {@link Dispatch} implementation that can be used to send outgoing messages on the given channel. The returned value is thread-safe.</p>
     *
     * <p>All messages sent via the dispatch are forwarded to exactly one connection.</p>
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
     * Adds a handler for messages on the given channel. The handler may implement {@link RejectedMessageListener} to receive notifications of outgoing messages that cannot be sent on the given
     * channel.
     *
     * <p>The given handler does not need to be thread-safe, and is notified by at most one thread at a time.</p>
     */
    public void addHandler(String channelName, Object handler) {
        lock.lock();
        try {
            assertRunning("add handler");

            if (!(handler instanceof RejectedMessageListener)) {
                return;
            }
            RejectedMessageListener listener = (RejectedMessageListener) handler;
            EndpointQueue queue = new EndpointQueue(lock.newCondition());
            outgoingQueue.addIncomingHandler(queue);
            workers.execute(new Handler(new ChannelIdentifier(channelName), queue, listener));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds a connection to some other message hub. Outgoing messages are forwarded to this connection, and incoming messages are received from it.
     */
    public void addConnection(Connection<InterHubMessage> connection) {
        lock.lock();
        try {
            assertRunning("add connection");
            outgoingQueue.connected();
            workers.execute(new ConnectionDispatch(connection));
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
     * <li>Stops accepting any further outgoing message.</li>
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
                outgoingQueue.requestStop();
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
            requestStop();
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

    /**
     * NOTE: must be holding lock to use this class.
     */
    private static class EndpointQueue {
        private final List<Object> queue = new ArrayList<Object>();
        private final Condition condition;

        private EndpointQueue(Condition condition) {
            this.condition = condition;
        }

        /**
         * Pass a collection whose last element is {@link #END_OF_STREAM} to mark the end of the queue.
         */
        void put(Collection<?> messages) {
            queue.addAll(messages);
            if (!queue.isEmpty()) {
                condition.signalAll();
            }
        }

        /**
         * Last element will be {@link #END_OF_STREAM} when end of queue has been reached.
         */
        void take(Collection<Object> drainTo) {
            while (queue.isEmpty()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            drainTo.addAll(queue);
            queue.clear();
        }
    }

    /**
     * NOTE: must be holding lock to use this class.
     */
    private static class OutgoingQueue {
        private boolean connected;
        private boolean stopping;
        private final List<ChannelMessage> queue = new ArrayList<ChannelMessage>();
        private final Set<EndpointQueue> incomingHandlers = new HashSet<EndpointQueue>();

        void dispatch(ChannelMessage message) {
            queue.add(message);
        }

        void requestStop() {
            stopping = true;
            try {
                if (!connected) {
                    for (EndpointQueue handler : incomingHandlers) {
                        handler.put(queue);
                        handler.put(Collections.singleton(END_OF_STREAM));
                    }
                    queue.clear();
                }
            } finally {
                incomingHandlers.clear();
            }
        }

        void connected() {
            this.connected = true;
        }

        void take(Collection<Object> drainTo) {
            drainTo.addAll(queue);
            if (stopping) {
                drainTo.add(END_OF_STREAM);
            }
            queue.clear();
        }

        void addIncomingHandler(EndpointQueue queue) {
            incomingHandlers.add(queue);
        }
    }

    private class ConnectionDispatch implements Runnable {
        private final Connection<InterHubMessage> connection;

        private ConnectionDispatch(Connection<InterHubMessage> connection) {
            this.connection = connection;
        }

        public void run() {
            List<Object> messages = new ArrayList<Object>();
            while (true) {
                lock.lock();
                try {
                    outgoingQueue.take(messages);
                } finally {
                    lock.unlock();
                }
                for (Object message : messages) {
                    if (message == END_OF_STREAM) {
                        return;
                    }
                    InterHubMessage channelMessage = (InterHubMessage) message;
                    try {
                        connection.dispatch(channelMessage);
                    } catch (Throwable t) {
                        errorHandler.execute(t);
                        return;
                    }
                }
                messages.clear();
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
            return String.format("Dispatch %s to %s channel %s", type.getSimpleName(), displayName, channelIdentifier);
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
        private final ChannelIdentifier channelIdentifier;
        private final EndpointQueue queue;
        private final RejectedMessageListener listener;

        public Handler(ChannelIdentifier channelIdentifier, EndpointQueue queue, RejectedMessageListener listener) {
            this.channelIdentifier = channelIdentifier;
            this.queue = queue;
            this.listener = listener;
        }

        public void run() {
            List<Object> messages = new ArrayList<Object>();
            while (true) {
                lock.lock();
                try {
                    queue.take(messages);
                } finally {
                    lock.unlock();
                }
                for (Object message : messages) {
                    if (message == END_OF_STREAM) {
                        return;
                    }
                    ChannelMessage channelMessage = (ChannelMessage) message;
                    if (!channelMessage.getChannel().equals(channelIdentifier)) {
                        continue;
                    }
                    try {
                        listener.messageDiscarded(channelMessage.getPayload());
                    } catch (Throwable t) {
                        errorHandler.execute(t);
                        return;
                    }
                }
                messages.clear();
            }
        }
    }
}
