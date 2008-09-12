/*
 * Copyright 2007, 2008 the original author or authors.
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
package org.gradle.api.internal.tasks;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.Expectations;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.Task;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.util.WrapUtil;

@RunWith(JMock.class)
public class DefaultTaskDependencyTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final DefaultTaskDependency dependency = new DefaultTaskDependency();
    private Task task;
    private Project project;
    private Task otherTask;

    @Before
    public void setUp() throws Exception {
        task = context.mock(Task.class, "task");
        project = context.mock(Project.class);

        context.checking(new Expectations(){{
            allowing(task).getProject();
            will(returnValue(project));
        }});
        otherTask = context.mock(Task.class, "otherTask");
    }

    @Test
    public void hasNoDependenciesByDefault() {
        assertThat(dependency.getDependencies(task), equalTo(WrapUtil.<Task>toSet()));
    }

    @Test
    public void canDependOnATaskInstance() {
        dependency.add(otherTask);

        assertThat(dependency.getDependencies(task), equalTo(WrapUtil.<Task>toSet(otherTask)));
    }

    @Test
    public void canDependOnATaskDependency() {
        final TaskDependency otherDependency = context.mock(TaskDependency.class);
        dependency.add(otherDependency);

        context.checking(new Expectations() {{
            one(otherDependency).getDependencies(task);
            will(returnValue(WrapUtil.toSet(otherTask)));
        }});

        assertThat(dependency.getDependencies(task), equalTo(WrapUtil.<Task>toSet(otherTask)));
    }

    @Test
    public void treatsOtherObjectsAsATaskPath() {
        dependency.add(new StringBuffer("task"));
        
        context.checking(new Expectations(){{
            one(project).task("task");
            will(returnValue(otherTask));
        }});

        assertThat(dependency.getDependencies(task), equalTo(WrapUtil.<Task>toSet(otherTask)));
    }

}
