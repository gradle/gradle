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
package org.gradle.process.internal;

import org.gradle.messaging.remote.ObjectConnection;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.gradle.util.Matchers.*;
import static org.hamcrest.Matchers.notNullValue;

@RunWith(JMock.class)
public class DefaultWorkerProcessTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ObjectConnection connection = context.mock(ObjectConnection.class);
    private final ExecHandle execHandle = context.mock(ExecHandle.class);

    @Test
    public void stopsConnectionWhenProcessStops() {
        final Collector<ExecHandleListener> listener = collector();

        context.checking(new Expectations() {{
            one(execHandle).addListener(with(notNullValue(ExecHandleListener.class)));
            will(collectTo(listener));
        }});

        DefaultWorkerProcess process = new DefaultWorkerProcess(connection, execHandle);

        context.checking(new Expectations() {{
            one(connection).stop();
        }});

        listener.get().executionFinished(execHandle);
    }
}
