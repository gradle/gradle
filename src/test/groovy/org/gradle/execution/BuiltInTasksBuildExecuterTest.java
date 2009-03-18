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
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.internal.project.ProjectInternal;

import java.util.Collections;

@RunWith(JMock.class)
public class BuiltInTasksBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final BuiltInTasksBuildExecuter executer = new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.TASKS);
    private final Project rootProject = context.mock(ProjectInternal.class, "root");
    private final Project project = context.mock(ProjectInternal.class, "project");
    private TaskExecuter taskExecuter;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        taskExecuter = context.mock(TaskExecuter.class);

        context.checking(new Expectations(){{
            allowing(project).getRootProject();
            will(returnValue(rootProject));
            allowing(project).absolutePath(with(notNullValue(String.class)));
            will(returnValue(":path"));
        }});
    }

    @Test
    public void executesTaskReportTask() {
        executer.select(project);
        assertThat(executer.getTask(), instanceOf(TaskReportTask.class));

        context.checking(new Expectations(){{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});

        assertThat(executer.getDisplayName(), equalTo("task list"));
        executer.execute(taskExecuter);
    }

    @Test
    public void executesPropertyReportTask() {
        executer.setOptions(BuiltInTasksBuildExecuter.Options.PROPERTIES);
        
        executer.select(project);
        assertThat(executer.getTask(), instanceOf(PropertyReportTask.class));

        context.checking(new Expectations() {{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});

        assertThat(executer.getDisplayName(), equalTo("property list"));
        executer.execute(taskExecuter);
    }

    @Test
    public void executesDependencyReportTask() {
        executer.setOptions(BuiltInTasksBuildExecuter.Options.DEPENDENCIES);

        executer.select(project);
        assertThat(executer.getTask(), instanceOf(DependencyReportTask.class));

        context.checking(new Expectations() {{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});

        assertThat(executer.getDisplayName(), equalTo("dependency list"));
        executer.execute(taskExecuter);
    }
}
