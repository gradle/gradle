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

import org.gradle.messaging.dispatch.Dispatch;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class ChannelMessageMarshallingDispatchTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Dispatch<Object> delegate = context.mock(Dispatch.class);
    private final ChannelMessageMarshallingDispatch dispatch = new ChannelMessageMarshallingDispatch(delegate);

    @Test
    public void mapsChannelKeyToIntegerChannelId() {
        final Message message1 = new TestMessage();
        final Message message2 = new TestMessage();

        context.checking(new Expectations() {{
            one(delegate).dispatch(new ChannelMetaInfo("channel", 0));
            one(delegate).dispatch(new ChannelMessage(0, message1));
            one(delegate).dispatch(new ChannelMessage(0, message2));
        }});

        dispatch.dispatch(new ChannelMessage("channel", message1));
        dispatch.dispatch(new ChannelMessage("channel", message2));
    }

    @Test
    public void mapsMultipleChannelsToDifferentIds() {
        final Message message1 = new TestMessage();
        final Message message2 = new TestMessage();

        context.checking(new Expectations() {{
            one(delegate).dispatch(new ChannelMetaInfo("channel1", 0));
            one(delegate).dispatch(new ChannelMessage(0, message1));
            one(delegate).dispatch(new ChannelMetaInfo("channel2", 1));
            one(delegate).dispatch(new ChannelMessage(1, message2));
        }});

        dispatch.dispatch(new ChannelMessage("channel1", message1));
        dispatch.dispatch(new ChannelMessage("channel2", message2));
    }

    @Test
    public void forwardsUnknownMessages() {
        final Message message = new TestMessage();

        context.checking(new Expectations() {{
            one(delegate).dispatch(message);
        }});

        dispatch.dispatch(message);
    }

    private static class TestMessage extends Message {
    }
}
