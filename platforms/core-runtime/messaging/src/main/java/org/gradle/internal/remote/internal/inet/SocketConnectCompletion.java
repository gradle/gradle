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

package org.gradle.internal.remote.internal.inet;

import org.gradle.internal.remote.internal.ConnectCompletion;
import org.gradle.internal.remote.internal.KryoBackedMessageSerializer;
import org.gradle.internal.remote.internal.RemoteConnection;
import org.gradle.internal.serialize.StatefulSerializer;

import java.nio.channels.SocketChannel;

class SocketConnectCompletion implements ConnectCompletion {
    private final SocketChannel socket;

    public SocketConnectCompletion(SocketChannel socket) {
        this.socket = socket;
    }

    @Override
    public String toString() {
        return socket.socket().getLocalSocketAddress() + " to " + socket.socket().getRemoteSocketAddress();
    }

    @Override
    public <T> RemoteConnection<T> create(StatefulSerializer<T> serializer) {
        return new SocketConnection<T>(socket, new KryoBackedMessageSerializer(), serializer);
    }
}
