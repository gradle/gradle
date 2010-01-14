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

import org.gradle.api.internal.TaskOutputsInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.tasks.DefaultTaskInputs;
import org.gradle.api.internal.tasks.DefaultTaskOutputs;
import org.gradle.api.tasks.TaskInputs;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class TaskInternalServiceRegistryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ServiceRegistry parent = context.mock(ServiceRegistry.class);
    private final ProjectInternal project = context.mock(ProjectInternal.class);
    private final TaskInternalServiceRegistry registry = new TaskInternalServiceRegistry(parent, project);

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
}
