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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.util.ThreadUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultConnector implements Connector, Stoppable {
    private final OutgoingConnector outgoingConnector;
    private final IncomingConnector incomingConnector;
    private final Lock lock = new ReentrantLock();
    private int nextConnectionId;
    private final Map<URI, Channel> pending = new HashMap<URI, Channel>();
    private final ExecutorService executorService;

    public DefaultConnector(OutgoingConnector outgoingConnector,
                            IncomingConnector incomingConnector) {
        this.outgoingConnector = outgoingConnector;
        this.incomingConnector = incomingConnector;
        executorService = Executors.newCachedThreadPool();
        incomingConnector.accept(incomingConnectionAction());
    }

    private Action<Connection<Message>> incomingConnectionAction() {
        return new Action<Connection<Message>>() {
            public void execute(Connection<Message> connection) {
                executorService.submit(new IncomingChannelHandler(connection));
            }
        };
    }

    public void stop() {
        ThreadUtils.shutdown(executorService);
    }

    public OutgoingConnection<Message> accept(Dispatch<Message> incomingDispatch) {
        lock.lock();
        try {
            URI localAddress;
            try {
                localAddress = new URI(String.format("channel:%s!%d", incomingConnector.getLocalAddress(),
                        nextConnectionId++));
            } catch (URISyntaxException e) {
                throw new GradleException(e);
            }
            Channel channel = new Channel(localAddress, null, incomingDispatch);
            pending.put(localAddress, channel);
            return channel;
        } finally {
            lock.unlock();
        }
    }

    public OutgoingConnection<Message> connect(URI destinationAddress, Dispatch<Message> incomingDispatch) {
        if (!destinationAddress.getScheme().equals("channel")) {
            throw new IllegalArgumentException(String.format("Cannot create a connection to destination URI '%s'.",
                    destinationAddress));
        }
        URI connectionAddress = toConnectionAddress(destinationAddress);
        Connection<Message> connection = outgoingConnector.create(connectionAddress);
        Channel channel = new Channel(null, destinationAddress, incomingDispatch);
        channel.setConnection(connection);
        channel.dispatch(new ConnectRequest(destinationAddress, null));
        return channel;
    }

    private URI toConnectionAddress(URI destinationAddress) {
        String content = destinationAddress.getSchemeSpecificPart();
        URI connectionAddress;
        try {
            connectionAddress = new URI(StringUtils.substringBeforeLast(content, "!"));
        } catch (URISyntaxException e) {
            throw new GradleException(e);
        }
        return connectionAddress;
    }

    private final class Channel implements OutgoingConnection<Message> {
        private final URI sourceAddress;
        private final URI destinationAddress;
        private final EndOfStreamDispatch outgoingDispatch;
        private final EndOfStreamFilter incomingDispatch;
        private final AsyncDispatch<Message> outgoingQueue;
        private final AsyncDispatch<Message> incomingQueue;
        private final DeferredConnection connection = new DeferredConnection();

        private Channel(URI sourceAddress, URI destinationAddress, Dispatch<Message> incomingDispatch) {
            this.sourceAddress = sourceAddress;
            this.destinationAddress = destinationAddress;
            outgoingQueue = new AsyncDispatch<Message>(executorService, connection);
            outgoingDispatch = new EndOfStreamDispatch(outgoingQueue);
            this.incomingDispatch = new EndOfStreamFilter(incomingDispatch, new Runnable() {
                public void run() {
                    requestStop();
                }
            });
            this.incomingQueue = new AsyncDispatch<Message>(executorService, this.incomingDispatch);
            incomingQueue.receiveFrom(connection);
        }

        public void setConnection(Connection<Message> connection) {
            this.connection.connect(connection);
        }

        public void dispatch(Message message) {
            outgoingDispatch.dispatch(message);
        }

        public URI getLocalAddress() {
            if (sourceAddress == null) {
                throw new UnsupportedOperationException();
            }
            return sourceAddress;
        }

        public URI getRemoteAddress() {
            if (destinationAddress == null) {
                throw new UnsupportedOperationException();
            }
            return destinationAddress;
        }

        public void requestStop() {
            outgoingDispatch.stop();
        }

        public void stop() {
            Future<?> stopJob = executorService.submit(new Runnable() {
                public void run() {
                    // End-of-stream handshake
                    requestStop();
                    incomingDispatch.stop();

                    // Flush queues (should be empty)
                    incomingQueue.requestStop();
                    outgoingQueue.requestStop();
                    incomingQueue.stop();
                    outgoingQueue.stop();
                }
            });
            try {
                stopJob.get(2, TimeUnit.MINUTES);
            } catch (Exception e) {
                throw new GradleException("Could not stop connection.", e);
            }
        }
    }

    private class IncomingChannelHandler implements Runnable {

        private final Connection<Message> connection;

        public IncomingChannelHandler(Connection<Message> connection) {
            this.connection = connection;
        }

        public void run() {
            ConnectRequest request = (ConnectRequest) connection.receive();
            Channel channel;
            lock.lock();
            try {
                channel = pending.remove(request.getDestinationAddress());
                if (channel == null) {
                    throw new IllegalStateException(String.format("Request to connect received for unknown address '%s'.",
                            request.getDestinationAddress()));
                }
            } finally {
                lock.unlock();
            }

            channel.setConnection(connection);
        }
    }
}
