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
package org.gradle.api.internal.project;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.TaskExecuter;
import static org.gradle.util.Matchers.*;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(JMock.class)
public class ExecutionShortCircuitTaskFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ITaskFactory delegate = context.mock(ITaskFactory.class);
    private final ProjectInternal projectInternal = context.mock(ProjectInternal.class);
    private final Map<String,String> options = WrapUtil.toMap("prop", "value");
    private final TaskInternal task = context.mock(TaskInternal.class);
    private final TaskExecuter delegateExecuter = context.mock(TaskExecuter.class);
    private final TaskArtifactStateRepository repository = context.mock(TaskArtifactStateRepository.class);
    private final ExecutionShortCircuitTaskFactory factory = new ExecutionShortCircuitTaskFactory(delegate, repository);

    @Test
    public void attachesATaskExecuterToTheTask() {
        context.checking(new Expectations() {{
            one(delegate).createTask(projectInternal, options);
            will(returnValue(task));

            allowing(task).getExecuter();
            will(returnValue(delegateExecuter));

            one(task).setExecuter(with(reflectionEquals(new ExecutionShortCircuitTaskExecuter(delegateExecuter,
                    repository))));
        }});

        assertThat(factory.createTask(projectInternal, options), sameInstance(task));
    }
}
