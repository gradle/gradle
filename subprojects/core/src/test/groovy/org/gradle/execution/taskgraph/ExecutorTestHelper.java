/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskDependency;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;

import java.util.List;

import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.notNullValue;

class ExecutorTestHelper {

    private final JUnit4Mockery context;
    private final ProjectInternal root;
    private final List<Task> executedTasks;

    ExecutorTestHelper(JUnit4Mockery context, ProjectInternal root, List<Task> executedTasks) {
        this.context = context;
        this.root = root;
        this.executedTasks = executedTasks;
    }

    void dependsOn(final Task task, final Task... dependsOn) {
        context.checking(new Expectations() {{
            TaskDependency taskDependency = context.mock(TaskDependency.class);
            allowing(task).getTaskDependencies();
            will(returnValue(taskDependency));
            allowing(taskDependency).getDependencies(task);
            will(returnValue(toSet(dependsOn)));
        }});
    }

    Task brokenTask(String name, final RuntimeException failure, final Task... dependsOn) {
        final TaskInternal task = createTask(name);
        dependsOn(task, dependsOn);
        context.checking(new Expectations() {{
            atMost(1).of(task).executeWithoutThrowingTaskFailure();
            will(new ExecuteTaskAction(task));
            allowing(task.getState()).getFailure();
            will(returnValue(failure));
            allowing(task.getState()).rethrowFailure();
            will(throwException(failure));
        }});
        return task;
    }

    Task task(final String name, final Task... dependsOn) {
        final TaskInternal task = createTask(name);
        dependsOn(task, dependsOn);
        context.checking(new Expectations() {{
            atMost(1).of(task).executeWithoutThrowingTaskFailure();
            will(new ExecuteTaskAction(task));
            allowing(task.getState()).getFailure();
            will(returnValue(null));
        }});
        return task;
    }

    TaskInternal createTask(final String name) {
        final TaskInternal task = context.mock(TaskInternal.class);
        context.checking(new Expectations() {{
            TaskStateInternal state = context.mock(TaskStateInternal.class);

            allowing(task).getProject();
            will(returnValue(root));
            allowing(task).getName();
            will(returnValue(name));
            allowing(task).getPath();
            will(returnValue(":" + name));
            allowing(task).getState();
            will(returnValue(state));
            allowing(task).getMustRunAfter();
            will(returnValue(new DefaultTaskDependency()));
            allowing(task).getFinalizedBy();
            will(returnValue(new DefaultTaskDependency()));
            allowing(task).getDidWork();
            will(returnValue(true));
            allowing(task).compareTo(with(notNullValue(TaskInternal.class)));
            will(new org.jmock.api.Action() {
                public Object invoke(Invocation invocation) throws Throwable {
                    return name.compareTo(((Task) invocation.getParameter(0)).getName());
                }

                public void describeTo(Description description) {
                    description.appendText("compare to");
                }
            });
        }});

        return task;
    }

    private class ExecuteTaskAction implements org.jmock.api.Action {
        private final TaskInternal task;

        public ExecuteTaskAction(TaskInternal task) {
            this.task = task;
        }

        public Object invoke(Invocation invocation) throws Throwable {
            executedTasks.add(task);
            return null;
        }

        public void describeTo(Description description) {
            description.appendText("execute task");
        }
    }
}
