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

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.AsyncStoppable;
import org.gradle.messaging.dispatch.*;

import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProtocolStack<T> implements AsyncStoppable {
    private final AsyncDispatch<Runnable> workQueue;
    private final QueuingDispatch<T> incomingQueue = new QueuingDispatch<T>();
    private final QueuingDispatch<T> outgoingQueue = new QueuingDispatch<T>();
    private final AsyncReceive<Runnable> receiver;
    private final DelayedReceive<Runnable> callbackQueue;
    private final LinkedList<Stage> stack = new LinkedList<Stage>();
    private final LinkedList<Runnable> contextQueue = new LinkedList<Runnable>();
    private final DispatchFailureHandler<? super T> outgoingDispatchFailureHandler;
    private final DispatchFailureHandler<? super T> incomingDispatchFailureHandler;
    private final CountDownLatch protocolsStopped;
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final AsyncConnection<T> bottomConnection;
    private final AsyncConnection<T> topConnection;

    public ProtocolStack(Executor executor, DispatchFailureHandler<? super T> outgoingDispatchFailureHandler, DispatchFailureHandler<? super T> incomingDispatchFailureHandler,
                         Protocol<T>... protocols) {
        this.outgoingDispatchFailureHandler = outgoingDispatchFailureHandler;
        this.incomingDispatchFailureHandler = incomingDispatchFailureHandler;
        this.callbackQueue = new DelayedReceive<Runnable>(new TrueTimeProvider());
        protocolsStopped = new CountDownLatch(protocols.length);

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

        // Wire up callback queue
        receiver = new AsyncReceive<Runnable>(executor);
        receiver.dispatchTo(workQueue);
        receiver.receiveFrom(callbackQueue);

        bottomConnection = new BottomConnection();
        topConnection = new TopConnection();

        // Start each protocol from bottom to top
        workQueue.dispatch(new Runnable() {
            public void run() {
                for (int i = stack.size() - 1; i >= 0; i--) {
                    Stage context = stack.get(i);
                    context.start();
                }
            }
        });
    }

    public AsyncConnection<T> getBottom() {
        return bottomConnection;
    }

    public AsyncConnection<T> getTop() {
        return topConnection;
    }

    public void requestStop() {
        if (!stopRequested.getAndSet(true)) {
            workQueue.dispatch(new Runnable() {
                public void run() {
                    stack.getFirst().requestStop();
                }
            });
        }
    }

    public void stop() {
        requestStop();
        try {
            protocolsStopped.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        callbackQueue.clear();
        CompositeStoppable.stoppable(callbackQueue, receiver, workQueue, incomingQueue, outgoingQueue).stop();
    }

    private class ExecuteRunnable implements Dispatch<Runnable> {
        public void dispatch(Runnable message) {
            contextQueue.add(message);
            while (!contextQueue.isEmpty()) {
                contextQueue.removeFirst().run();
            }
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

    private enum StageState { Init, StopRequested, StopPending, Stopped }

    private class ProtocolStage extends Stage implements ProtocolContext<T> {
        private final Protocol<T> protocol;
        private StageState state = StageState.Init;

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
            if (state == StageState.Init) {
                throw new IllegalStateException(String.format("Cannot stop when in %s state.", state));
            }
            if (state != StageState.Stopped) {
                state = StageState.Stopped;
                protocolsStopped.countDown();
                contextQueue.add(new Runnable() {
                    public void run() {
                        outgoing.requestStop();
                    }
                });
            }
        }

        public void stopLater() {
            if (state == StageState.Init || state == StageState.Stopped) {
                throw new IllegalStateException(String.format("Cannot stop later when in %s state.", state));
            }
            state = StageState.StopPending;
        }

        @Override
        public void requestStop() {
            assert state == StageState.Init;
            state = StageState.StopRequested;
            protocol.stopRequested();
            if (state == StageState.StopRequested) {
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
                if (!cancelled && state != StageState.Stopped) {
                    action.run();
                }
            }
        }
    }

    private class TopStage extends Stage {
        @Override
        public void handleIncoming(T message) {
            incomingQueue.dispatch(message);
        }

        @Override
        public void handleOutgoing(T message) {
            outgoing.handleOutgoing(message);
        }

        @Override
        public void requestStop() {
            outgoing.requestStop();
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

    private class BottomConnection implements AsyncConnection<T> {
        public void dispatchTo(Dispatch<? super T> handler) {
            outgoingQueue.dispatchTo(new FailureHandlingDispatch<T>(handler, outgoingDispatchFailureHandler));
        }

        public void dispatch(final T message) {
            workQueue.dispatch(new Runnable() {
                @Override
                public String toString() {
                    return String.format("incoming %s", message);
                }

                public void run() {
                    stack.getLast().handleIncoming(message);
                }
            });
        }
    }

    private class TopConnection implements AsyncConnection<T> {
        public void dispatchTo(Dispatch<? super T> handler) {
            incomingQueue.dispatchTo(new FailureHandlingDispatch<T>(handler, incomingDispatchFailureHandler));
        }

        public void dispatch(final T message) {
            workQueue.dispatch(new Runnable() {
                @Override
                public String toString() {
                    return String.format("outgoing %s", message);
                }

                public void run() {
                    stack.getFirst().handleOutgoing(message);
                }
            });
        }
    }
}
