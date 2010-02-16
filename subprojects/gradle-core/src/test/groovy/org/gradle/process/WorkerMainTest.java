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

package org.gradle.process;

import org.gradle.api.Action;
import org.gradle.messaging.MessagingClient;
import org.gradle.messaging.ObjectConnection;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class WorkerMainTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Action<WorkerProcessContext> action = context.mock(Action.class);
    private final MessagingClient client = context.mock(MessagingClient.class);
    private final ClassLoader appClassLoader = new ClassLoader() {
    };

    @Test
    public void createsConnectionAndExecutesAction() throws Exception {
        final ObjectConnection connection = context.mock(ObjectConnection.class);
        final Collector<WorkerProcessContext> collector = collector();

        WorkerMain main = new WorkerMain(action, client, appClassLoader);

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(WorkerProcessContext.class)));
            will(collectTo(collector));

            one(client).stop();
        }});

        main.run();

        context.checking(new Expectations() {{
            allowing(client).getConnection();
            will(returnValue(connection));
        }});

        assertThat(collector.get().getServerConnection(), sameInstance(connection));
        assertThat(collector.get().getApplicationClassLoader(), sameInstance(appClassLoader));
    }

    @Test
    public void cleansUpWhenActionThrowsException() throws Exception {
        final RuntimeException failure = new RuntimeException();

        WorkerMain main = new WorkerMain(action, client, appClassLoader);

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(WorkerProcessContext.class)));
            will(throwException(failure));

            one(client).stop();
        }});

        try {
            main.run();
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }
}
