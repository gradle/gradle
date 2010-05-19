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
import org.gradle.messaging.dispatch.MethodInvocation;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class MethodInvocationUnmarshallingDispatchTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Dispatch<MethodInvocation> target = context.mock(Dispatch.class);
    private final MethodInvocationUnmarshallingDispatch dispatch = new MethodInvocationUnmarshallingDispatch(target, getClass().getClassLoader());

    @Test
    public void doesNotForwardMethodMetaInfoMessages() throws Exception {
        dispatch.dispatch(new MethodMetaInfo(1, String.class.getMethod("charAt", Integer.TYPE)));
    }

    @Test
    public void transformsRemoteMethodInvocationMessage() throws Exception {
        final Method method = String.class.getMethod("charAt", Integer.TYPE);

        context.checking(new Expectations() {{
            one(target).dispatch(new MethodInvocation(method, new Object[]{17}));
        }});

        dispatch.dispatch(new MethodMetaInfo(1, method));
        dispatch.dispatch(new RemoteMethodInvocation(1, new Object[]{17}));
    }

    @Test
    public void failsWhenRemoteMethodInvocationMessageReceivedForUnknownMethod() {
        try {
            dispatch.dispatch(new RemoteMethodInvocation(1, new Object[]{17}));
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Received a method invocation message for an unknown method."));
        }
    }

    @Test
    public void failsWhenUnexpectedMessageReceived() {
        final Message message = new Message() {
        };

        try {
            dispatch.dispatch(message);
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), startsWith("Received an unknown message "));
        }
    }
}