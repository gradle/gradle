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
package org.gradle.listener;

import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DefaultListenerManagerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ListenerManager manager = new DefaultListenerManager();

    private final TestFooListener fooListener1 = context.mock(TestFooListener.class, "foo listener 1");
    private final TestFooListener fooListener2 = context.mock(TestFooListener.class, "foo listener 2");
    private final TestFooListener fooListener3 = context.mock(TestFooListener.class, "foo listener 3");
    private final TestFooListener fooListener4 = context.mock(TestFooListener.class, "foo listener 4");
    private final TestBarListener barListener1 = context.mock(TestBarListener.class, "bar listener 1");

    @Test
    public void canAddListenerBeforeObtainingBroadcaster() {
        manager.addListener(fooListener1);

        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});

        manager.getBroadcaster(TestFooListener.class).foo("param");
    }

    @Test
    public void canAddListenerAfterObtainingBroadcaster() {
        TestFooListener broadcaster = manager.getBroadcaster(TestFooListener.class);

        manager.addListener(fooListener1);

        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});

        broadcaster.foo("param");
    }

    @Test
    public void canAddLoggerBeforeObtainingBroadcaster() {
        manager.useLogger(fooListener1);

        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});

        manager.getBroadcaster(TestFooListener.class).foo("param");
    }

    @Test
    public void canAddLoggerAfterObtainingBroadcaster() {
        TestFooListener broadcaster = manager.getBroadcaster(TestFooListener.class);

        manager.useLogger(fooListener1);

        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});

        broadcaster.foo("param");
    }

    @Test
    public void addedListenersGetMessagesInOrderAdded() {
        context.checking(new Expectations() {{
            Sequence sequence = context.sequence("sequence");
            one(fooListener1).foo("param"); inSequence(sequence);
            one(fooListener2).foo("param"); inSequence(sequence);
            one(fooListener3).foo("param"); inSequence(sequence);
            one(fooListener4).foo("param"); inSequence(sequence);
        }});

        manager.addListener(fooListener1);
        manager.addListener(barListener1);
        manager.addListener(fooListener2);
        manager.addListener(fooListener3);

        // get the broadcaster and then add more listeners (because broadcasters
        // are cached and so must be maintained correctly after getting defined
        TestFooListener broadcaster = manager.getBroadcaster(TestFooListener.class);

        manager.addListener(fooListener4);

        broadcaster.foo("param");
    }

    @Test
    public void cachesBroadcasters() {
        assertSame(manager.getBroadcaster(TestFooListener.class), manager.getBroadcaster(TestFooListener.class));
    }

    @Test
    public void removedListenersDontGetMessages() {
        manager.addListener(fooListener1);
        manager.addListener(fooListener2);

        manager.removeListener(fooListener2);

        TestFooListener testFooListener = manager.getBroadcaster(TestFooListener.class);

        manager.removeListener(fooListener1);

        testFooListener.foo("param");
    }

    @Test
    public void replacedLoggersDontGetMessages() {
        context.checking(new Expectations() {{
            one(fooListener4).foo("param");
        }});

        manager.useLogger(fooListener1);
        manager.useLogger(fooListener2);

        TestFooListener testFooListener = manager.getBroadcaster(TestFooListener.class);

        manager.useLogger(fooListener3);
        manager.useLogger(fooListener4);

        testFooListener.foo("param");
    }

    @Test
    public void listenerReceivesEventsFromAnonymousBroadcasters() {
        manager.addListener(fooListener1);

        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});

        manager.createAnonymousBroadcaster(TestFooListener.class).getSource().foo("param");
    }

    @Test
    public void listenerReceivesEventsFromChildren() {
        manager.addListener(fooListener1);

        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});

        manager.createChild().getBroadcaster(TestFooListener.class).foo("param");
    }
    
    @Test
    public void listenerDoesNotReceiveEventsFromParent() {
        manager.createChild().addListener(fooListener1);

        manager.getBroadcaster(TestFooListener.class).foo("param");
    }

    @Test
    public void loggerReceivesEventsFromChildren() {
        manager.useLogger(fooListener1);

        ListenerManager child = manager.createChild();
        TestFooListener broadcaster = child.getBroadcaster(TestFooListener.class);

        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});
        broadcaster.foo("param");

        manager.useLogger(fooListener2);

        context.checking(new Expectations() {{
            one(fooListener2).foo("param");
        }});
        broadcaster.foo("param");
    }

    @Test
    public void loggerDoesNotReceiveEventsFromParent() {
        manager.createChild().useLogger(fooListener1);

        manager.getBroadcaster(TestFooListener.class).foo("param");
    }

    @Test
    public void loggerInChildHasPrecedenceOverLoggerInParent() {
        manager.useLogger(fooListener1);

        ListenerManager child = manager.createChild();
        TestFooListener broadcaster = child.getBroadcaster(TestFooListener.class);

        child.useLogger(fooListener2);

        context.checking(new Expectations() {{
            one(fooListener2).foo("param");
        }});

        broadcaster.foo("param");
    }

    public interface TestFooListener {
        void foo(String param);
    }

    public interface TestBarListener {
        void bar(int value);
    }
}

