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
package org.gradle.execution;

import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.jmock.Expectations;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.gradle.api.Project;
import org.gradle.api.tasks.diagnostics.TaskListTask;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.Collections;

@RunWith(JMock.class)
public class BuiltInTasksBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final BuiltInTasksBuildExecuter executer = new BuiltInTasksBuildExecuter();
    private final Project rootProject = context.mock(ProjectInternal.class, "root");
    private final Project project = context.mock(ProjectInternal.class, "project");
    private DefaultTaskExecuter taskExecuter;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        taskExecuter = context.mock(DefaultTaskExecuter.class);

        context.checking(new Expectations(){{
            allowing(project).getRootProject();
            will(returnValue(rootProject));
            allowing(project).absolutePath("taskList");
            will(returnValue(":path"));
        }});
    }

    @Test
    public void hasNextBeforeFirstSelection() {
        assertThat(executer.hasNext(), equalTo(true));
        executer.select(project);
        assertThat(executer.hasNext(), equalTo(false));
    }

    @Test
    public void executesTaskListTask() {
        executer.select(project);
        assertThat(executer.getTask(), instanceOf(TaskListTask.class));

        context.checking(new Expectations(){{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});

        assertThat(executer.getDescription(), equalTo("taskList"));
        executer.execute(taskExecuter);
    }

    @Test
    public void doesNotRequireProjectReload() {
        assertThat(executer.requiresProjectReload(), equalTo(false));
        executer.select(project);
        assertThat(executer.requiresProjectReload(), equalTo(false));
    }

}
