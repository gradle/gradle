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
package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskInputs;
import static org.gradle.util.GUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class DependencyAutoWireTaskFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ITaskFactory delegate = context.mock(ITaskFactory.class);
    private final DependencyAutoWireTaskFactory factory = new DependencyAutoWireTaskFactory(delegate);

    @Test
    public void addsDependencyOnInputFiles() {
        final TaskInternal task = context.mock(TaskInternal.class);
        final ProjectInternal project = context.mock(ProjectInternal.class);
        final TaskInputs taskInputs = context.mock(TaskInputs.class);
        final FileCollection inputFiles = context.mock(FileCollection.class);

        context.checking(new Expectations() {{
            one(delegate).createTask(project, map());
            will(returnValue(task));
            allowing(task).getInputs();
            will(returnValue(taskInputs));
            allowing(taskInputs).getFiles();
            will(returnValue(inputFiles));
            one(task).dependsOn(inputFiles);
        }});

        assertThat(factory.createTask(project, map()), sameInstance(task));
    }
}
