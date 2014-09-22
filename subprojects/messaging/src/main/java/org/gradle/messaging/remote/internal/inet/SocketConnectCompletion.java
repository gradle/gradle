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

package org.gradle.messaging.remote.internal.inet;

import org.gradle.messaging.remote.internal.*;

import java.nio.channels.SocketChannel;

class SocketConnectCompletion implements ConnectCompletion {
    private final SocketChannel socket;

    public SocketConnectCompletion(SocketChannel socket) {
        this.socket = socket;
    }

    @Override
    public String toString() {
        return String.format("%s to %s", socket.socket().getLocalSocketAddress(), socket.socket().getRemoteSocketAddress());
    }

    public <T> RemoteConnection<T> create(ClassLoader messageClassLoader) {
        return new SocketConnection<T>(socket, new DefaultMessageSerializer<T>(messageClassLoader));
    }

    public <T> RemoteConnection<T> create(MessageSerializer<T> serializer) {
        return new SocketConnection<T>(socket, serializer);
    }
}
