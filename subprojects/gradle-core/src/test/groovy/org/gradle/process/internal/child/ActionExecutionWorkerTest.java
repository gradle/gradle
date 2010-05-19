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

package org.gradle.process.internal.child;

import org.gradle.api.Action;
import org.gradle.messaging.remote.MessagingClient;
import org.gradle.messaging.remote.ObjectConnection;
import org.gradle.process.internal.WorkerProcessContext;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@RunWith(JMock.class)
public class ActionExecutionWorkerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Action<WorkerProcessContext> action = context.mock(Action.class);
    private final MessagingClient client = context.mock(MessagingClient.class);
    private final WorkerContext workerContext = context.mock(WorkerContext.class);
    private final ClassLoader appClassLoader = new ClassLoader() {
    };
    private final ActionExecutionWorker main = new ActionExecutionWorker(action, 12, "<display name>", null) {
        @Override
        MessagingClient createClient() {
            return client;
        }
    };

    @Test
    public void createsConnectionAndExecutesAction() throws Exception {
        final ObjectConnection connection = context.mock(ObjectConnection.class);
        final Collector<WorkerProcessContext> collector = collector();

        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(WorkerProcessContext.class)));
            will(collectTo(collector));

            one(client).stop();
        }});

        main.execute(workerContext);

        context.checking(new Expectations() {{
            allowing(workerContext).getApplicationClassLoader();
            will(returnValue(appClassLoader));
            allowing(client).getConnection();
            will(returnValue(connection));
        }});

        assertThat(collector.get().getServerConnection(), sameInstance(connection));
        assertThat(collector.get().getApplicationClassLoader(), sameInstance(appClassLoader));
        assertThat(collector.get().getWorkerId(), equalTo((Object) 12));
        assertThat(collector.get().getDisplayName(), equalTo("<display name>"));
    }

    @Test
    public void cleansUpWhenActionThrowsException() throws Exception {
        final RuntimeException failure = new RuntimeException();


        context.checking(new Expectations() {{
            one(action).execute(with(notNullValue(WorkerProcessContext.class)));
            will(throwException(failure));

            one(client).stop();
        }});

        try {
            main.execute(workerContext);
            fail();
        } catch (RuntimeException e) {
            assertThat(e, sameInstance(failure));
        }
    }
}
