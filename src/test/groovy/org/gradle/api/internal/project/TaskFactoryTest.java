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
package org.gradle.api.internal.project;

import org.gradle.api.*;
import org.gradle.api.internal.DefaultTask;
import org.gradle.util.GUtil;
import org.gradle.util.TestTask;
import org.gradle.util.WrapUtil;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;


/**
 * @author Hans Dockter
 */
public class TaskFactoryTest {
    public static final String TEST_TASK_NAME = "task";

    private Map empyArgMap;

    private TaskFactory taskFactory;

    private DefaultProject testProject;

    @Before
    public void setUp() {
        taskFactory = new TaskFactory();
        testProject = new DefaultProject("projectName");
        empyArgMap = new HashMap();
    }

    @Test
    public void testCreateTask() {
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task")));
        assertTrue(task.getActions().isEmpty());
    }

    @Test
    public void testCannotCreateTaskWithNoName() {
        try {
            taskFactory.createTask(testProject, empyArgMap);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("The task name must be provided."));
        }
    }

    @Test
    public void testCreateTaskWithDependencies() {
        List testDependsOn = WrapUtil.toList("/path1");
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_DEPENDS_ON, testDependsOn)));
        assertEquals(new HashSet(testDependsOn), task.getDependsOn());
        assertTrue(task.getActions().isEmpty());
    }

    @Test
    public void testCreateTaskWithSingleDependency() {
        String testDependsOn = "/path1";
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_DEPENDS_ON, testDependsOn)));
        assertEquals(WrapUtil.toSet(testDependsOn), task.getDependsOn());
        assertTrue(task.getActions().isEmpty());
    }

    @Test
    public void testCreateTaskWithNonDefaultType() {
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, TestTask.class)));
        assertEquals(TestTask.class, task.getClass());
    }

    @Test
    public void testCreateTaskWithAction() {
        TaskAction action = new TaskAction() {
            public void execute(Task task) {
            }
        };

        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_ACTION, action)));
        assertThat(task.getActions(), equalTo(WrapUtil.toList(action)));
    }

    @Test
    public void testCreateTaskWithActionClosure() {
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_ACTION, HelperUtil.TEST_CLOSURE)));
        assertFalse(task.getActions().isEmpty());
    }

    @Test
    public void testCreateTaskForTypeWithMissingConstructor() {
        try {
            taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, MissingConstructorTask.class));
            fail();
        } catch (GradleException e) {
            assertEquals(
                    "Cannot create task of type 'MissingConstructorTask' as it does not have an appropriate public constructor.",
                    e.getMessage());
        }
    }

    @Test
    public void testCreateTaskForTypeWhichDoesNotImplementTask() {
        try {
            taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, NotATask.class));
            fail();
        } catch (GradleException e) {
            assertEquals("Cannot create task of type 'NotATask' as it does not implement the Task interface.",
                    e.getMessage());
        }
    }

    @Test
    public void testCreateTaskWhenConstructorThrowsException() {
        try {
            taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, CannotConstructTask.class));
            fail();
        } catch (GradleException e) {
            assertEquals("Could not create task of type 'CannotConstructTask'.", e.getMessage());
            assertTrue(RuntimeException.class.isInstance(e.getCause()));
            assertEquals("fail", e.getCause().getMessage());
        }
    }

    @Test
    public void createTaskWithDescription() {
        Object testDescription = 9;
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_DESCRIPTION, testDescription)));
        assertEquals("9", task.getDescription());
    }

    private Task checkTask(Task task) {
        assertEquals(TEST_TASK_NAME, task.getName());
        assertSame(testProject, task.getProject());
        return task;
    }

    public static class MissingConstructorTask extends DefaultTask {
    }

    public static class NotATask {
    }

    public static class CannotConstructTask extends DefaultTask {
        public CannotConstructTask(Project project, String name) {
            super(project, name);
            throw new RuntimeException("fail");
        }
    }
}
