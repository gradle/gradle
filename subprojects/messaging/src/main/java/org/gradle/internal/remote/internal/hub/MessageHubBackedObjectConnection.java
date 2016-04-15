/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.remote.internal.hub;

import org.gradle.api.Action;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.internal.dispatch.*;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.internal.ConnectCompletion;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.serialize.StatefulSerializer;
import org.gradle.internal.serialize.kryo.TypeSafeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageHubBackedObjectConnection implements ObjectConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHubBackedObjectConnection.class);
    private final MessageHub hub;
    private ConnectCompletion completion;
    private RemoteConnection<InterHubMessage> connection;
    private ClassLoader methodParamClassLoader;
    private SerializerRegistry paramSerializers;

    public MessageHubBackedObjectConnection(ExecutorFactory executorFactory, ConnectCompletion completion) {
        this.hub = new MessageHub(completion.toString(), executorFactory, new Action<Throwable>() {
            public void execute(Throwable throwable) {
                LOGGER.error("Unexpected exception thrown.", throwable);
            }
        });
        this.completion = completion;
    }

    @Override
    public void useJavaSerializationForParameters(ClassLoader incomingMessageClassLoader) {
        methodParamClassLoader = incomingMessageClassLoader;
    }

    public <T> void addIncoming(Class<T> type, final T instance) {
        if (methodParamClassLoader == null) {
            methodParamClassLoader = type.getClassLoader();
        }
        Dispatch<MethodInvocation> handler = new ReflectionDispatch(instance);
        if (instance instanceof StreamCompletion) {
            handler = new BoundedDispatchWrapper((StreamCompletion) instance, handler);
        }
        hub.addHandler(type.getName(), handler);
    }

    public <T> T addOutgoing(Class<T> type) {
        if (methodParamClassLoader == null) {
            methodParamClassLoader = type.getClassLoader();
        }
        ProxyDispatchAdapter<T> adapter = new ProxyDispatchAdapter<T>(hub.getOutgoing(type.getName(), MethodInvocation.class), type, ThreadSafe.class);
        return adapter.getSource();
    }

    public void useParameterSerializers(SerializerRegistry serializer) {
        this.paramSerializers = serializer;
    }

    public void connect() {
        if (methodParamClassLoader == null) {
            methodParamClassLoader = getClass().getClassLoader();
        }

        MethodArgsSerializer argsSerializer;
        if (paramSerializers != null) {
            argsSerializer = new DefaultMethodArgsSerializer(paramSerializers);
        } else {
            argsSerializer = new JavaSerializationBackedMethodArgsSerializer(methodParamClassLoader);
        }

        StatefulSerializer<InterHubMessage> serializer = new InterHubMessageSerializer(
                new TypeSafeSerializer<MethodInvocation>(MethodInvocation.class,
                        new MethodInvocationSerializer(
                                methodParamClassLoader,
                                argsSerializer)));

        connection = completion.create(serializer);
        hub.addConnection(connection);
        hub.noFurtherConnections();
        completion = null;
    }

    public void requestStop() {
        hub.requestStop();
    }

    public void stop() {
        // TODO:ADAM - need to cleanup completion too, if not used
        CompositeStoppable.stoppable(hub, connection).stop();
    }

    private static class BoundedDispatchWrapper implements BoundedDispatch<MethodInvocation> {
        private final StreamCompletion instance;
        private final Dispatch<MethodInvocation> handler;

        BoundedDispatchWrapper(StreamCompletion instance, Dispatch<MethodInvocation> handler) {
            this.instance = instance;
            this.handler = handler;
        }

        @Override
        public void endStream() {
            instance.endStream();
        }

        @Override
        public void dispatch(MethodInvocation message) {
            handler.dispatch(message);
        }
    }
}
