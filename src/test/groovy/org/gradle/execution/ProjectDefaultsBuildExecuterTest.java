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
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.equalTo;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith (org.jmock.integration.junit4.JMock.class)
public class ProjectDefaultsBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final Project project = context.mock(Project.class, "[project]");
    private final TaskExecuter taskExecuter = context.mock(TaskExecuter.class);

    @Test public void usesProjectDefaultTasksFromProject() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(toList("a", "b")));
            Task task = context.mock(Task.class);
            atLeast(1).of(project).getTasksByName("a", true);
            will(returnValue(toSet(task)));
            atLeast(1).of(project).getTasksByName("b", true);
            will(returnValue(toSet(task)));

            one(taskExecuter).addTasks(toSet(task));
            one(taskExecuter).addTasks(toSet(task));
            one(taskExecuter).execute();
        }});

        BuildExecuter executer = new ProjectDefaultsBuildExecuter();
        executer.select(project);
        executer.execute(taskExecuter);
    }

    @Test public void createsDescription() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(toList("a", "b")));
            Task task = context.mock(Task.class);
            atLeast(1).of(project).getTasksByName("a", true);
            will(returnValue(toSet(task)));
            atLeast(1).of(project).getTasksByName("b", true);
            will(returnValue(toSet(task)));
        }});

        BuildExecuter executer = new ProjectDefaultsBuildExecuter();
        executer.select(project);
        assertThat(executer.getDisplayName(), equalTo("project default tasks 'a', 'b'"));
    }

    @Test public void failsWhenNoProjectDefaultTasksSpecified() {
        context.checking(new Expectations() {{
            one(project).getDefaultTasks();
            will(returnValue(toList()));
        }});

        BuildExecuter executer = new ProjectDefaultsBuildExecuter();
        try {
            executer.select(project);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("No tasks have been specified and [project] has not defined any default tasks."));
        }
    }
}
