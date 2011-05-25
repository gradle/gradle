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

import org.gradle.messaging.concurrent.*;
import org.gradle.messaging.dispatch.DiscardingFailureHandler;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.DispatchFailureHandler;
import org.gradle.messaging.remote.internal.protocol.ChannelMessage;
import org.gradle.util.IdGenerator;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MessageHub implements AsyncStoppable {
    private final Lock lock = new ReentrantLock();
    private final Set<StoppableExecutor> executors = new HashSet<StoppableExecutor>();
    private final Set<AsyncConnectionAdapter<Message>> connections = new HashSet<AsyncConnectionAdapter<Message>>();
    private final Set<ProtocolStack<ChannelMessage>> handlers = new HashSet<ProtocolStack<ChannelMessage>>();
    private ProtocolStack<ChannelMessage> unicastOutgoing;
    private ProtocolStack<ChannelMessage> broadcastOutgoing;
    private final DispatchFailureHandler<Object> failureHandler;
    private final Router router;
    private final String displayName;
    private final String nodeName;
    private final ExecutorFactory executorFactory;
    private final IdGenerator<?> idGenerator;
    private final ClassLoader messagingClassLoader;
    private final StoppableExecutor incomingExecutor;

    public MessageHub(String displayName, String nodeName, ExecutorFactory executorFactory, IdGenerator<?> idGenerator, ClassLoader messagingClassLoader) {
        this.displayName = displayName;
        this.nodeName = nodeName;
        this.executorFactory = executorFactory;
        this.idGenerator = idGenerator;
        this.messagingClassLoader = messagingClassLoader;
        failureHandler = new DiscardingFailureHandler<Object>(LoggerFactory.getLogger(MessageHub.class));
        StoppableExecutor executor = executorFactory.create(displayName + " message router");
        executors.add(executor);
        router = new Router(executor, failureHandler);

        incomingExecutor = executorFactory.create(displayName + " incoming");
        executors.add(incomingExecutor);
    }

    /**
     * Adds an incoming connection. Stops the connection when finished with it.
     */
    public void addConnection(Connection<Message> connection) {
        lock.lock();
        try {
            AsyncConnectionAdapter<Message> asyncConnection = new AsyncConnectionAdapter<Message>(connection, new EndOfStreamReceiveHandler(), executorFactory);
            connections.add(asyncConnection);

            AsyncConnection<Message> incomingEndpoint = router.createRemoteConnection();
            incomingEndpoint.dispatchTo(new MethodInvocationMarshallingDispatch(asyncConnection));
            asyncConnection.dispatchTo(new MethodInvocationUnmarshallingDispatch(incomingEndpoint, messagingClassLoader));
        } finally {
            lock.unlock();
        }
    }

    public Dispatch<Object> addUnicastOutgoing(String channel) {
        return new OutgoingMultiplex(channel, getUnicastOutgoing());
    }

    private Dispatch<ChannelMessage> getUnicastOutgoing() {
        lock.lock();
        try {
            if (unicastOutgoing == null) {
                Protocol<ChannelMessage> unicastSendProtocol = new InstancePerChannelProtocolAdapter<Object>(Object.class, new InstancePerChannelProtocolAdapter.ChannelProtocolFactory<Object>() {
                    public Protocol<Object> newChannel(Object channelKey) {
                        return new UnicastSendProtocol();
                    }
                });
                Protocol<ChannelMessage> sendProtocol = new InstancePerChannelProtocolAdapter<Object>(Object.class, new InstancePerChannelProtocolAdapter.ChannelProtocolFactory<Object>() {
                    public Protocol<Object> newChannel(Object channelKey) {
                        return new SendProtocol(idGenerator.generateId(), nodeName);
                    }
                });
                StoppableExecutor executor = executorFactory.create(displayName + " outgoing unicast");
                executors.add(executor);
                unicastOutgoing = new ProtocolStack<ChannelMessage>(executor, failureHandler, failureHandler, unicastSendProtocol, sendProtocol);

                AsyncConnection<Message> outgoingEndpoint = router.createLocalConnection();
                unicastOutgoing.getBottom().dispatchTo(outgoingEndpoint);
                outgoingEndpoint.dispatchTo(new TypeCastDispatch<ChannelMessage, Message>(ChannelMessage.class, unicastOutgoing.getBottom()));
            }
            return unicastOutgoing.getTop();
        } finally {
            lock.unlock();
        }
    }

    public Dispatch<Object> addMulticastOutgoing(String channel) {
        return new OutgoingMultiplex(channel, getMulticastOutgoing());
    }

    private Dispatch<ChannelMessage> getMulticastOutgoing() {
        lock.lock();
        try {
            if (broadcastOutgoing == null) {
                Protocol<ChannelMessage> unicastSendProtocol = new InstancePerChannelProtocolAdapter<Object>(Object.class, new InstancePerChannelProtocolAdapter.ChannelProtocolFactory<Object>() {
                    public Protocol<Object> newChannel(Object channelKey) {
                        return new BroadcastSendProtocol();
                    }
                });
                Protocol<ChannelMessage> sendProtocol = new InstancePerChannelProtocolAdapter<Object>(Object.class, new InstancePerChannelProtocolAdapter.ChannelProtocolFactory<Object>() {
                    public Protocol<Object> newChannel(Object channelKey) {
                        return new SendProtocol(idGenerator.generateId(), nodeName);
                    }
                });
                StoppableExecutor executor = executorFactory.create(displayName + " outgoing broadcast");
                executors.add(executor);
                broadcastOutgoing = new ProtocolStack<ChannelMessage>(executor, failureHandler, failureHandler, unicastSendProtocol, sendProtocol);

                AsyncConnection<Message> outgoingEndpoint = router.createLocalConnection();
                broadcastOutgoing.getBottom().dispatchTo(outgoingEndpoint);
                outgoingEndpoint.dispatchTo(new TypeCastDispatch<ChannelMessage, Message>(ChannelMessage.class, broadcastOutgoing.getBottom()));
            }
            return broadcastOutgoing.getTop();
        } finally {
            lock.unlock();
        }
    }

    public void addIncoming(String channel, final Dispatch<Object> dispatch) {
        lock.lock();
        try {
            final Object id = idGenerator.generateId();
            Protocol<ChannelMessage> workerProtocol = new InstancePerChannelProtocolAdapter<Object>(Object.class, new InstancePerChannelProtocolAdapter.ChannelProtocolFactory<Object>() {
                public Protocol<Object> newChannel(Object channelKey) {
                    return new WorkerProtocol(dispatch);
                }
            }, channel);
            Protocol<ChannelMessage> receiveProtocol = new InstancePerChannelProtocolAdapter<Message>(Message.class, new InstancePerChannelProtocolAdapter.ChannelProtocolFactory<Message>() {
                public Protocol<Message> newChannel(Object channelKey) {
                    return new ReceiveProtocol(id, nodeName);
                }
            }, channel);

            ProtocolStack<ChannelMessage> stack = new ProtocolStack<ChannelMessage>(incomingExecutor, failureHandler, failureHandler, workerProtocol, receiveProtocol);
            handlers.add(stack);

            AsyncConnection<Message> incomingEndpoint = router.createLocalConnection();
            stack.getBottom().dispatchTo(incomingEndpoint);
            incomingEndpoint.dispatchTo(new TypeCastDispatch<ChannelMessage, Message>(ChannelMessage.class, stack.getBottom()));
        } finally {
            lock.unlock();
        }
    }

    public void requestStop() {
        lock.lock();
        try {
            if (unicastOutgoing != null) {
                unicastOutgoing.requestStop();
            }
            if (broadcastOutgoing != null) {
                broadcastOutgoing.requestStop();
            }
            for (ProtocolStack<ChannelMessage> handler : handlers) {
                handler.requestStop();
            }
        } finally {
            lock.unlock();
        }
    }

    public void stop() {
        requestStop();

        CompositeStoppable stoppable = new CompositeStoppable();
        lock.lock();
        try {
            stoppable.add(unicastOutgoing);
            stoppable.add(broadcastOutgoing);
            stoppable.add(handlers);
            stoppable.add(connections);
            stoppable.add(router);
            stoppable.add(executors);
        } finally {
            executors.clear();
            unicastOutgoing = null;
            lock.unlock();
        }

        stoppable.stop();
    }
}
