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
package org.gradle.messaging;

import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;

import org.gradle.messaging.dispatch.Connector;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.Message;
import org.gradle.messaging.dispatch.OutgoingConnection;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultMessagingServerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Connector connector = context.mock(Connector.class);
    private int counter;
    private final DefaultMessagingServer server = new DefaultMessagingServer(connector, getClass().getClassLoader());

    @Test
    public void createsConnection() {
        expectMessageConnectionCreated();

        ObjectConnection connection = server.createUnicastConnection();
        assertThat(connection, instanceOf(DefaultObjectConnection.class));
    }

    @Test
    public void stopsAllConnectionsOnStop() {
        final OutgoingConnection<Message> connection1 = expectMessageConnectionCreated();
        final OutgoingConnection<Message> connection2 = expectMessageConnectionCreated();

        server.createUnicastConnection();
        server.createUnicastConnection();

        context.checking(new Expectations() {{
            one(connection1).requestStop();
            one(connection2).requestStop();
            one(connection1).stop();
            one(connection2).stop();
        }});

        server.stop();
    }

    @Test
    public void discardsConnectionWhenItIsStopped() {
        final OutgoingConnection<Message> connection1 = expectMessageConnectionCreated();

        ObjectConnection objectConnection = server.createUnicastConnection();

        context.checking(new Expectations() {{
            one(connection1).stop();
        }});

        objectConnection.stop();
        server.stop();
    }

    private OutgoingConnection<Message> expectMessageConnectionCreated() {
        final OutgoingConnection<Message> messageConnection = context.mock(OutgoingConnection.class, String.valueOf(
                counter++));
        context.checking(new Expectations() {{
            one(connector).accept(with(notNullValue(Dispatch.class)));
            will(returnValue(messageConnection));
        }});
        return messageConnection;
    }
}
