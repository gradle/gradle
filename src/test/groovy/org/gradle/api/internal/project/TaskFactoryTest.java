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

import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import org.gradle.util.TestTask;
import org.gradle.util.WrapUtil;
import org.gradle.api.internal.DefaultTask;
import org.gradle.api.TaskAction;
import org.gradle.api.Task;
import org.gradle.api.InvalidUserDataException;

import java.util.*;


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

    @Before public void setUp() {
        taskFactory = new TaskFactory();
        testProject = new DefaultProject();
        testAction = new TaskAction() {
            public void execute(Task task) {
                ;
            }
        };
        empyArgMap = new HashMap();
        tasks = new HashMap();
    }

    @Test public void testCreateTask() {
        assertEquals(0, checkTask(taskFactory.addTask(testProject, tasks, empyArgMap, TEST_TASK_NAME, null)).getActions().size());
    }

    @Test public void testCreateTaskWithActions() {
        assertEquals(WrapUtil.toList(testAction), checkTask(taskFactory.addTask(testProject, tasks, empyArgMap, TEST_TASK_NAME, testAction)).getActions());
    }

    @Test public void testCreateTaskWithDependencies() {
        List testDependsOn = WrapUtil.toList("/path1");
        Task task = checkTask(taskFactory.addTask(testProject, tasks, WrapUtil.toMap("dependsOn", testDependsOn), TEST_TASK_NAME, null));
        assertEquals(new HashSet(testDependsOn),task.getDependsOn());
        assert task.getActions().size() == 0;
    }

    @Test public void testCreateTaskWithSingleDependency() {
        String testDependsOn = "/path1";
        Task task = checkTask(taskFactory.addTask(testProject, tasks, WrapUtil.toMap("dependsOn", testDependsOn), TEST_TASK_NAME, null));
        assertEquals(WrapUtil.toSet(testDependsOn), task.getDependsOn());
        assert task.getActions().size() == 0;
    }

    @Test public void testCreateTaskWithActionAndDependencies() {
        List testDependsOn = WrapUtil.toList("/path1", "path2", "path2/path3");
        Task task = checkTask(taskFactory.addTask(testProject, tasks, WrapUtil.toMap("dependsOn", testDependsOn), TEST_TASK_NAME, testAction));
        assertEquals(new HashSet(testDependsOn), task.getDependsOn());
        assertEquals(WrapUtil.toList(testAction), task.getActions());
    }

    @Test (expected = InvalidUserDataException.class) public void testCreateDefaultTaskWithSameNameAsExistingTask() {
        tasks.put(TEST_TASK_NAME, new DefaultTask());
        taskFactory.addTask(testProject, tasks, empyArgMap, TEST_TASK_NAME, null);
    }

    @Test public void testCreateDefaultTaskWithSameNameAsExistingTaskAndOverwriteTrue() {
        Task oldTask = new DefaultTask();
        tasks.put(TEST_TASK_NAME, oldTask);
        Task task = taskFactory.addTask(testProject, tasks, WrapUtil.toMap("overwrite", true), TEST_TASK_NAME, null);
        Assert.assertNotSame(oldTask, task);
    }

    @Test public void testCreateTaskWithNonDefaultType() {
        Task task = checkTask(taskFactory.addTask(testProject, tasks, WrapUtil.toMap(Task.TASK_TYPE, TestTask.class), TEST_TASK_NAME, null));
        assertEquals(TestTask.class, task.getClass());
    }

    private Task checkTask(Task task) {
        assertEquals(TEST_TASK_NAME, task.getName());
        assertSame(testProject, task.getProject());
        return task;
    }

}
