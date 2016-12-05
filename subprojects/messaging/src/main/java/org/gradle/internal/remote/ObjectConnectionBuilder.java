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
package org.gradle.internal.remote;

import org.gradle.internal.serialize.SerializerRegistry;

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
     * <p>A handler instance may also implement {@link org.gradle.internal.dispatch.StreamCompletion}, in which case it will be notified when no further messages will be forwarded to it.
     * This may happen because the peer has signalled that it has finished sending messages, or closes the connection, or crashes. It may also happen when
     * this side of the connection is closed using {@link ObjectConnection#stop()}.
     * </p>
     *
     * <p>Method invocations are called on the given instance in the order that they were called on the transmitter object.</p>
     *
     * @param type The type.
     * @param instance The handler instance. Incoming messages on the given type are delivered to this handler.
     */
    <T> void addIncoming(Class<T> type, T instance);

    /**
     * Use the given Classloader to deserialize method parameters for method invocations received from the peer, for those types where Java serialization is used.
     */
    void useJavaSerializationForParameters(ClassLoader incomingMessageClassLoader);

    /**
     * Adds a set of specified serializers for incoming and outgoing method parameters. For any types that are not known to any registry added using this method, then Java serialization is used.
     */
    void useParameterSerializers(SerializerRegistry serializers);
}
