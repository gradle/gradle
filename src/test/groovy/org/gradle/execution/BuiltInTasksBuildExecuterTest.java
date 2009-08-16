/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.api.tasks.diagnostics.PropertyReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

@RunWith(JMock.class)
public class BuiltInTasksBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final BuiltInTasksBuildExecuter executer = new BuiltInTasksBuildExecuter(BuiltInTasksBuildExecuter.Options.TASKS);
    private final BuildInternal build = context.mock(BuildInternal.class);
    private final ProjectInternal project = context.mock(ProjectInternal.class, "project");
    private final TaskExecuter taskExecuter = context.mock(TaskExecuter.class);

    @Before
    public void setUp() {
        context.checking(new Expectations(){{
            allowing(build).getDefaultProject();
            will(returnValue(project));
            allowing(build).getTaskGraph();
            will(returnValue(taskExecuter));
            allowing(project).absolutePath(with(notNullValue(String.class)));
            will(returnValue(":path"));
            allowing(project).getConvention();
            will(returnValue(null));
        }});
    }

    @Test
    public void executesTaskReportTask() {
        executer.select(build);
        assertThat(executer.getTask(), instanceOf(TaskReportTask.class));

        context.checking(new Expectations(){{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});

        assertThat(executer.getDisplayName(), equalTo("task list"));
        executer.execute();
    }

    @Test
    public void executesPropertyReportTask() {
        executer.setOptions(BuiltInTasksBuildExecuter.Options.PROPERTIES);
        
        executer.select(build);
        assertThat(executer.getTask(), instanceOf(PropertyReportTask.class));

        context.checking(new Expectations() {{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});

        assertThat(executer.getDisplayName(), equalTo("property list"));
        executer.execute();
    }

    @Test
    public void executesDependencyReportTask() {
        executer.setOptions(BuiltInTasksBuildExecuter.Options.DEPENDENCIES);

        executer.select(build);
        assertThat(executer.getTask(), instanceOf(DependencyReportTask.class));

        context.checking(new Expectations() {{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});

        assertThat(executer.getDisplayName(), equalTo("dependency list"));
        executer.execute();
    }
}
