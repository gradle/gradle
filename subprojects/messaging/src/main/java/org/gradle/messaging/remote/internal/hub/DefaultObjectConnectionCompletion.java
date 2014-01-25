/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.messaging.remote.ObjectConnectionCompletion;
import org.gradle.messaging.remote.internal.ConnectCompletion;
import org.gradle.messaging.remote.internal.Connection;
import org.gradle.messaging.remote.internal.MessageSerializer;
import org.gradle.messaging.remote.internal.hub.protocol.InterHubMessage;
import org.gradle.messaging.serialize.kryo.JavaSerializer;
import org.gradle.messaging.serialize.kryo.TypeSafeSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class DefaultObjectConnectionCompletion implements ObjectConnectionCompletion {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultObjectConnectionCompletion.class);
    private final ConnectCompletion completion;
    private final ExecutorFactory executorFactory;

    public DefaultObjectConnectionCompletion(ConnectCompletion completion, ExecutorFactory executorFactory) {
        this.completion = completion;
        this.executorFactory = executorFactory;
    }

    public ObjectConnection create(ClassLoader messageClassLoader) {
        MessageSerializer<InterHubMessage> serializer = new InterHubMessageSerializer(
                new TypeSafeSerializer<MethodInvocation>(
                        MethodInvocation.class,
                        new MethodInvocationSerializer(
                                messageClassLoader,
                                new JavaSerializer<Object[]>(
                                        messageClassLoader))));

        Connection<InterHubMessage> connection = completion.create(serializer);
        MessageHub hub = new MessageHub(connection.toString(), executorFactory, new Action<Throwable>() {
            public void execute(Throwable throwable) {
                LOGGER.error("Unexpected exception thrown.", throwable);
            }
        });
        return new MessageHubBackedObjectConnection(hub, connection);
    }
}
