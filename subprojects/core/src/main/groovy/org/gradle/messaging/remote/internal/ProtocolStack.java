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

import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.dispatch.*;
import org.gradle.util.TrueTimeProvider;
import org.gradle.util.UncheckedException;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProtocolStack<T> implements AsyncConnection<T> {
    private final AsyncDispatch<Runnable> workQueue;
    private final AsyncDispatch<T> handlerQueue;
    private final AsyncDispatch<T> outgoingQueue;
    private final AsyncReceive<Runnable> receiver;
    private final DelayedReceive<Runnable> callbackQueue;
    private final LinkedList<Stage> stack = new LinkedList<Stage>();
    private final LinkedList<Runnable> contextQueue = new LinkedList<Runnable>();
    private final DispatchFailureHandler<T> outgoingDispatchFailureHandler;
    private final DispatchFailureHandler<T> incomingDispatchFailureHandler;
    private final CountDownLatch protocolsStopped;
    private final AtomicBoolean stopRequested = new AtomicBoolean();

    public ProtocolStack(Dispatch<? super T> outgoing, Receive<? extends T> incoming, Executor executor, ReceiveFailureHandler receiveFailureHandler,
                         DispatchFailureHandler<T> outgoingDispatchFailureHandler, DispatchFailureHandler<T> incomingDispatchFailureHandler,
                         Protocol<T>... protocols) {
        this.outgoingDispatchFailureHandler = outgoingDispatchFailureHandler;
        this.incomingDispatchFailureHandler = incomingDispatchFailureHandler;
        this.callbackQueue = new DelayedReceive<Runnable>(new TrueTimeProvider());
        protocolsStopped = new CountDownLatch(protocols.length);

        // Setup the outgoing queues.
        handlerQueue = new AsyncDispatch<T>(executor);
        outgoingQueue = new AsyncDispatch<T>(executor);
        outgoingQueue.dispatchTo(new FailureHandlingDispatch<T>(outgoing, outgoingDispatchFailureHandler));

        //Start work queue
        workQueue = new AsyncDispatch<Runnable>(executor);
        workQueue.dispatchTo(new ExecuteRunnable());

        stack.add(new TopStage());
        for (Protocol<T> protocol : protocols) {
            stack.add(new ProtocolStage(protocol));
        }
        stack.add(new BottomStage());
        for (int i = 0; i < stack.size(); i++) {
            Stage context = stack.get(i);
            Stage outgoingStage = i == stack.size() - 1 ? null : stack.get(i + 1);
            Stage incomingStage = i == 0 ? null : stack.get(i - 1);
            context.attach(outgoingStage, incomingStage);
        }

        // Start each protocol from bottom to top
        for (int i = stack.size() - 1; i >= 0; i--) {
            Stage context = stack.get(i);
            context.start();
        }
        assert contextQueue.isEmpty();

        // Finally, start receiving
        receiver = new AsyncReceive<Runnable>(executor, workQueue);
        receiver.receiveFrom(callbackQueue);
        receiver.receiveFrom(new IncomingMessageReceive(new FailureHandlingReceive<T>(incoming, receiveFailureHandler)));
    }

    public void receiveOn(Dispatch<? super T> handler) {
        handlerQueue.dispatchTo(new FailureHandlingDispatch<T>(handler, incomingDispatchFailureHandler));
    }

    public void dispatch(final T message) {
        workQueue.dispatch(new Runnable() {
            public void run() {
                stack.getFirst().handleOutgoing(message);
            }
        });
    }

    public void requestStop() {
        if (!stopRequested.getAndSet(true)) {
            workQueue.dispatch(new Runnable() {
                public void run() {
                    for (Stage stage : stack) {
                        stage.requestStop();
                    }
                }
            });
        }
    }

    public void stop() {
        requestStop();
        try {
            protocolsStopped.await();
        } catch (InterruptedException e) {
            throw UncheckedException.asUncheckedException(e);
        }
        callbackQueue.clear();
        new CompositeStoppable(callbackQueue, receiver, workQueue, handlerQueue, outgoingQueue).stop();
    }

    private class ExecuteRunnable implements Dispatch<Runnable> {
        public void dispatch(Runnable message) {
            contextQueue.add(message);
            while (!contextQueue.isEmpty()) {
                contextQueue.removeFirst().run();
            }
        }
    }

    private class IncomingMessageReceive implements Receive<Runnable> {
        private final Receive<T> receive;

        private IncomingMessageReceive(Receive<T> receive) {
            this.receive = receive;
        }

        public Runnable receive() {
            final T message = receive.receive();
            if (message == null) {
                return null;
            }
            return new Runnable() {
                public void run() {
                    stack.getLast().handleIncoming(message);
                }
            };
        }
    }

    private abstract class Stage {
        protected Stage outgoing;
        protected Stage incoming;

        public void attach(Stage outgoing, Stage incoming) {
            this.outgoing = outgoing;
            this.incoming = incoming;
        }

        public void start() {
        }

        public void handleIncoming(T message) {
            throw new UnsupportedOperationException();
        }

        public void handleOutgoing(T message) {
            throw new UnsupportedOperationException();
        }

        public void requestStop() {
        }
    }

    private class ProtocolStage extends Stage implements ProtocolContext<T> {
        private final Protocol<T> protocol;
        private boolean stopped;
        private boolean stopPending;

        private ProtocolStage(Protocol<T> protocol) {
            this.protocol = protocol;
        }

        @Override
        public void start() {
            protocol.start(this);
        }

        @Override
        public void handleIncoming(T message) {
            try {
                protocol.handleIncoming(message);
            } catch (Throwable throwable) {
                incomingDispatchFailureHandler.dispatchFailed(message, throwable);
            }
        }

        @Override
        public void handleOutgoing(T message) {
            try {
                protocol.handleOutgoing(message);
            } catch (Throwable throwable) {
                outgoingDispatchFailureHandler.dispatchFailed(message, throwable);
            }
        }

        public void dispatchIncoming(final T message) {
            contextQueue.add(new Runnable() {
                public void run() {
                    incoming.handleIncoming(message);
                }
            });
        }

        public void dispatchOutgoing(final T message) {
            contextQueue.add(new Runnable() {
                public void run() {
                    outgoing.handleOutgoing(message);
                }
            });
        }

        public Callback callbackLater(int delay, TimeUnit delayUnits, Runnable action) {
            DefaultCallback callback = new DefaultCallback(action);
            callbackQueue.dispatchLater(callback, delay, delayUnits);
            return callback;
        }

        public void stopped() {
            stopPending = false;
            if (!stopped) {
                stopped = true;
                protocolsStopped.countDown();
            }
        }

        public void stopLater() {
            stopPending = true;
        }

        @Override
        public void requestStop() {
            protocol.stopRequested();
            if (!stopPending) {
                stopped();
            }
        }

        private class DefaultCallback implements Runnable, ProtocolContext.Callback {
            final Runnable action;
            boolean cancelled;

            private DefaultCallback(Runnable action) {
                this.action = action;
            }

            public void cancel() {
                cancelled = true;
                callbackQueue.remove(this);
            }

            public void run() {
                if (!cancelled && !stopped) {
                    action.run();
                }
            }
        }
    }

    private class TopStage extends Stage {
        @Override
        public void handleIncoming(T message) {
            handlerQueue.dispatch(message);
        }

        @Override
        public void handleOutgoing(T message) {
            outgoing.handleOutgoing(message);
        }
    }

    private class BottomStage extends Stage {
        @Override
        public void handleIncoming(T message) {
            incoming.handleIncoming(message);
        }

        @Override
        public void handleOutgoing(T message) {
            outgoingQueue.dispatch(message);
        }
    }
}
