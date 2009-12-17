/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.listener;

import org.gradle.util.TestClosure;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.HelperUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class ListenerBroadcastTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ListenerBroadcast<TestListener> broadcast = new ListenerBroadcast<TestListener>(TestListener.class);

    @Test
    public void createsSourceObject() {
        assertThat(broadcast.getSource(), notNullValue());
        assertTrue(broadcast.getSource().equals(broadcast.getSource()));
        assertFalse(broadcast.getSource().equals("something"));
        assertFalse(broadcast.getSource().equals(null));
        assertFalse(broadcast.getSource().equals(new ListenerBroadcast<TestListener>(TestListener.class).getSource()));
        assertEquals(broadcast.getSource().hashCode(), broadcast.getSource().hashCode());
        assertThat(broadcast.getSource().toString(), equalTo("TestListener broadcast"));
    }

    @Test
    public void getTypeIsCorrect() {
        assertThat(broadcast.getType(), equalTo(TestListener.class));
    }

    @Test
    public void sourceObjectDoesNothingWhenNoListenersAdded() {
        broadcast.getSource().event1("param");
    }

    @Test
    public void sourceObjectNotifiesEachListenerInOrderAdded() {
        final TestListener listener1 = context.mock(TestListener.class, "listener1");
        final TestListener listener2 = context.mock(TestListener.class, "listener2");

        context.checking(new Expectations() {{
            one(listener1).event1("param");
            one(listener2).event1("param");
        }});

        broadcast.add(listener1);
        broadcast.add(listener2);

        broadcast.getSource().event1("param");
    }

    @Test
    public void canDispatchEventToListeners() throws NoSuchMethodException {
        final TestListener listener1 = context.mock(TestListener.class, "listener1");
        final TestListener listener2 = context.mock(TestListener.class, "listener2");

        context.checking(new Expectations() {{
            one(listener1).event1("param");
            one(listener2).event1("param");
        }});

        broadcast.add(listener1);
        broadcast.add(listener2);

        Event event = new Event(TestListener.class.getMethod("event1", String.class), new Object[] { "param" });
        broadcast.dispatch(event);
    }

    @Test
    public void listenerIsNotUsedAfterItIsRemoved() {
        TestListener listener = context.mock(TestListener.class);

        broadcast.add(listener);
        broadcast.remove(listener);

        broadcast.getSource().event1("param");
    }

    @Test
    public void canUseClosureForSingleEventMethod() {
        final TestClosure testClosure = context.mock(TestClosure.class);
        context.checking(new Expectations() {{
            one(testClosure).call("param");
            will(returnValue("ignore me"));
        }});

        broadcast.add("event1", toClosure(testClosure));
        broadcast.getSource().event1("param");
    }

    @Test
    public void doesNotNotifyClosureForOtherEventMethods() {
        final TestClosure testClosure = context.mock(TestClosure.class);

        broadcast.add("event1", toClosure(testClosure));
        broadcast.getSource().event2(9, "param");
    }

    @Test
    public void closureCanHaveFewerParametersThanEventMethod() {
        broadcast.add("event2", toClosure("{ a -> 'result' }"));
        broadcast.getSource().event2(1, "param");
        broadcast.getSource().event2(2, null);
    }

    @Test
    public void canAttachALogger() {
        final TestListener logger = context.mock(TestListener.class);
        broadcast.setLogger(logger);

        context.checking(new Expectations() {{
            one(logger).event1("param");
        }});

        broadcast.getSource().event1("param");
    }

    @Test
    public void canRemoveALogger() {
        final TestListener logger = context.mock(TestListener.class);
        broadcast.setLogger(logger);
        broadcast.remove(logger);

        broadcast.getSource().event1("param");
    }

    @Test
    public void discardsPreviousLoggerWhenLoggerAttached() {
        final TestListener oldLogger = context.mock(TestListener.class, "old");
        final TestListener logger = context.mock(TestListener.class, "new");
        assertThat(broadcast.setLogger(oldLogger), nullValue());
        assertThat(broadcast.setLogger(logger), sameInstance(oldLogger));

        context.checking(new Expectations() {{
            one(logger).event1("param");
        }});

        broadcast.getSource().event1("param");
    }

    @Test
    public void loggerIsNotifiedBeforeAnyListeners() {
        final TestListener listener = context.mock(TestListener.class, "listener");
        final TestListener logger = context.mock(TestListener.class, "logger");
        broadcast.add(listener);
        broadcast.setLogger(logger);

        context.checking(new Expectations() {{
            one(logger).event1("param");
            one(listener).event1("param");
        }});

        broadcast.getSource().event1("param");
    }

    @Test
    public void wrapsExceptionThrownByListener() {
        final TestListener listener = context.mock(TestListener.class);
        final RuntimeException failure = new RuntimeException();

        context.checking(new Expectations() {{
            one(listener).event1("param");
            will(throwException(failure));
        }});

        broadcast.add(listener);

        try {
            broadcast.getSource().event1("param");
            fail();
        } catch (ListenerNotificationException e) {
            assertThat(e.getMessage(), equalTo("Failed to notify test listener."));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void wrapsExceptionThrownByClosure() {
        final TestClosure testClosure = context.mock(TestClosure.class);
        final RuntimeException failure = new RuntimeException();

        context.checking(new Expectations() {{
            one(testClosure).call("param");
            will(throwException(failure));
        }});

        broadcast.add("event1", toClosure(testClosure));

        try {
            broadcast.getSource().event1("param");
            fail();
        } catch (ListenerNotificationException e) {
            assertThat(e.getMessage(), equalTo("Failed to notify test listener."));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void dispatchWrapsExceptionThrownByListener() throws NoSuchMethodException {
        final TestListener listener = context.mock(TestListener.class);
        final RuntimeException failure = new RuntimeException();

        context.checking(new Expectations() {{
            one(listener).event1("param");
            will(throwException(failure));
        }});

        broadcast.add(listener);

        try {
            broadcast.dispatch(new Event(TestListener.class.getMethod("event1", String.class), new Object[]{"param"}));
            fail();
        } catch (ListenerNotificationException e) {
            assertThat(e.getMessage(), equalTo("Failed to notify test listener."));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }


    private interface TestListener {
        void event1(String param);

        void event2(int value, String other);
    }
}