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
package org.gradle.api.testing.execution.control.server;

import org.gradle.api.testing.execution.PipelineDispatcher;
import org.gradle.api.testing.execution.control.messages.client.TestClientControlMessage;
import org.gradle.api.testing.execution.control.messages.server.TestServerControlMessage;
import org.gradle.api.testing.execution.control.server.messagehandlers.CompositeMessageHandler;
import org.gradle.api.testing.execution.control.server.messagehandlers.ForkStartedMessageHandler;
import org.gradle.api.testing.execution.control.server.messagehandlers.ForkStoppedMessageHandler;
import org.gradle.api.testing.execution.control.server.messagehandlers.NextActionRequestMessageHandler;
import org.gradle.messaging.ObjectConnection;
import org.gradle.messaging.TcpMessagingServer;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.Stoppable;

import java.net.URI;

/**
 * @author Tom Eyckmans
 */
public class ExternalControlServerFactory implements ControlServerFactory, Stoppable {
    private final TcpMessagingServer server;

    public ExternalControlServerFactory() {
        server = new TcpMessagingServer(TestServerControlMessage.class.getClassLoader());
    }

    public TestControlServer createTestControlServer(final PipelineDispatcher pipelineDispatcher) {
        CompositeMessageHandler handler = new CompositeMessageHandler(pipelineDispatcher);
        handler.add(new ForkStartedMessageHandler(pipelineDispatcher));
        handler.add(new ForkStoppedMessageHandler(pipelineDispatcher));
        handler.add(new NextActionRequestMessageHandler(pipelineDispatcher));
        pipelineDispatcher.setMessageHandler(handler);

        ObjectConnection connection = server.createUnicastConnection();
        final Dispatch<TestServerControlMessage> outgoing = connection.addOutgoing(Dispatch.class);
        Dispatch<TestClientControlMessage> incoming = new Dispatch<TestClientControlMessage>() {
            public void dispatch(final TestClientControlMessage message) {
                pipelineDispatcher.messageReceived(message, outgoing);
            }
        };
        connection.addIncoming(Dispatch.class, incoming);
        return new TestControlServerImpl(connection);
    }

    public void stop() {
        server.stop();
    }

    private class TestControlServerImpl implements TestControlServer {
        private final ObjectConnection connection;

        public TestControlServerImpl(ObjectConnection connection) {
            this.connection = connection;
        }

        public URI getLocalAddress() {
            return connection.getLocalAddress();
        }

        public void stop() {
            connection.stop();
        }
    }
}
