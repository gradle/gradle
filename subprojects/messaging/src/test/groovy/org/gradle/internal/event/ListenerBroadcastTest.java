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

package org.gradle.internal.event;

import org.gradle.api.Action;
import org.gradle.messaging.dispatch.Dispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.strictlyEqual;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class ListenerBroadcastTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ListenerBroadcast<TestListener> broadcast = new ListenerBroadcast<TestListener>(TestListener.class);

    @Test
    public void createsSourceObject() {
        assertThat(broadcast.getSource(), notNullValue());
        assertThat(broadcast.getSource(), strictlyEqual(broadcast.getSource()));
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

        MethodInvocation invocation = new MethodInvocation(TestListener.class.getMethod("event1", String.class), new Object[]{"param"});
        broadcast.dispatch(invocation);
    }

    @Test
    public void listenerIsNotUsedAfterItIsRemoved() {
        TestListener listener = context.mock(TestListener.class);

        broadcast.add(listener);
        broadcast.remove(listener);

        broadcast.getSource().event1("param");
    }

    @Test
    public void canUseDispatchToReceiveNotifications() throws NoSuchMethodException {
        final Dispatch<MethodInvocation> dispatch1 = context.mock(Dispatch.class, "listener1");
        final Dispatch<MethodInvocation> dispatch2 = context.mock(Dispatch.class, "listener2");
        final MethodInvocation invocation = new MethodInvocation(TestListener.class.getMethod("event1", String.class), new Object[]{"param"});

        context.checking(new Expectations() {{
            one(dispatch1).dispatch(invocation);
            one(dispatch2).dispatch(invocation);
        }});

        broadcast.add(dispatch1);
        broadcast.add(dispatch2);

        broadcast.getSource().event1("param");
    }

    @Test
    public void dispatchIsNotUsedAfterItIsRemoved() {
        Dispatch<MethodInvocation> dispatch = context.mock(Dispatch.class);

        broadcast.add(dispatch);
        broadcast.remove(dispatch);

        broadcast.getSource().event1("param");
    }

    @Test
    public void canUseActionForSingleEventMethod() {
        final Action<String> action = context.mock(Action.class);
        context.checking(new Expectations() {{
            one(action).execute("param");
        }});

        broadcast.add("event1", action);
        broadcast.getSource().event1("param");
    }

    @Test
    public void doesNotNotifyActionForOtherEventMethods() {
        final Action<String> action = context.mock(Action.class);

        broadcast.add("event1", action);
        broadcast.getSource().event2(9, "param");
    }

    @Test
    public void actionCanHaveFewerParametersThanEventMethod() {
        final Action<Integer> action = context.mock(Action.class);
        context.checking(new Expectations() {{
            one(action).execute(1);
            one(action).execute(2);
        }});
        broadcast.add("event2", action);
        broadcast.getSource().event2(1, "param");
        broadcast.getSource().event2(2, null);
    }

    @Test
    public void listenerCanAddAnotherListener() {
        final TestListener listener1 = context.mock(TestListener.class, "listener1");
        final TestListener listener2 = context.mock(TestListener.class, "listener2");
        final TestListener listener3 = context.mock(TestListener.class, "listener3");

        broadcast.add(listener1);
        broadcast.add(listener2);

        context.checking(new Expectations() {{
            ignoring(listener2);
            one(listener1).event1("event");
            will(new org.jmock.api.Action() {
                public void describeTo(Description description) {
                    description.appendText("add listener");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    broadcast.add(listener3);
                    return null;
                }
            });
        }});

        broadcast.getSource().event1("event");
    }

    @Test
    public void wrapsCheckedExceptionThrownByListener() throws Exception {
        final TestListener listener = context.mock(TestListener.class);
        final Exception failure = new Exception();

        context.checking(new Expectations() {{
            one(listener).event3();
            will(throwException(failure));
        }});

        broadcast.add(listener);

        try {
            broadcast.getSource().event3();
            fail();
        } catch (ListenerNotificationException e) {
            assertThat(e.getMessage(), equalTo("Failed to notify test listener."));
            assertThat(e.getCause(), sameInstance((Throwable) failure));
        }
    }

    @Test
    public void attemptsToNotifyAllOtherListenersWhenOneThrowsException() {
        final TestListener listener1 = context.mock(TestListener.class);
        final TestListener listener2 = context.mock(TestListener.class);
        final RuntimeException failure = new RuntimeException();

        context.checking(new Expectations() {{
            one(listener1).event1("param");
            will(throwException(failure));
            one(listener2).event1("param");
        }});

        broadcast.add(listener1);
        broadcast.add(listener2);

        try {
            broadcast.getSource().event1("param");
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }

    @Test
    public void attemptsToNotifyAllOtherListenersWhenMultipleThrowException() {
        final TestListener listener1 = context.mock(TestListener.class);
        final TestListener listener2 = context.mock(TestListener.class);
        final TestListener listener3 = context.mock(TestListener.class);
        final RuntimeException failure1 = new RuntimeException();
        final RuntimeException failure2 = new RuntimeException();

        context.checking(new Expectations() {{
            one(listener1).event1("param");
            will(throwException(failure1));
            one(listener2).event1("param");
            will(throwException(failure2));
            one(listener3).event1("param");
        }});

        broadcast.add(listener1);
        broadcast.add(listener2);
        broadcast.add(listener3);

        try {
            broadcast.getSource().event1("param");
            fail();
        } catch (ListenerNotificationException e) {
            assertThat(e.getCauses().size(), equalTo(2));
            assertThat(e.getCauses().get(0), sameInstance((Throwable) failure1));
            assertThat(e.getCauses().get(1), sameInstance((Throwable) failure2));
        }
    }

    public interface TestListener {
        void event1(String param);

        void event2(int value, String other);

        void event3() throws Exception;
    }
}
