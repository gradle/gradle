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

import org.gradle.messaging.dispatch.Addressable;
import org.gradle.messaging.dispatch.AsyncStoppable;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class DefaultObjectConnectionTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private DefaultObjectConnection sender;
    private DefaultObjectConnection receiver;
    private final Addressable messageConnection = context.mock(Addressable.class);
    private final AsyncStoppable stopControl = context.mock(AsyncStoppable.class);

    @Before
    public void setUp() {
        IncomingMethodInvocationHandler senderIncoming = new IncomingMethodInvocationHandler(getClass().getClassLoader());
        IncomingMethodInvocationHandler receiverIncoming = new IncomingMethodInvocationHandler(
                getClass().getClassLoader());
        OutgoingMethodInvocationHandler senderOutgoing = new OutgoingMethodInvocationHandler(
                receiverIncoming.getIncomingDispatch());
        OutgoingMethodInvocationHandler receiverOutgoing = new OutgoingMethodInvocationHandler(
                senderIncoming.getIncomingDispatch());
        sender = new DefaultObjectConnection(messageConnection, stopControl, senderOutgoing, senderIncoming);
        receiver = new DefaultObjectConnection(messageConnection, stopControl, receiverOutgoing, receiverIncoming);
    }

    @Test
    public void createsProxyForOutgoingType() throws Exception {
        TestRemote proxy = sender.addOutgoing(TestRemote.class);
        assertThat(proxy, strictlyEqual(proxy));
        assertThat(proxy.toString(), equalTo("TestRemote broadcast"));
    }

    @Test
    public void deliversMethodInvocationsOnOutgoingObjectToHandlerObject() throws Exception {
        final TestRemote handler = context.mock(TestRemote.class);
        context.checking(new Expectations() {{
            one(handler).doStuff("param");
        }});
        receiver.addIncoming(TestRemote.class, handler);

        TestRemote proxy = sender.addOutgoing(TestRemote.class);
        proxy.doStuff("param");
    }

    @Test
    public void deliversMethodInvocationsOnOutgoingObjectToHandlerDispatch() throws Exception {
        final Dispatch<MethodInvocation> handler = context.mock(Dispatch.class);
        context.checking(new Expectations() {{
            one(handler).dispatch(new MethodInvocation(TestRemote.class.getMethod("doStuff", String.class),
                    new Object[]{"param"}));
        }});
        receiver.addIncoming(TestRemote.class, handler);

        TestRemote proxy = sender.addOutgoing(TestRemote.class);
        proxy.doStuff("param");
    }

    @Test
    public void canHaveMultipleOutgoingTypes() throws Exception {
        final TestRemote handler1 = context.mock(TestRemote.class);
        final TestRemote2 handler2 = context.mock(TestRemote2.class);

        context.checking(new Expectations() {{
            one(handler1).doStuff("handler 1");
            one(handler2).doStuff("handler 2");
        }});
        receiver.addIncoming(TestRemote.class, handler1);
        receiver.addIncoming(TestRemote2.class, handler2);

        TestRemote remote1 = sender.addOutgoing(TestRemote.class);
        TestRemote2 remote2 = sender.addOutgoing(TestRemote2.class);

        remote1.doStuff("handler 1");
        remote2.doStuff("handler 2");
    }

    @Test
    public void handlesTypesWithSuperTypes() {
        final TestRemote3 handler = context.mock(TestRemote3.class);

        context.checking(new Expectations() {{
            one(handler).doStuff("handler 1");
        }});
        receiver.addIncoming(TestRemote3.class, handler);

        TestRemote3 remote1 = sender.addOutgoing(TestRemote3.class);

        remote1.doStuff("handler 1");
    }

    @Test
    public void handlesMethodInvocationOnUnknownType() {
        TestRemote remote = sender.addOutgoing(TestRemote3.class);
        try {
            remote.doStuff("handler");
            fail();
        } catch (IllegalStateException e) {
            assertThat(e.getMessage(), equalTo("Received method invocation for unknown type '" + TestRemote.class.getName() + "'."));
        }
    }

    @Test
    public void cannotRegisterMultipleHandlerObjectsWithSameType() {
        TestRemote handler = context.mock(TestRemote.class);
        receiver.addIncoming(TestRemote.class, handler);

        try {
            receiver.addIncoming(TestRemote.class, handler);
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("A handler has already been added for type '" + TestRemote.class.getName() + "'."));
        }
    }

    @Test
    public void cannotRegisterMultipleHandlerObjectsWithOverlappingMethods() {
        receiver.addIncoming(TestRemote3.class, context.mock(TestRemote3.class));

        try {
            receiver.addIncoming(TestRemote.class, context.mock(TestRemote.class));
            fail();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), equalTo("A handler has already been added for type '" + TestRemote.class.getName() + "'."));
        }
    }

    @Test
    public void canCreateMultipleOutgoingObjectsWithSameType() {
        sender.addOutgoing(TestRemote.class);
        sender.addOutgoing(TestRemote.class);
    }

    @Test
    public void stopsConnectionOnStop() {
        context.checking(new Expectations() {{
            one(stopControl).stop();
        }});

        receiver.stop();
    }
    
    public interface TestRemote {
        void doStuff(String param);
    }

    public interface TestRemote2 {
        void doStuff(String param);
    }

    public interface TestRemote3 extends TestRemote {
    }
}
