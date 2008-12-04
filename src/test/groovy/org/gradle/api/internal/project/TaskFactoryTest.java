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
import org.gradle.util.TestTask;
import org.gradle.util.WrapUtil;
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
    public static final String TEST_TASK_NAME = "sometask";

    private Map empyArgMap;

    private TaskFactory taskFactory;

    private DefaultProject testProject;

    private TaskAction testAction;

    private Map tasks;

    @Before
    public void setUp() {
        taskFactory = new TaskFactory();
        testProject = new DefaultProject("projectName");
        testAction = new TaskAction() {
            public void execute(Task task) {
                ;
            }
        };
        empyArgMap = new HashMap();
        tasks = new HashMap();
    }

    @Test
    public void testCreateTask() {
        assertEquals(0, checkTask(taskFactory.createTask(testProject, tasks, empyArgMap, TEST_TASK_NAME))
                .getActions().size());
    }

    @Test
    public void testCreateTaskWithDependencies() {
        List testDependsOn = WrapUtil.toList("/path1");
        Task task = checkTask(taskFactory.createTask(testProject, tasks, WrapUtil.toMap("dependsOn", testDependsOn),
                TEST_TASK_NAME));
        assertEquals(new HashSet(testDependsOn), task.getDependsOn());
        assert task.getActions().size() == 0;
    }

    @Test
    public void testCreateTaskWithSingleDependency() {
        String testDependsOn = "/path1";
        Task task = checkTask(taskFactory.createTask(testProject, tasks, WrapUtil.toMap("dependsOn", testDependsOn),
                TEST_TASK_NAME));
        assertEquals(WrapUtil.toSet(testDependsOn), task.getDependsOn());
        assert task.getActions().size() == 0;
    }

    @Test
    public void testCreateDefaultTaskWithSameNameAsExistingTask() {
        tasks.put("name", new DefaultTask(testProject, "name"));
        try {
            taskFactory.createTask(testProject, tasks, empyArgMap, "name");
            fail();
        } catch (InvalidUserDataException e) {
            assertEquals("Cannot create task with name 'name' as a task with that name already exists.",
                    e.getMessage());
        }
    }

    @Test
    public void testCreateDefaultTaskWithSameNameAsExistingTaskAndOverwriteTrue() {
        Task oldTask = new DefaultTask(testProject, TEST_TASK_NAME);
        tasks.put(TEST_TASK_NAME, oldTask);
        Task task = taskFactory.createTask(testProject, tasks, WrapUtil.toMap("overwrite", true), TEST_TASK_NAME);
        assertNotSame(oldTask, task);
    }

    @Test
    public void testCreateTaskWithNonDefaultType() {
        Task task = checkTask(taskFactory.createTask(testProject, tasks, WrapUtil.toMap(Task.TASK_TYPE, TestTask.class),
                TEST_TASK_NAME));
        assertEquals(TestTask.class, task.getClass());
    }

    @Test
    public void testCreateTaskForTypeWithMissingConstructor() {
        try {
            taskFactory.createTask(testProject, tasks, WrapUtil.toMap(Task.TASK_TYPE, MissingConstructorTask.class),
                    "name");
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
            taskFactory.createTask(testProject, tasks, WrapUtil.toMap(Task.TASK_TYPE, NotATask.class), "name");
            fail();
        } catch (GradleException e) {
            assertEquals("Cannot create task of type 'NotATask' as it does not implement the Task interface.",
                    e.getMessage());
        }
    }

    @Test
    public void testCreateTaskWhenConstructorThrowsException() {
        try {
            taskFactory.createTask(testProject, tasks, WrapUtil.toMap(Task.TASK_TYPE, CannotConstructTask.class),
                    "name");
            fail();
        } catch (GradleException e) {
            assertEquals("Could not create task of type 'CannotConstructTask'.", e.getMessage());
            assertTrue(RuntimeException.class.isInstance(e.getCause()));
            assertEquals("fail", e.getCause().getMessage());
        }
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
