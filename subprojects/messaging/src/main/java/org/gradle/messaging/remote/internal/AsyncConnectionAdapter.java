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
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Adapts a {@link Connection} into an {@link AsyncConnection}.
 */
public class AsyncConnectionAdapter<T> implements AsyncConnection<T>, Stoppable {
    private final Connection<T> connection;
    private final AsyncReceive<T> incoming;
    private final ProtocolStack<T> stack;
    private final AsyncDispatch<T> outgoing;
    private final Set<Stoppable> executors = new HashSet<Stoppable>();

    public AsyncConnectionAdapter(Connection<T> connection, DispatchFailureHandler<? super T> dispatchFailureHandler, ExecutorFactory executor, Protocol<T>... protocols) {
        this.connection = connection;

        StoppableExecutor outgoingExecutor = executor.create(String.format("%s send", connection));
        executors.add(outgoingExecutor);
        outgoing = new AsyncDispatch<T>(outgoingExecutor);
        outgoing.dispatchTo(new FailureHandlingDispatch<T>(connection, dispatchFailureHandler));

        StoppableExecutor dispatchExecutor = executor.create(String.format("%s dispatch", connection));
        executors.add(dispatchExecutor);
        stack = new ProtocolStack<T>(dispatchExecutor, dispatchFailureHandler, dispatchFailureHandler, protocols);
        stack.getBottom().dispatchTo(outgoing);

        StoppableExecutor incomingExecutor = executor.create(String.format("%s receive", connection));
        executors.add(incomingExecutor);
        incoming = new AsyncReceive<T>(incomingExecutor);
        incoming.dispatchTo(stack.getBottom());
        incoming.receiveFrom(new ConnectionReceive<T>(connection));
    }

    public void dispatch(T message) {
        stack.getTop().dispatch(message);
    }

    public void dispatchTo(Dispatch<? super T> handler) {
        stack.getTop().dispatchTo(handler);
    }

    public void stop() {
        CompositeStoppable.stoppable(stack, outgoing, connection, incoming).add(executors).stop();
    }

    private class ConnectionReceive<T> implements Receive<T> {
        private final Connection<T> connection;

        public ConnectionReceive(Connection<T> connection) {
            this.connection = connection;
        }

        public T receive() {
            T result = connection.receive();
            if (result == null) {
                stack.requestStop();
            }
            return result;
        }
    }
}
