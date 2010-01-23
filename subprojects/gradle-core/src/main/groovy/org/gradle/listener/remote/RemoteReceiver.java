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

import org.gradle.messaging.dispatch.DefaultConnector;
import org.gradle.messaging.DefaultMessagingServer;
import org.gradle.messaging.ObjectConnection;
import org.gradle.messaging.dispatch.*;

import java.io.IOException;
import java.net.URI;

public class RemoteReceiver implements Stoppable {
    private final ObjectConnection connection;
    private final DefaultMessagingServer server;
    private final TcpIncomingConnector incomingConnector;
    private final DefaultConnector channelFactory;

    public RemoteReceiver(Class<?> type, Dispatch<? super MethodInvocation> dispatch) throws IOException {
        incomingConnector = new TcpIncomingConnector(type.getClassLoader());
        channelFactory = new DefaultConnector(new NoOpOutgoingConnector(), incomingConnector);
        server = new DefaultMessagingServer(channelFactory, type.getClassLoader());
        connection = server.createUnicastConnection();
        connection.addIncoming(type, dispatch);
    }

    public URI getLocalAddress() {
        return connection.getLocalAddress();
    }

    public void stop() {
        incomingConnector.requestStop();
        server.stop();
        channelFactory.stop();
        incomingConnector.stop();
    }

    private static class NoOpOutgoingConnector implements OutgoingConnector {
        public Connection<Message> create(URI destinationUri) {
            throw new UnsupportedOperationException();
        }
    }
}

