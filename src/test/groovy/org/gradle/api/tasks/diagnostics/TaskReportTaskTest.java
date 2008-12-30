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
package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.diagnostics.TaskReportRenderer;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@RunWith(JMock.class)
public class TaskReportTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private TaskReportRenderer printer;
    private ProjectInternal project;
    private TaskReportTask task;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        printer = context.mock(TaskReportRenderer.class);
        project = context.mock(ProjectInternal.class);

        context.checking(new Expectations(){{
            allowing(project).getRootProject();
            will(returnValue(project));
            allowing(project).absolutePath("list");
            will(returnValue(":path"));
        }});

        task = new TaskReportTask(project, "list");
        task.setFormatter(printer);
    }

    @Test
    public void isDagNeutral() {
        assertTrue(task.isDagNeutral());
    }

    @Test
    public void usesTaskListPrettyPrinterToWriteReport() {
        final SortedMap<Project, Set<Task>> tasks = new TreeMap<Project, Set<Task>>();

        context.checking(new Expectations(){{
            one(project).getAllTasks(true);
            will(returnValue(tasks));
            one(printer).getPrettyText(tasks);
            will(returnValue("<report>"));
        }});

        task.execute();
    }

}
