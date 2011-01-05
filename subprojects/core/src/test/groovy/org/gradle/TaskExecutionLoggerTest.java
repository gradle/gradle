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

package org.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.invocation.Gradle;
import org.gradle.logging.ProgressLogger;
import org.gradle.api.tasks.TaskState;
import org.gradle.logging.ProgressLoggerFactory;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class TaskExecutionLoggerTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ProgressLoggerFactory progressLoggerFactory = context.mock(ProgressLoggerFactory.class);
    private final Task task = context.mock(Task.class);
    private final TaskState state = context.mock(TaskState.class);
    private final ProgressLogger progressLogger = context.mock(ProgressLogger.class);
    private final Gradle gradle = context.mock(Gradle.class);
    private final TaskExecutionLogger executionLogger = new TaskExecutionLogger(progressLoggerFactory);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            Project project = context.mock(Project.class);
            allowing(task).getProject();
            will(returnValue(project));
            allowing(project).getGradle();
            will(returnValue(gradle));
            allowing(task).getPath();
            will(returnValue(":path"));
        }});
    }

    @Test
    public void logsExecutionOfTaskInRootBuild() {
        context.checking(new Expectations() {{
            allowing(gradle).getParent();
            will(returnValue(null));
            one(progressLoggerFactory).start(TaskExecutionLogger.class.getName(), ":path");
            will(returnValue(progressLogger));
            one(progressLogger).progress(":path");
        }});

        executionLogger.beforeExecute(task);

        context.checking(new Expectations() {{
            allowing(state).getSkipMessage();
            will(returnValue(null));
            one(progressLogger).completed();
        }});

        executionLogger.afterExecute(task, state);
    }

    @Test
    public void logsExecutionOfTaskInSubBuild() {
        context.checking(new Expectations() {{
            Project rootProject = context.mock(Project.class, "rootProject");

            allowing(gradle).getParent();
            will(returnValue(context.mock(Gradle.class, "rootBuild")));
            allowing(gradle).getRootProject();
            will(returnValue(rootProject));
            allowing(rootProject).getName();
            will(returnValue("build"));

            one(progressLoggerFactory).start(TaskExecutionLogger.class.getName(), ":build:path");
            will(returnValue(progressLogger));
            one(progressLogger).progress(":build:path");
        }});

        executionLogger.beforeExecute(task);

        context.checking(new Expectations() {{
            allowing(state).getSkipMessage();
            will(returnValue(null));
            one(progressLogger).completed();
        }});

        executionLogger.afterExecute(task, state);
    }

    @Test
    public void logsSkippedTaskExecution() {
        context.checking(new Expectations() {{
            allowing(gradle).getParent();
            will(returnValue(null));
            one(progressLoggerFactory).start(TaskExecutionLogger.class.getName(), ":path");
            will(returnValue(progressLogger));
            one(progressLogger).progress(":path");
        }});

        executionLogger.beforeExecute(task);

        context.checking(new Expectations() {{
            allowing(state).getSkipMessage();
            will(returnValue("skipped"));
            one(progressLogger).completed("skipped");
        }});

        executionLogger.afterExecute(task, state);
    }
}
