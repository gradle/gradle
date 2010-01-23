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
package org.gradle.messaging.dispatch;

import org.gradle.api.GradleException;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.SocketChannel;

public class TcpOutgoingConnector implements OutgoingConnector {
    private ClassLoader classLoader;

    public TcpOutgoingConnector(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public Connection<Message> create(URI destinationUri) {
        if (!destinationUri.getScheme().equals("tcp") || !destinationUri.getHost().equals("localhost")) {
            throw new IllegalArgumentException(String.format("Cannot create connection to destination URI '%s'.",
                    destinationUri));
        }
        
        try {
            SocketChannel socketChannel;
            socketChannel = SocketChannel.open(new InetSocketAddress(InetAddress.getByName(null),
                    destinationUri.getPort()));
            URI localAddress = new URI(String.format("tcp://localhost:%d", socketChannel.socket().getLocalPort()));
            return new SocketConnection(socketChannel, localAddress, destinationUri, classLoader);
        } catch (Exception e) {
            throw new GradleException(e);
        }
    }
}
