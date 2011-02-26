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
package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.*;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.plugins.Convention;
import org.gradle.api.tasks.ConventionValue;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.util.GUtil;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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
        taskFactory = new TaskFactory(new AsmBackedClassGenerator());
        testProject = HelperUtil.createRootProject();
        empyArgMap = new HashMap();
    }

    @Test
    public void testCreateTask() {
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task")));
        assertThat(task, instanceOf(DefaultTask.class));
        assertThat(task.getProject(), sameInstance((Project) testProject));
        assertThat(task.getName(), equalTo("task"));
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
        Set expected = WrapUtil.toSet((Object) testDependsOn);
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_DEPENDS_ON, testDependsOn)));
        assertEquals(expected, task.getDependsOn());
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
    public void testCreateTaskOfTypeWithNoArgsConstructor() {
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, TestDefaultTask.class)));
        assertThat(task.getProject(), sameInstance((Project) testProject));
        assertThat(task.getName(), equalTo("task"));
        assertTrue(TestDefaultTask.class.isAssignableFrom(task.getClass()));
    }

    @Test
    public void testAppliesConventionMappingToEachGetter() {
        TestConventionTask task = (TestConventionTask) checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, TestConventionTask.class)));

        assertThat(task.getProperty(), nullValue());

        task.getConventionMapping().map("property", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                return "conventionValue";
            }
        });

        assertThat(task.getProperty(), equalTo("conventionValue"));

        task.setProperty("value");
        assertThat(task.getProperty(), equalTo("value"));
    }

    @Test
    public void doesNotApplyConventionMappingToGettersDefinedByTaskInterface() {
        TestConventionTask task = (TestConventionTask) checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, TestConventionTask.class)));
        task.getConventionMapping().map("description", new ConventionValue() {
            public Object getValue(Convention convention, IConventionAware conventionAwareObject) {
                throw new UnsupportedOperationException();
            }
        });
        assertThat(task.getDescription(), nullValue());
    }

    @Test
    public void testCreateTaskWithAction() {
        Action<Task> action = new Action<Task>() {
            public void execute(Task task) {
            }
        };

        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_ACTION, action)));
        assertThat((List)task.getActions(), equalTo((List) WrapUtil.toList(action)));
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
        } catch (InvalidUserDataException e) {
            assertEquals(
                    "Cannot create task of type 'MissingConstructorTask' as it does not have a public no-args constructor.",
                    e.getMessage());
        }
    }

    @Test
    public void testCreateTaskForTypeWhichDoesNotImplementTask() {
        try {
            taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, NotATask.class));
            fail();
        } catch (InvalidUserDataException e) {
            assertEquals("Cannot create task of type 'NotATask' as it does not implement the Task interface.",
                    e.getMessage());
        }
    }

    @Test
    public void testCreateTaskWhenConstructorThrowsException() {
        try {
            taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_TYPE, CannotConstructTask.class));
            fail();
        } catch (TaskInstantiationException e) {
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

    @Test
    public void createTaskWithGroup() {
        Object testGroup = "The Group";
        Task task = checkTask(taskFactory.createTask(testProject, GUtil.map(Task.TASK_NAME, "task", Task.TASK_GROUP, testGroup)));
        assertEquals(testGroup, task.getGroup());
    }

    private Task checkTask(Task task) {
        assertEquals(TEST_TASK_NAME, task.getName());
        assertSame(testProject, task.getProject());
        return task;
    }

    public static class TestDefaultTask extends DefaultTask {
    }

    public static class TestConventionTask extends ConventionTask {
        private String property;

        public String getProperty() {
            return property;
        }

        public void setProperty(String property) {
            this.property = property;
        }
    }

    public static class MissingConstructorTask extends DefaultTask {
        public MissingConstructorTask(Integer something) {
        }
    }

    public static class NotATask {
    }

    public static class CannotConstructTask extends DefaultTask {
        public CannotConstructTask() {
            throw new RuntimeException("fail");
        }
    }
}
