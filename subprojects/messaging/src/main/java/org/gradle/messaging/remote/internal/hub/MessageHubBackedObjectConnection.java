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

package org.gradle.messaging.remote.internal.hub;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.ProxyDispatchAdapter;
import org.gradle.messaging.dispatch.ReflectionDispatch;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.internal.ConnectCompletion;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.serialize.kryo.JavaSerializer;
import org.gradle.messaging.serialize.kryo.StatefulSerializer;
import org.gradle.messaging.serialize.kryo.TypeSafeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHubBackedObjectConnection implements ObjectConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHubBackedObjectConnection.class);
    private final MessageHub hub;
    private ConnectCompletion completion;
    private Connection<InterHubMessage> connection;
    private ClassLoader methodParamClassLoader;
    private StatefulSerializer<Object[]> paramSerializer;

    public MessageHubBackedObjectConnection(ExecutorFactory executorFactory, ConnectCompletion completion) {
        this.hub = new MessageHub(completion.toString(), executorFactory, new Action<Throwable>() {
            public void execute(Throwable throwable) {
                LOGGER.error("Unexpected exception thrown.", throwable);
            }
        });
        this.completion = completion;
    }

    public <T> void addIncoming(Class<T> type, T instance) {
        if (methodParamClassLoader == null) {
            methodParamClassLoader = type.getClassLoader();
        }
        hub.addHandler(type.getName(), new ReflectionDispatch(instance));
    }

    public <T> T addOutgoing(Class<T> type) {
        if (methodParamClassLoader == null) {
            methodParamClassLoader = type.getClassLoader();
        }
        ProxyDispatchAdapter<T> adapter = new ProxyDispatchAdapter<T>(hub.getOutgoing(type.getName(), MethodInvocation.class), type, ThreadSafe.class);
        return adapter.getSource();
    }

    public void useDefaultSerialization(ClassLoader methodParamClassLoader) {
        this.methodParamClassLoader = methodParamClassLoader;
    }

    public void useParameterSerializer(StatefulSerializer<Object[]> serializer) {
        this.paramSerializer = serializer;
    }

    public void connect() {
        if (methodParamClassLoader == null) {
            methodParamClassLoader = getClass().getClassLoader();
        }

        StatefulSerializer<Object[]> paramSerializer = this.paramSerializer;
        if (paramSerializer == null) {
            paramSerializer = new JavaSerializer<Object[]>(methodParamClassLoader);
        }

        MessageSerializer<InterHubMessage> serializer = new InterHubMessageSerializer(
                new TypeSafeSerializer<MethodInvocation>(MethodInvocation.class, new MethodInvocationSerializer(
                                methodParamClassLoader,
                                paramSerializer)));

        connection = completion.create(serializer);
        hub.addConnection(connection);
        completion = null;
    }

    public void requestStop() {
        hub.requestStop();
    }

    public void stop() {
        // TODO:ADAM - need to cleanup completion too, if not used
        CompositeStoppable.stoppable(hub, connection).stop();
    }
}
