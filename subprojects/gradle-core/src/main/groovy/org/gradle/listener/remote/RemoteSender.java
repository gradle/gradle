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
package org.gradle.listener.remote;

import org.gradle.messaging.TcpMessagingClient;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;

public class RemoteSender<T> implements Closeable {
    private final T source;
    private final TcpMessagingClient client;

    public RemoteSender(Class<T> type, URI serverAddress) throws IOException {
        client = new TcpMessagingClient(type.getClassLoader(), serverAddress);
        source = client.getConnection().addOutgoing(type);
    }

    public T getSource() {
        return source;
    }

    public void close() throws IOException {
        client.stop();
    }
}
