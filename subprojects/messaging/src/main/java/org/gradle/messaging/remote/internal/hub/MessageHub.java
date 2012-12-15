package org.gradle.messaging.remote.internal.hub;

import org.gradle.api.Action;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.Dispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MessageHub implements AsyncStoppable {
    private enum State {Running, Stopping, Stopped}

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHub.class);
    private static final Object END_OF_STREAM = new Object();
    private final StoppableExecutor workers;
    private final String displayName;
    private final Action<? super Throwable> errorHandler;
    private final Lock lock = new ReentrantLock();
    private State state = State.Running;
    private final Map<String, ChannelQueue> outgoingChannels = new HashMap<String, ChannelQueue>();

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
            ChannelQueue queue = getOutgoingQueue(channelName);
            return new ChannelDispatch<T>(type, channelName, queue);
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
            final RejectedMessageListener listener = (RejectedMessageListener) handler;

            ChannelQueue channelQueue = getOutgoingQueue(channelName);
            final HandlerQueue queue = new HandlerQueue(lock.newCondition());
            channelQueue.addHandler(queue);
            workers.execute(new Handler(queue, listener));
        } finally {
            lock.unlock();
        }
    }

    private ChannelQueue getOutgoingQueue(String channelName) {
        ChannelQueue queue = outgoingChannels.get(channelName);
        if (queue == null) {
            queue = new ChannelQueue();
            outgoingChannels.put(channelName, queue);
        }
        return queue;
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
                for (ChannelQueue channelQueue : outgoingChannels.values()) {
                    channelQueue.stop();
                }
            } finally {
                outgoingChannels.clear();
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
                outgoingChannels.clear();
                state = State.Stopped;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * NOTE: must be holding lock to use this class.
     */
    private static class HandlerQueue {
        private final List<Object> queue = new ArrayList<Object>();
        private final Condition condition;

        private HandlerQueue(Condition condition) {
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
    private static class ChannelQueue {
        private final List<Object> queue = new ArrayList<Object>();
        private final Set<HandlerQueue> handlers = new HashSet<HandlerQueue>();

        void dispatch(Object message) {
            queue.add(message);
        }

        void stop() {
            try {
                for (HandlerQueue handler : handlers) {
                    handler.put(queue);
                    handler.put(Collections.singleton(END_OF_STREAM));
                }
            } finally {
                queue.clear();
                handlers.clear();
            }
        }

        void addHandler(HandlerQueue queue) {
            handlers.add(queue);
        }
    }

    private class ChannelDispatch<T> implements Dispatch<T> {
        private final Class<T> type;
        private final String channelName;
        private final ChannelQueue queue;

        public ChannelDispatch(Class<T> type, String channelName, ChannelQueue queue) {
            this.type = type;
            this.channelName = channelName;
            this.queue = queue;
        }

        @Override
        public String toString() {
            return String.format("Dispatch %s to %s channel %s", type.getSimpleName(), displayName, channelName);
        }

        public void dispatch(T message) {
            lock.lock();
            try {
                assertRunning("dispatch message");
                queue.dispatch(message);
            } finally {
                lock.unlock();
            }
        }
    }

    private class Handler implements Runnable {
        private final HandlerQueue queue;
        private final RejectedMessageListener listener;

        public Handler(HandlerQueue queue, RejectedMessageListener listener) {
            this.queue = queue;
            this.listener = listener;
        }

        public void run() {
            while (true) {
                List<Object> messages = new ArrayList<Object>();
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
                    try {
                        listener.messageDiscarded(message);
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
