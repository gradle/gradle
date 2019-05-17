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
import org.gradle.api.GradleException;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ThreadSafe;
import org.gradle.internal.dispatch.BoundedDispatch;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.dispatch.ProxyDispatchAdapter;
import org.gradle.internal.dispatch.ReflectionDispatch;
import org.gradle.internal.dispatch.StreamCompletion;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.internal.ConnectCompletion;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.serialize.StatefulSerializer;
import org.gradle.internal.serialize.kryo.TypeSafeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MessageHubBackedObjectConnection implements ObjectConnection {
    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHubBackedObjectConnection.class);
    private final MessageHub hub;
    private final List<Action<Throwable>> unrecoverableErrorHandlers = new ArrayList<Action<Throwable>>();
    private ConnectCompletion completion;
    private RemoteConnection<InterHubMessage> connection;
    //    private ClassLoader methodParamClassLoader;
    private List<SerializerRegistry> paramSerializers = new ArrayList<SerializerRegistry>();
    private Set<ClassLoader> methodParamClassLoaders = new HashSet<ClassLoader>();
    private volatile boolean aborted;

    public MessageHubBackedObjectConnection(ExecutorFactory executorFactory, ConnectCompletion completion) {
        Action<Throwable> errorHandler = new Action<Throwable>() {
            @Override
            public void execute(Throwable throwable) {
                Throwable current = throwable;
                for (Action<Throwable> handler : unrecoverableErrorHandlers) {
                    try {
                        handler.execute(current);
                    } catch (Throwable e) {
                        current = new DefaultMultiCauseException("Error in unrecoverable error handler: " + handler, e, throwable);
                    }
                }
            }
        };
        this.hub = new MessageHub(completion.toString(), executorFactory, errorHandler);
        this.completion = completion;
        this.addUnrecoverableErrorHandler(new Action<Throwable>() {
            @Override
            public void execute(Throwable throwable) {
                if (!aborted && !Thread.currentThread().isInterrupted()) {
                    LOGGER.error("Unexpected exception thrown.", throwable);
                }
            }
        });
    }

    @Override
    public void useJavaSerializationForParameters(ClassLoader incomingMessageClassLoader) {
        methodParamClassLoaders.add(incomingMessageClassLoader);
    }

    @Override
    public <T> void addIncoming(Class<T> type, final T instance) {
        if (connection != null) {
            throw new GradleException("Cannot add incoming message handler after connection established.");
        }
        // we don't want to add core classloader explicitly here.
        if (type.getClassLoader() != getClass().getClassLoader()) {
            methodParamClassLoaders.add(type.getClassLoader());
        }
        Dispatch<MethodInvocation> handler = new DispatchWrapper<T>(instance);
        hub.addHandler(type.getName(), handler);
    }

    @Override
    public <T> T addOutgoing(Class<T> type) {
        if (connection != null) {
            throw new GradleException("Cannot add outgoing message transmitter after connection established.");
        }
        methodParamClassLoaders.add(type.getClassLoader());
        ProxyDispatchAdapter<T> adapter = new ProxyDispatchAdapter<T>(hub.getOutgoing(type.getName(), MethodInvocation.class), type, ThreadSafe.class);
        return adapter.getSource();
    }

    @Override
    public void useParameterSerializers(SerializerRegistry serializer) {
        this.paramSerializers.add(serializer);
    }

    @Override
    public void connect() {
        ClassLoader methodParamClassLoader;
        if (methodParamClassLoaders.size() == 0) {
            methodParamClassLoader = getClass().getClassLoader();
        } else if (methodParamClassLoaders.size() == 1) {
            methodParamClassLoader = methodParamClassLoaders.iterator().next();
        } else {
            methodParamClassLoader = new CachingClassLoader(new MultiParentClassLoader(methodParamClassLoaders));
        }
        MethodArgsSerializer argsSerializer = new DefaultMethodArgsSerializer(paramSerializers, new JavaSerializationBackedMethodArgsSerializer(methodParamClassLoader));

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

    @Override
    public void requestStop() {
        hub.requestStop();
    }

    @Override
    public void stop() {
        // TODO:ADAM - need to cleanup completion too, if not used
        CompositeStoppable.stoppable(hub, connection).stop();
    }

    @Override
    public void abort() {
        aborted = true;
        stop();
    }

    @Override
    public void addUnrecoverableErrorHandler(Action<Throwable> handler) {
        unrecoverableErrorHandlers.add(handler);
    }

    private static class DispatchWrapper<T> implements BoundedDispatch<MethodInvocation>, StreamFailureHandler {
        private final T instance;
        private final Dispatch<MethodInvocation> handler;

        DispatchWrapper(T instance) {
            this.instance = instance;
            this.handler = new ReflectionDispatch(instance);
        }

        @Override
        public void endStream() {
            if (instance instanceof StreamCompletion) {
                ((StreamCompletion)instance).endStream();
            }
        }

        @Override
        public void dispatch(MethodInvocation message) {
            handler.dispatch(message);
        }

        @Override
        public void handleStreamFailure(Throwable t) {
            if (instance instanceof StreamFailureHandler) {
                ((StreamFailureHandler)instance).handleStreamFailure(t);
            }
        }
    }
}
