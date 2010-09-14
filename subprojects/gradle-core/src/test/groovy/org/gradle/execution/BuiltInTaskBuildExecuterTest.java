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
package org.gradle.execution;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.tasks.diagnostics.AbstractReportTask;
import org.gradle.api.tasks.diagnostics.TaskReportTask;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Set;

import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class BuiltInTaskBuildExecuterTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final GradleInternal gradle = context.mock(GradleInternal.class);
    private final ProjectInternal project = HelperUtil.createRootProject();
    private final TaskGraphExecuter taskExecuter = context.mock(TaskGraphExecuter.class);
    private final ITaskFactory taskFactory = context.mock(ITaskFactory.class);
    private final BuiltInTaskBuildExecuter executer = new TestTaskBuildExecuter(null);
    private final ServiceRegistryFactory serviceRegistryFactory = context.mock(ServiceRegistryFactory.class);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(gradle).getDefaultProject();
            will(returnValue(project));
            allowing(gradle).getTaskGraph();
            will(returnValue(taskExecuter));
        }});
    }

    @Test
    public void executesReportTask() {
        expectTaskCreated();

        executer.select(gradle);
        assertThat(executer.getTask(), instanceOf(TaskReportTask.class));
        assertThat(executer.getDisplayName(), equalTo("task list"));

        expectTaskExecuted();

        executer.execute();
    }

    @Test
    public void executesAgainstDefaultProjectIfPathEmpty() {
        expectTaskCreated();

        executer.select(gradle);

        assertThat(executer.getTask().getProjects(), equalTo(toSet((Project) gradle.getDefaultProject())));
    }

    @Test
    public void executesAgainstSingleProjectSpecifiedByPath() {
        String somePath = ":SomePath";
        final ProjectInternal rootProject = context.mock(ProjectInternal.class, "rootProject");
        final ProjectInternal someProject = context.mock(ProjectInternal.class, "someProject");

        context.checking(new Expectations() {{
            allowing(gradle).getRootProject();
            will(returnValue(rootProject));
            allowing(rootProject).project(":SomePath");
            will(returnValue(someProject));
        }});

        BuiltInTaskBuildExecuter executer = new TestTaskBuildExecuter(somePath);

        expectTaskCreated();
        executer.select(gradle);

        assertThat(executer.getTask().getProjects(), equalTo(toSet((Project) someProject)));
    }

    @Test
    public void executesAgainstAllProjectWhenWildcardIsUsed() {
        final ProjectInternal rootProject = context.mock(ProjectInternal.class, "rootProject");
        final Set<Project> allProjects = toSet(context.mock(Project.class, "someProject"));
        context.checking(new Expectations() {{
            allowing(gradle).getRootProject();
            will(returnValue(rootProject));
            allowing(rootProject).getAllprojects();
            will(returnValue(allProjects));
        }});

        BuiltInTaskBuildExecuter executer = new TestTaskBuildExecuter(BuiltInTaskBuildExecuter.ALL_PROJECTS_WILDCARD);

        expectTaskCreated();

        executer.select(gradle);
        assertThat(executer.getTask().getProjects(), equalTo(allProjects));
    }

    private void expectTaskCreated() {
        context.checking(new Expectations(){{
            allowing(gradle).getServices();
            will(returnValue(serviceRegistryFactory));

            allowing(serviceRegistryFactory).get(ITaskFactory.class);
            will(returnValue(taskFactory));

            one(taskFactory).createTask(project, GUtil.map(Task.TASK_NAME, "report", Task.TASK_TYPE, TaskReportTask.class));
            will(returnValue(HelperUtil.createTask(TaskReportTask.class)));
        }});
    }

    private void expectTaskExecuted() {
        context.checking(new Expectations() {{
            one(taskExecuter).execute(Collections.singleton(executer.getTask()));
        }});
    }
    
    private class TestTaskBuildExecuter extends BuiltInTaskBuildExecuter {
        private TestTaskBuildExecuter(String path) {
            super(path);
        }

        @Override
        protected Class<? extends AbstractReportTask> getTaskType() {
            return TaskReportTask.class;
        }

        public String getDisplayName() {
            return "task list";
        }
    }
}