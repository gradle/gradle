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
package org.gradle.api.internal.tasks

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency
import org.gradle.util.JUnit4GroovyMockery
import static org.gradle.util.WrapUtil.*
import org.gradle.util.WrapUtil
import static org.hamcrest.Matchers.*
import org.jmock.integration.junit4.JMock
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.tasks.TaskContainer;

@RunWith (JMock.class)
public class DefaultTaskDependencyTest {
    private final JUnit4GroovyMockery context = new JUnit4GroovyMockery();
    private final DefaultTaskDependency dependency = new DefaultTaskDependency();
    private Task task;
    private Project project;
    private TaskContainer taskContainer;
    private Task otherTask;

    @Before
    public void setUp() throws Exception {
        task = context.mock(Task.class, "task");
        project = context.mock(Project.class);
        taskContainer = context.mock(TaskContainer.class)

        context.checking({
            allowing(task).getProject()
            will(returnValue(project))
            allowing(project).getTasks()
            will(returnValue(taskContainer))
        });
        otherTask = context.mock(Task.class, "otherTask");
    }

    @Test
    public void hasNoDependenciesByDefault() {
        assertThat(dependency.getDependencies(task), equalTo(WrapUtil.toSet()));
    }

    @Test
    public void canDependOnATaskInstance() {
        dependency.add(otherTask);

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void canDependOnATaskDependency() {
        final TaskDependency otherDependency = context.mock(TaskDependency.class);
        dependency.add(otherDependency);

        context.checking({
            one(otherDependency).getDependencies(task);
            will(returnValue(toSet(otherTask)));
        });

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void canDependOnAClosure() {
        dependency.add({Task suppliedTask ->
            assertThat(suppliedTask, sameInstance(task))
            otherTask
        })

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void closureCanReturnACollection() {
        dependency.add({ toList(otherTask) })

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void treatsOtherObjectsAsATaskPath() {
        dependency.add(new StringBuffer("task"));

        context.checking({
            one(taskContainer).getByPath("task");
            will(returnValue(otherTask));
        });

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void flattensCollections() {
        dependency.add(toList(otherTask));

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

    @Test
    public void flattensMaps() {
        dependency.add(toMap("key", otherTask));

        assertThat(dependency.getDependencies(task), equalTo(toSet(otherTask)));
    }

}
