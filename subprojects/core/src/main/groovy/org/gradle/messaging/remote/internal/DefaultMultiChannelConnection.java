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

import org.gradle.api.GradleException;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.*;
import org.gradle.messaging.remote.Address;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

class DefaultMultiChannelConnection implements MultiChannelConnection<Object> {
    private final Address sourceAddress;
    private final Address destinationAddress;
    private final IncomingDemultiplex incomingDemux;
    private final StoppableExecutor executor;
    private final Connection<Object> connection;
    private final ProtocolStack<Object> stack;
    private final DiscardingFailureHandler<Object> failureHandler;

    DefaultMultiChannelConnection(ExecutorFactory executorFactory, String displayName, final Connection<Object> connection, Address sourceAddress, Address destinationAddress) {
        this.connection = connection;
        this.executor = executorFactory.create(displayName);

        this.sourceAddress = sourceAddress;
        this.destinationAddress = destinationAddress;

        Dispatch<Object> outgoing = connection;
        Receive<Object> incoming = new EndOfStreamReceive(connection);
        Protocol<Object> endOfStream = new EndOfStreamHandshakeProtocol(new Runnable() {
            public void run() {
                requestStop();
            }
        });
        Protocol<Object> channel = new ChannelMultiplexProtocol();
        failureHandler = new DiscardingFailureHandler<Object>(LoggerFactory.getLogger(DefaultMultiChannelConnector.class));

        stack = new ProtocolStack<Object>(outgoing, incoming, executor, failureHandler, failureHandler, failureHandler, endOfStream, channel);

        // Incoming pipeline: <connection> -> <async-receive> -> <ignore-failures> -> <end-of-stream-filter> -> <channel-demux> -> <channel-async-queue> -> <ignore-failures> -> <handler>
        incomingDemux = new IncomingDemultiplex(executor);
        stack.receiveOn(incomingDemux);
    }

    private Dispatch<Object> wrapFailures(Dispatch<Object> dispatch) {
        return new FailureHandlingDispatch<Object>(dispatch, failureHandler);
    }

    public Address getLocalAddress() {
        if (sourceAddress == null) {
            throw new UnsupportedOperationException();
        }
        return sourceAddress;
    }

    public Address getRemoteAddress() {
        if (destinationAddress == null) {
            throw new UnsupportedOperationException();
        }
        return destinationAddress;
    }

    public void requestStop() {
        stack.requestStop();
    }

    public void addIncomingChannel(Object channelKey, Dispatch<Object> dispatch) {
        incomingDemux.addIncomingChannel(channelKey, wrapFailures(dispatch));
    }

    public Dispatch<Object> addOutgoingChannel(Object channelKey) {
        return new OutgoingMultiplex(channelKey, stack);
    }

    public void stop() {
        executor.execute(new Runnable() {
            public void run() {
                requestStop();
                new CompositeStoppable(stack, connection, incomingDemux).stop();
            }
        });
        try {
            executor.stop(120, TimeUnit.SECONDS);
        } catch (Throwable e) {
            throw new GradleException("Could not stop connection.", e);
        }
    }

}
