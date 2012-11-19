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

package org.gradle.api.internal.project;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.DefaultTaskInputs;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.internal.tasks.TaskStatusNagger;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.internal.Factory;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.logging.LoggingManagerInternal;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertSame;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class TaskInternalServiceRegistryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ServiceRegistry parent = context.mock(ServiceRegistry.class);
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final TaskInternal task = context.mock(TaskInternal.class);
    private final TaskInternalServiceRegistry registry = new TaskInternalServiceRegistry(parent, project, task);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(project).getFileResolver();
            will(returnValue(context.mock(FileResolver.class)));
        }});
    }

    @Test
    public void createsATaskInputsInstance() {
        TaskInputs inputs = registry.get(TaskInputs.class);
        assertThat(inputs, instanceOf(DefaultTaskInputs.class));
    }

    @Test
    public void createsATaskOutputsInternalInstance() {
        TaskOutputsInternal outputs = registry.get(TaskOutputsInternal.class);
        assertThat(outputs, instanceOf(DefaultTaskOutputs.class));
    }

    @Test
    public void createsATaskStatusNaggerInstance() {
        TaskStatusNagger nagger = registry.get(TaskStatusNagger.class);
        assertSame(nagger, registry.get(TaskStatusNagger.class));
    }

    @Test
    public void createsALoggingManagerAndStdOutputCapture() {
        final Factory<LoggingManagerInternal> loggingManagerFactory = context.mock(Factory.class);
        final LoggingManager loggingManager = context.mock(LoggingManagerInternal.class);

        context.checking(new Expectations() {{
            allowing(parent).getFactory(LoggingManagerInternal.class);
            will(returnValue(loggingManagerFactory));
            one(loggingManagerFactory).create();
            will(returnValue(loggingManager));
        }});

        assertThat(registry.get(LoggingManager.class), sameInstance(loggingManager));
    }
}
