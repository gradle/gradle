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

package org.gradle.messaging.remote.internal;

import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.Receive;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DeferredConnection implements Dispatch<Message>, Receive<Message> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeferredConnection.class);

    private enum State {
        Stopped,
        Stopping(Stopped),
        Connected(Stopping, Stopped),
        AwaitConnect(Connected, Stopping, Stopped);

        private Set<State> successors;

        private State(State... successors) {
            this.successors = new HashSet<State>(Arrays.asList(successors));
        }

        public boolean canTransitionTo(State successor) {
            return successor == this || successors.contains(successor);
        }

        public State onDispatchEndOfStream() {
            return this == AwaitConnect ? Stopping : this;
        }
    }

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Connection<Message> connection;
    private State receiveState = State.AwaitConnect;
    private State dispatchState = State.AwaitConnect;

    public void connect(Connection<Message> connection) {
        lock.lock();
        try {
            if (receiveState == State.AwaitConnect) {
                this.connection = connection;
                setState(State.Connected, State.Connected);
                return;
            }
        } finally {
            lock.unlock();
        }

        // Stopping - discard
        connection.dispatch(new EndOfStreamEvent());
        connection.stop();
    }

    public Message receive() {
        Receive<Message> receive;

        lock.lock();
        try {
            while (receiveState == State.AwaitConnect) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new UncheckedException(e);
                }
            }
            switch (receiveState) {
                case Stopping:
                    setState(State.Stopped, dispatchState);
                    return new EndOfStreamEvent();
                case Stopped:
                    return null;
                case Connected:
                    break;
                default:
                    throw new IllegalStateException(String.format("Connection is in unexpected receive state %s.", receiveState));
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
            message = new EndOfStreamEvent();
        }
        if (message == null) {
            LOGGER.warn("Received unexpected end-of-stream. Discarding connection");
            message = new EndOfStreamEvent();
        }

        if (!(message instanceof EndOfStreamEvent)) {
            return message;
        }

        lock.lock();
        try {
            switch (receiveState) {
                case Connected:
                    setState(State.Stopped, dispatchState);
                    break;
                default:
                    throw new IllegalStateException(String.format("Connection is in unexpected state %s.", receiveState));
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
            boolean endOfStream = message instanceof EndOfStreamEvent;
            while (!endOfStream && dispatchState == State.AwaitConnect) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw new UncheckedException(e);
                }
            }
            switch (dispatchState) {
                case AwaitConnect:
                    setState(receiveState.onDispatchEndOfStream(), State.Stopped);
                    return;
                case Connected:
                    if (endOfStream) {
                        setState(receiveState.onDispatchEndOfStream(), State.Stopped);
                    }
                    break;
                case Stopping:
                    if (endOfStream) {
                        setState(receiveState, State.Stopped);
                        return;
                    }
                    LOGGER.error("Could not send message, as connection is stopping.");
                    return;
                default:
                    throw new IllegalStateException(String.format("Connection is in unexpected dispatch state %s.", dispatchState));
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
                switch (dispatchState) {
                    case Connected:
                        setState(receiveState.onDispatchEndOfStream(), State.Stopped);
                        break;
                    default:
                        throw new IllegalStateException(String.format("Connection is in unexpected dispatch state %s.", dispatchState));
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
            if (receiveState != State.Stopped || dispatchState != State.Stopped || connection == null) {
                return;
            }
            stoppable = connection;
            connection = null;
        } finally {
            lock.unlock();
        }

        stoppable.stop();
    }

    public void requestStop() {
        lock.lock();
        try {
            if (receiveState == State.AwaitConnect) {
                setState(State.Stopping, State.Stopping);
            }
        } finally {
            lock.unlock();
        }
    }

    private void setState(State receiveState, State dispatchState) {
        if (!this.receiveState.canTransitionTo(receiveState)) {
            throw new IllegalStateException(String.format("Cannot change receive state from %s to %s.", this.receiveState, receiveState));
        }
        if (!this.dispatchState.canTransitionTo(dispatchState)) {
            throw new IllegalStateException(String.format("Cannot change dispatch state from %s to %s.", this.dispatchState, dispatchState));
        }
        this.receiveState = receiveState;
        this.dispatchState = dispatchState;
        condition.signalAll();
    }
}
