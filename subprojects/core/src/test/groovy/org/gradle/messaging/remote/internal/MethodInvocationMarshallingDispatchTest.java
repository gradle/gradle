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
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(JMock.class)
public class MethodInvocationMarshallingDispatchTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Dispatch<Message> target = context.mock(Dispatch.class);
    private final MethodInvocationMarshallingDispatch dispatch = new MethodInvocationMarshallingDispatch(target);

    @Test
    public void sendsAMethodMetaInfoMessageWhenAMethodIsFirstReferenced() throws Exception {
        final Method method = String.class.getMethod("charAt", Integer.TYPE);

        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("seq");

            one(target).dispatch(new MethodMetaInfo(0, method));
            inSequence(sequence);

            one(target).dispatch(new RemoteMethodInvocation(0, new Object[]{17}));
            inSequence(sequence);

            one(target).dispatch(new RemoteMethodInvocation(0, new Object[]{12}));
            inSequence(sequence);
        }});

        dispatch.dispatch(new MethodInvocation(method, new Object[]{17}));
        dispatch.dispatch(new MethodInvocation(method, new Object[]{12}));
    }
}
