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
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.AsyncDispatch;
import org.gradle.messaging.dispatch.AsyncReceive;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.Receive;

import java.util.HashSet;
import java.util.Set;

/**
 * Adapts a {@link Connection} into a {@link AsyncConnection}.
 */
public class AsyncConnectionAdapter<T> implements AsyncConnection<T>, Stoppable {
    private final Connection<T> connection;
    private final AsyncDispatch<T> outgoing;
    private final AsyncReceive<T> incoming;
    private final ReceiveHandler<T> receiveHandler;
    private final Set<Stoppable> executors = new HashSet<Stoppable>();

    public AsyncConnectionAdapter(Connection<T> connection, ReceiveHandler<T> receiveHandler, ExecutorFactory executor) {
        this.connection = connection;
        this.receiveHandler = receiveHandler;

        StoppableExecutor outgoingExecutor = executor.create(String.format("%s send", connection));
        executors.add(outgoingExecutor);
        outgoing = new AsyncDispatch<T>(outgoingExecutor);
        outgoing.dispatchTo(connection);

        StoppableExecutor incomingExecutor = executor.create(String.format("%s receive", connection));
        executors.add(incomingExecutor);
        incoming = new AsyncReceive<T>(incomingExecutor);
        incoming.receiveFrom(new IncomingReceive());
    }

    public void dispatch(T message) {
        outgoing.dispatch(message);
    }

    public void receiveOn(Dispatch<? super T> handler) {
        incoming.dispatchTo(handler);
    }

    public void stop() {
        new CompositeStoppable(outgoing, connection, incoming).add(executors).stop();
    }

    private class IncomingReceive implements Receive<T> {
        boolean finished;

        public T receive() {
            if (finished) {
                return null;
            }

            T message = connection.receive();
            if (message == null) {
                finished = true;
                return receiveHandler.endOfStream();
            } else if (receiveHandler.isEndOfStream(message)) {
                finished = true;
            }

            return message;
        }
    }
}
