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
package org.gradle.messaging.remote;

import org.gradle.messaging.serialize.kryo.StatefulSerializer;

public interface ObjectConnectionBuilder {
    /**
     * Creates a transmitter for outgoing messages on the given type. The returned object is thread-safe.
     *
     * <p>Method invocations on the transmitter object are dispatched asynchronously to a corresponding handler in the peer. Method invocations are
     * called on the handler in the same order that they were called on the transmitter object.</p>
     *
     * @param type The type
     * @return A sink. Method calls made on this object are sent as outgoing messages.
     */
    <T> T addOutgoing(Class<T> type);

    /**
     * Registers a handler for incoming messages on the given type. The provided handler is not required to be
     * thread-safe. Messages are delivered to the handler by a single thread.
     *
     * <p>Method invocations are called on the given instance in the order that they were called on the transmitter object.</p>
     *
     * @param type The type.
     * @param instance The handler instance. Incoming messages on the given type are delivered to this handler.
     */
    <T> void addIncoming(Class<T> type, T instance);

    /**
     * Use the specified serializer for all incoming and outgoing parameters.
     */
    void useParameterSerializer(StatefulSerializer<Object[]> serializer);

    /**
     * Use Java serialization for the parameters of incoming and outgoing method calls, with the specified ClassLoader used to deserialize incoming
     * method parameters.
     *
     * <p>This method is generally not required as the ClassLoader is inferred from the incoming and outgoing types.</p>
     *
     * @param methodParamClassLoader The ClassLoader to use.
     */
    void useDefaultSerialization(ClassLoader methodParamClassLoader);
}
