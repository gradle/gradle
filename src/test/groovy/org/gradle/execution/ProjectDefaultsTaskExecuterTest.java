/*
 * Copyright 2008 the original author or authors.
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
package org.gradle.execution;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.util.WrapUtil;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class ProjectDefaultsTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Project project = context.mock(Project.class);

    @Test public void usesProjectDefaultTasksFromInitialProject() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(WrapUtil.toList("a", "b")));
            Task task = context.mock(Task.class);
            atLeast(1).of(project).getTasksByName("a", true);
            will(returnValue(WrapUtil.toSet(task)));
            atLeast(1).of(project).getTasksByName("b", true);
            will(returnValue(WrapUtil.toSet(task)));
        }});

        TaskExecuter executer = new ProjectDefaultsTaskExecuter();
        assertThat(executer.hasNext(), equalTo(true));
        executer.select(project);
    }

    @Test public void failsWhenNoProjectDefaultTasksSpecified() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(WrapUtil.toList()));
        }});

        TaskExecuter executer = new ProjectDefaultsTaskExecuter();
        try {
            executer.select(project);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("No tasks have been specified and the project has not defined any default tasks."));
        }
    }

    @Test public void usesOnlyTheFirstProject() {
        final Project project1 = context.mock(Project.class, "project1");
        final Project project2 = context.mock(Project.class, "project2");

        context.checking(new Expectations() {{
            one(project1).getDefaultTasks();
            will(returnValue(WrapUtil.toList("a", "b")));
            Task task = context.mock(Task.class);
            atLeast(1).of(project1).getTasksByName("a", true);
            will(returnValue(WrapUtil.toSet(task)));
            atLeast(1).of(project1).getTasksByName("b", true);
            will(returnValue(WrapUtil.toSet(task)));
            atLeast(1).of(project2).getTasksByName("b", true);
            will(returnValue(WrapUtil.toSet(task)));
        }});

        TaskExecuter executer = new ProjectDefaultsTaskExecuter();
        executer.select(project1);
        executer.select(project2);
    }
}
