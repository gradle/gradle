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

import org.gradle.api.GradleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Arrays.*;

public class DeferredConnection implements Dispatch<Message>, Receive<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeferredConnection.class);

    private enum State {
        Stopped,
        AwaitIncomingEndOfStream(Stopped),
        AwaitOutgoingEndOfStream(Stopped),
        GenerateIncomingEndOfStream(Stopped),
        Connected(AwaitIncomingEndOfStream, AwaitOutgoingEndOfStream),
        AwaitConnect(Connected, GenerateIncomingEndOfStream);

        private Set<State> successors;

        private State(State... successors) {
            this.successors = new HashSet<State>(Arrays.asList(successors));
        }

        public boolean canTransitionTo(State successor) {
            return successors.contains(successor);
        }
    }

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Connection<Message> connection;
    private State state = State.AwaitConnect;

    public void connect(Connection<Message> connection) {
        lock.lock();
        try {
            if (state == State.AwaitConnect) {
                this.connection = connection;
                setState(State.Connected);
                return;
            }
        } finally {
            lock.unlock();
        }

        // Stopping - discard
        connection.dispatch(new EndOfStream());
        connection.stop();
    }

    public Message receive() {
        Receive<Message> receive;

        lock.lock();
        try {
            while (state == State.AwaitConnect) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new GradleException(e);
                }
            }
            switch (state) {
                case GenerateIncomingEndOfStream:
                    setState(State.Stopped);
                    return new EndOfStream();
                case Stopped:
                case AwaitOutgoingEndOfStream:
                    return null;
                case Connected:
                case AwaitIncomingEndOfStream:
                    break;
                default:
                    throw new IllegalStateException(String.format("Connection is in unexpected state %s.", state));
            }

            receive = connection;
        } finally {
            lock.unlock();
        }

        Message message;
        try {
            message = receive.receive();
        } catch (Throwable throwable) {
            LOGGER.error(String.format("Could not receive next message using %s. Discarding connection.", receive),
                    throwable);
            message = new EndOfStream();
        }
        if (message == null) {
            LOGGER.warn("Received unexpected end-of-stream. Discarding connection");
            message = new EndOfStream();
        }

        if (!(message instanceof EndOfStream)) {
            return message;
        }

        lock.lock();
        try {
            switch (state) {
                case Connected:
                    setState(State.AwaitOutgoingEndOfStream);
                    break;
                case AwaitIncomingEndOfStream:
                    setState(State.Stopped);
                    break;
                default:
                    throw new IllegalStateException(String.format("Connection is in unexpected state %s.", state));
            }
        } finally {
            lock.unlock();
        }

        cleanup();
        return message;
    }

    public void dispatch(Message message) {
        Dispatch<Message> dispatch;

        lock.lock();
        try {
            boolean endOfStream = message instanceof EndOfStream;
            while (!endOfStream && state == State.AwaitConnect) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new GradleException(e);
                }
            }
            switch (state) {
                case AwaitConnect:
                    setState(State.GenerateIncomingEndOfStream);
                    return;
                case Connected:
                    if (endOfStream) {
                        setState(State.AwaitIncomingEndOfStream);
                    }
                    break;
                case AwaitOutgoingEndOfStream:
                    if (endOfStream) {
                        setState(State.Stopped);
                    }
                    break;
                default:
                    throw new IllegalStateException(String.format("Connection is in unexpected state %s.", state));
            }
            dispatch = connection;
        } finally {
            lock.unlock();
        }

        try {
            dispatch.dispatch(message);
        } catch (Throwable throwable) {
            LOGGER.error(String.format("Could not send message using %s. Discarding connection.", dispatch), throwable);
            lock.lock();
            try {
                switch (state) {
                    case Connected:
                        setState(State.AwaitIncomingEndOfStream);
                        break;
                    case AwaitOutgoingEndOfStream:
                        setState(State.Stopped);
                        break;
                    default:
                        throw new IllegalStateException(String.format("Connection is in unexpected state %s.", state));
                }
            } finally {
                lock.unlock();
            }
        }

        cleanup();
    }

    private void cleanup() {
        Stoppable stoppable;

        lock.lock();
        try {
            if (state != State.Stopped || connection == null) {
                return;
            }
            stoppable = connection;
            connection = null;
        } finally {
            lock.unlock();
        }

        stoppable.stop();
    }

    private void setState(State state) {
        if (!this.state.canTransitionTo(state)) {
            throw new IllegalStateException(String.format("Cannot change state from %s to %s.", this.state, state));
        }
        this.state = state;
        condition.signalAll();
    }
}
