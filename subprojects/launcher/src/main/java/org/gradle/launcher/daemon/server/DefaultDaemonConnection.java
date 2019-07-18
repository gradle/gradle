/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.launcher.daemon.server;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.launcher.daemon.protocol.*;
import org.gradle.launcher.daemon.server.api.DaemonConnection;
import org.gradle.launcher.daemon.server.api.StdinHandler;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultDaemonConnection implements DaemonConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDaemonConnection.class);
    private final RemoteConnection<Message> connection;
    private final ManagedExecutor executor;
    private final StdinQueue stdinQueue;
    private final DisconnectQueue disconnectQueue;
    private final CancelQueue cancelQueue;
    private final ReceiveQueue receiveQueue;
    private volatile boolean stopping;

    public DefaultDaemonConnection(final RemoteConnection<Message> connection, ExecutorFactory executorFactory) {
        this.connection = connection;
        stdinQueue = new StdinQueue(executorFactory);
        disconnectQueue = new DisconnectQueue();
        cancelQueue = new CancelQueue(executorFactory);
        receiveQueue = new ReceiveQueue();
        executor = executorFactory.create("Handler for " + connection.toString());
        executor.execute(new Runnable() {
            @Override
            public void run() {
                Throwable failure = null;
                try {
                    while (true) {
                        Object message;
                        try {
                            message = connection.receive();
                        } catch (Exception e) {
                            if (!stopping && LOGGER.isDebugEnabled()) {
                                LOGGER.debug(String.format("thread %s: Could not receive message from client.", Thread.currentThread().getId()), e);
                            }
                            failure = e;
                            return;
                        }
                        if (message == null) {
                            LOGGER.debug("thread {}: Received end-of-input from client.", Thread.currentThread().getId());
                            return;
                        }

                        if (message instanceof InputMessage) {
                            LOGGER.debug("thread {}: Received IO message from client: {}", Thread.currentThread().getId(), message);
                            stdinQueue.add((InputMessage) message);
                        } else if (message instanceof Cancel) {
                            LOGGER.debug("thread {}: Received cancel message from client: {}", Thread.currentThread().getId(), message);
                            cancelQueue.add((Cancel) message);
                        } else {
                            LOGGER.debug("thread {}: Received non-IO message from client: {}", Thread.currentThread().getId(), message);
                            receiveQueue.add(message);
                        }
                    }
                } finally {
                    stdinQueue.disconnect();
                    cancelQueue.disconnect();
                    disconnectQueue.disconnect();
                    receiveQueue.disconnect(failure);
                }
            }
        });
    }

    @Override
    public void onStdin(StdinHandler handler) {
        stdinQueue.useHandler(handler);
    }

    @Override
    public void onDisconnect(Runnable handler) {
        disconnectQueue.useHandler(handler);
    }

    @Override
    public void onCancel(Runnable handler) {
        cancelQueue.useHandler(handler);
    }

    @Override
    public Object receive(long timeoutValue, TimeUnit timeoutUnits) {
        return receiveQueue.take(timeoutValue, timeoutUnits);
    }

    @Override
    public void daemonUnavailable(DaemonUnavailable unavailable) {
        connection.dispatch(unavailable);
        connection.flush();
    }

    @Override
    public void buildStarted(BuildStarted buildStarted) {
        connection.dispatch(buildStarted);
        connection.flush();
    }

    @Override
    public void logEvent(OutputEvent logEvent) {
        connection.dispatch(new OutputMessage(logEvent));
        connection.flush();
    }

    @Override
    public void event(Object event) {
        connection.dispatch(new BuildEvent(event));
        connection.flush();
    }

    @Override
    public void completed(Result result) {
        connection.dispatch(result);
        connection.flush();
    }

    @Override
    public void stop() {
        stopping = true;

        // 1. Stop handling disconnects. Blocks until the handler has finished.
        // 2. Stop the connection. This means that the thread receiving from the connection will receive a null and finish up.
        // 3. Stop receiving incoming messages. Blocks until the receive thread has finished. This will notify the stdin and receive queues to signal end of input.
        // 4. Stop the receive queue, to unblock any threads blocked in receive().
        // 5. Stop handling stdin. Blocks until the handler has finished. Discards any queued input.
        CompositeStoppable.stoppable(disconnectQueue, connection, executor, receiveQueue, stdinQueue, cancelQueue).stop();
    }

    @Override
    public String toString() {
        return "DefaultDaemonConnection: " + connection;
    }

    private static abstract class CommandQueue<C extends Message, H> implements Stoppable {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        protected final LinkedList<C> queue = new LinkedList<C>();
        private final String name;
        private ManagedExecutor executor;
        private boolean removed;
        private final ExecutorFactory executorFactory;

        private CommandQueue(ExecutorFactory executorFactory, String name) {
            this.executorFactory = executorFactory;
            this.name = name;
        }

        @Override
        public void stop() {
            ManagedExecutor executor;
            lock.lock();
            try {
                executor = this.executor;
            } finally {
                lock.unlock();
            }
            if (executor != null) {
                executor.stop();
            }
        }

        public void add(C command) {
            lock.lock();
            try {
                queue.add(command);
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public void useHandler(final H handler) {
            if (handler != null) {
                startConsuming(handler);
            } else {
                stopConsuming();
            }
        }

        protected void stopConsuming() {
            ManagedExecutor executor;
            lock.lock();
            try {
                queue.clear();
                removed = true;
                condition.signalAll();
                executor = this.executor;
            } finally {
                lock.unlock();
            }
            if (executor != null) {
                executor.stop();
            }
        }

        protected void startConsuming(final H handler) {
            lock.lock();
            try {
                if (executor != null) {
                    throw new UnsupportedOperationException("More instances of " + name + " not supported.");
                }
                executor = executorFactory.create(name);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        while (true) {
                            C command;
                            lock.lock();
                            try {
                                while (!removed && queue.isEmpty()) {
                                    try {
                                        condition.await();
                                    } catch (InterruptedException e) {
                                        throw UncheckedException.throwAsUncheckedException(e);
                                    }
                                }
                                if (removed) {
                                    return;
                                }
                                command = queue.removeFirst();
                            } finally {
                                lock.unlock();
                            }
                            if (doHandleCommand(handler, command)) {
                                return;
                            }
                        }
                    }
                });
            } finally {
                lock.unlock();
            }
        }

        /** @return true if the queue should stop processing. */
        protected abstract boolean doHandleCommand(final H handler, C command);
        // Called under lock
        protected abstract void doHandleDisconnect();

        public void disconnect() {
            lock.lock();
            try {
                doHandleDisconnect();
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private static class StdinQueue extends CommandQueue<InputMessage, StdinHandler> {

        private StdinQueue(ExecutorFactory executorFactory) {
            super(executorFactory, "Stdin handler");
        }

        @Override
        protected boolean doHandleCommand(final StdinHandler handler, InputMessage command) {
            try {
                if (command instanceof CloseInput) {
                    handler.onEndOfInput();
                    return true;
                } else {
                    handler.onInput((ForwardInput) command);
                }
            } catch (Exception e) {
                LOGGER.warn("Could not forward client stdin.", e);
                return true;
            }
            return false;
        }

        @Override
        protected void doHandleDisconnect() {
            queue.clear();
            queue.add(new CloseInput());
        }
    }

    private static class CancelQueue extends CommandQueue<Cancel, Runnable> {

        private CancelQueue(ExecutorFactory executorFactory) {
            super(executorFactory, "Cancel handler");
        }

        @Override
        protected boolean doHandleCommand(Runnable handler, Cancel command) {
            try {
                handler.run();
            } catch (Exception e) {
                LOGGER.warn("Could not process cancel request from client.", e);
            }
            return true;
        }

        @Override
        protected void doHandleDisconnect() {
            queue.clear();
        }
    }

    private static class DisconnectQueue implements Stoppable {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private Runnable handler;
        private boolean notifying;
        private boolean disconnected;

        public void disconnect() {
            Runnable action;
            lock.lock();
            try {
                disconnected = true;
                if (handler == null) {
                    return;
                }
                action = handler;
                notifying = true;
            } finally {
                lock.unlock();
            }
            runAction(action);
        }

        private void runAction(Runnable action) {
            try {
                action.run();
            } catch (Exception e) {
                LOGGER.warn("Failed to notify disconnect handler.", e);
            } finally {
                lock.lock();
                try {
                    notifying = false;
                    condition.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        public void stop() {
            useHandler(null);
        }

        public void useHandler(Runnable handler) {
            if (handler != null) {
                startMonitoring(handler);
            } else {
                stopMonitoring();
            }
        }

        private void startMonitoring(Runnable handler) {
            Runnable action;

            lock.lock();
            try {
                if (this.handler != null) {
                    throw new UnsupportedOperationException("Multiple disconnect handlers not supported.");
                }
                this.handler = handler;
                if (!disconnected) {
                    return;
                }
                action = handler;
                notifying = true;
            } finally {
                lock.unlock();
            }

            runAction(action);
        }

        private void stopMonitoring() {
            lock.lock();
            try {
                while (notifying) {
                    try {
                        condition.await();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
                handler = null;
            } finally {
                lock.unlock();
            }
        }
    }

    private static class ReceiveQueue implements Stoppable {
        private static final Object END = new Object();
        private final BlockingQueue<Object> queue = new LinkedBlockingQueue<Object>();

        @Override
        public void stop() {
        }

        public void disconnect(Throwable failure) {
            queue.clear();
            if (failure != null) {
                add(failure);
            }
            add(END);
        }

        public void add(Object message) {
            try {
                queue.put(message);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        public Object take(long timeoutValue, TimeUnit timeoutUnits) {
            Object result;
            try {
                result = queue.poll(timeoutValue, timeoutUnits);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
            if (result instanceof Throwable) {
                Throwable failure = (Throwable) result;
                throw UncheckedException.throwAsUncheckedException(failure);
            }
            return result == END ? null : result;
        }
    }
}
