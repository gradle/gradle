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

import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.assertThat;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import static org.hamcrest.Matchers.equalTo;

@RunWith(JMock.class)
public class DefaultListenerManagerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ListenerManager manager = new DefaultListenerManager();

    private final TestFooListener fooListener1 = context.mock(TestFooListener.class, "foo listener 1");
    private final TestFooListener fooListener2 = context.mock(TestFooListener.class, "foo listener 2");
    private final TestBarListener barListener1 = context.mock(TestBarListener.class, "bar listener 1");

    @Test
    public void addedListenersGetMessages() {
        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
            one(fooListener2).foo("param");
        }});

        manager.addListener(fooListener1);
        manager.addListener(barListener1);

        // get the broadcaster and then add more listeners (because broadcasters
        // are cached and so must be maintained correctly after getting defined
        TestFooListener broadcaster = manager.getBroadcaster(TestFooListener.class);

        manager.addListener(fooListener2);

        assertThat(manager.getBroadcaster(TestFooListener.class), equalTo(broadcaster));
        broadcaster.foo("param");
    }

    @Test
    public void removedListenersDontGetMessages() {
        context.checking(new Expectations() {{
            one(fooListener1).foo("param");
        }});

        manager.addListener(fooListener1);
        manager.addListener(fooListener2);
        manager.addListener(barListener1);

        // get the broadcaster and then add more listeners (because broadcasters
        // are cached and so must be maintained correctly after getting defined
        TestFooListener broadcaster = manager.getBroadcaster(TestFooListener.class);

        manager.removeListener(fooListener2);

        assertThat(manager.getBroadcaster(TestFooListener.class), equalTo(broadcaster));
        broadcaster.foo("param");
    }

    private interface TestFooListener {
        void foo(String param);
    }

    private interface TestBarListener {
        void bar(int value);
    }
}

