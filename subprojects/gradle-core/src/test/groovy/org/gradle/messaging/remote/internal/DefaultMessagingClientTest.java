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
package org.gradle.messaging.remote.internal;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URI;

import static org.hamcrest.Matchers.*;

@RunWith(JMock.class)
public class DefaultMessagingClientTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final MultiChannelConnector connector = context.mock(MultiChannelConnector.class);

    @Test
    public void createsConnectionOnConstructionAndStopsOnStop() throws Exception {
        final URI serverAddress = new URI("test:somestuff");
        final MultiChannelConnection<Message> connection = context.mock(MultiChannelConnection.class);

        context.checking(new Expectations() {{
            one(connector).connect(with(equalTo(serverAddress)));
            will(returnValue(connection));
        }});

        DefaultMessagingClient client = new DefaultMessagingClient(connector, getClass().getClassLoader(), serverAddress);

        context.checking(new Expectations() {{
            one(connection).stop();
        }});
        client.stop();
    }

}
