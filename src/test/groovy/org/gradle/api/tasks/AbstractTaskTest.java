/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.gradle.api.*;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.execution.Dag;
import org.gradle.test.util.Check;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Hans Dockter
 */
public abstract class AbstractTaskTest {
    public static final String TEST_TASK_NAME = "taskname";

    public static final String TEST_PROJECT_NAME = "/projectTestName";

    private AbstractProject project;

    private Dag tasksGraph;


    @Before
    public void setUp() {
        tasksGraph = new Dag();
        project = HelperUtil.createRootProject(new File(HelperUtil.TMP_DIR_FOR_TEST).getAbsoluteFile());
    }

    public abstract AbstractTask getTask();

    public Task createTask(Project project, String name) {
        try {
            Constructor constructor = getTask().getClass().getDeclaredConstructor(Project.class, String.class, Dag.class);
            return (Task) constructor.newInstance(project, name, tasksGraph);
        } catch (Exception e) {
            throw new GradleException("Task creation error.", e);
        }
    }

    @Test
    public void testTask() {
        assertTrue(getTask().isEnabled());
        assertEquals(TEST_TASK_NAME, getTask().getName());
        assertSame(project, getTask().getProject());
        assertSame(tasksGraph, getTask().getTasksGraph());
        assertNotNull(getTask().getSkipProperties());
    }

    // We do it in an own method, so that its easy to overwrite the test for getTask() which deviate from default.
    @Test
    public void testDagNeutral() {
        assertFalse(getTask().isDagNeutral());
    }

    @Test
    public void testPath() {
        DefaultProject rootProject = HelperUtil.createRootProject(new File("parent", "root"));
        DefaultProject childProject = HelperUtil.createChildProject(rootProject, "child");
        DefaultProject childchildProject = HelperUtil.createChildProject(childProject, "childchild");

        Task task = createTask(rootProject, TEST_TASK_NAME);
        assertEquals(Project.PATH_SEPARATOR + TEST_TASK_NAME, task.getPath());
        task = createTask(childProject, TEST_TASK_NAME);
        assertEquals(Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.getPath());
        task = createTask(childchildProject, TEST_TASK_NAME);
        assertEquals(Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + "childchild" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.getPath());
    }

    @Test
    public void testDependsOn() {
        Task dependsOnTask = createTask(project, "somename");
        Task task = createTask(project, TEST_TASK_NAME);
        task.dependsOn(Project.PATH_SEPARATOR + "path1");
        assertEquals(WrapUtil.toSet(Project.PATH_SEPARATOR + "path1"), task.getDependsOn());
        task.dependsOn("path2", dependsOnTask);
        assertEquals(WrapUtil.toSet(Project.PATH_SEPARATOR + "path1", "path2", dependsOnTask), task.getDependsOn());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testDependsOnWithEmptySecondArgument() {
        getTask().dependsOn("path1", "");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testDependsOnWithEmptyFirstArgument() {
        getTask().dependsOn("", "path1");
    }

    @Test(expected = InvalidUserDataException.class)
    public void testDependsOnWithNullFirstArgument() {
        getTask().dependsOn(null, "path1");
    }

    @Test
    public void testToString() {
        assertEquals(getTask().getPath(), getTask().toString());
    }

    @Test
    public void testDoFirst() {
        TaskAction action1 = Check.createTaskAction();
        TaskAction action2 = Check.createTaskAction();
        int actionSizeBefore = getTask().getActions().size();
        assertSame(getTask(), getTask().doFirst(action2));
        assertEquals(actionSizeBefore + 1, getTask().getActions().size());
        assertEquals(action2, getTask().getActions().get(0));
        assertSame(getTask(), getTask().doFirst(action1));
        assertEquals(action1, getTask().getActions().get(0));
    }

    @Test
    public void testDoLast() {
        TaskAction action1 = Check.createTaskAction();
        TaskAction action2 = Check.createTaskAction();
        int actionSizeBefore = getTask().getActions().size();
        assertSame(getTask(), getTask().doLast(action1));
        assertEquals(actionSizeBefore + 1, getTask().getActions().size());
        assertEquals(action1, getTask().getActions().get(getTask().getActions().size() - 1));
        assertSame(getTask(), getTask().doLast(action2));
        assertEquals(action2, getTask().getActions().get(getTask().getActions().size() - 1));
    }

    @Test
    public void testDeleteAllActions() {
        TaskAction action1 = Check.createTaskAction();
        TaskAction action2 = Check.createTaskAction();
        getTask().doLast(action1);
        getTask().doLast(action2);
        assertSame(getTask(), getTask().deleteAllActions());
        assertEquals(new ArrayList(), getTask().getActions());
    }

    @Test(expected = InvalidUserDataException.class)
    public void testAddActionWithNull() {
        getTask().doLast((Closure) null);
    }

    @Test
    public void testAddActionsWithClosures() {
        GroovyTaskTestHelper.checkAddActionsWithClosures(getTask());
    }


    @Test
    public void testBasicExecute() {
        getTask().setActions(new ArrayList());
        assertFalse(getTask().isExecuted());
        final List<Boolean> actionsCalled = WrapUtil.toList(false, false);
        TaskAction action1 = new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                actionsCalled.set(0, true);
            }
        };
        TaskAction action2 = new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                actionsCalled.set(1, true);
            }
        };
        getTask().doLast(action1);
        getTask().doLast(action2);
        getTask().execute();
        assertTrue(getTask().isExecuted());
        assertTrue(actionsCalled.get(0));
        assertTrue(actionsCalled.get(1));
    }

    @Test
    public void testConfigure() {
        getTask().setActions(new ArrayList());
        GroovyTaskTestHelper.checkConfigure(getTask());
    }

    @Test
    public void testActionWithThrowable() {
        getTask().doFirst(createExceptionAction(new RuntimeException()));
        checkException(GradleScriptException.class);
    }

    @Test
    public void testActionWithGradleEception() {
        getTask().doFirst(createExceptionAction(new GradleException("x")));
        checkException(GradleException.class);
    }

    @Test
    public void testActionWithInvokerInvocationExceptionAndWrappedThrowable() {
        getTask().doFirst(createExceptionAction(new InvokerInvocationException(new RuntimeException("x"))));
        checkException(GradleScriptException.class);
    }

    @Test
    public void testActionWithInvokerInvocationExceptionAndWrappedGradleException() {
        getTask().doFirst(createExceptionAction(new InvokerInvocationException(new GradleException("x"))));
        checkException(GradleException.class);
    }

    private void checkException(Class exceptionType) {
        try {
            getTask().execute();
            fail();
        } catch (Exception e) {
            assertEquals(exceptionType, e.getClass());
            GradleException gradleException = (GradleException) e;
//            assertEquals(getProject().getBuildFileClassName(), gradleException.getScriptName());
        }
    }

    @Test
    public void testStopExecution() {
        checkStopExecution(new StopExecutionException());
    }

    @Test
    public void testStopExecutionWrappedInInvokerInvocationException() {
        checkStopExecution(new InvokerInvocationException(new StopExecutionException()));
    }

    private void checkStopExecution(RuntimeException actionException) {
        final List<Boolean> actionsCalled = WrapUtil.toList(false, false);
        TaskAction action2 = new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                actionsCalled.set(1, true);
            }
        };
        getTask().doFirst(action2);
        getTask().doFirst(createExceptionAction(actionException));
        getTask().execute();
        assertFalse(actionsCalled.get(1));
        assertTrue(getTask().isExecuted());
    }

    @Test
    public void testStopAction() {
        checkStopAction(new StopActionException());
    }

    @Test
    public void testStopActionWrappedInInvokerInvocationException() {
        checkStopAction(new InvokerInvocationException(new StopActionException()));
    }

    private void checkStopAction(RuntimeException actionException) {
        getTask().setActions(new ArrayList());
        final List<Boolean> actionsCalled = WrapUtil.toList(false, false);
        TaskAction action2 = new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                actionsCalled.set(1, true);
            }
        };
        getTask().doFirst(action2);
        getTask().doFirst(createExceptionAction(actionException));
        getTask().execute();
        assertTrue(actionsCalled.get(1));
        assertTrue(getTask().isExecuted());
    }

    private TaskAction createExceptionAction(final RuntimeException e) {
        return new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                throw e;
            }
        };
    }

    @Test
    public void testDisabled() {
        getTask().setActions(new ArrayList());
        TaskAction action1 = new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                fail();
            }
        };
        getTask().doFirst(action1);
        getTask().setEnabled(false);
        getTask().execute();
        assert getTask().isExecuted();
    }

    @Test
    public void testSkipProperties() {
        getTask().setActions(new ArrayList());
        getTask().setSkipProperties(WrapUtil.toList("prop1"));
        final List<Boolean> actionsCalled = WrapUtil.toList(false);
        TaskAction action1 = new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                actionsCalled.set(0, true);
            }
        };
        getTask().doFirst(action1);
        System.setProperty(getTask().getSkipProperties().get(0), "true");
        getTask().execute();
        assertFalse(actionsCalled.get(0));
        assertTrue(getTask().isExecuted());

        System.setProperty(getTask().getSkipProperties().get(0), "");
        getTask().setExecuted(false);
        getTask().execute();
        assertFalse(actionsCalled.get(0));
        assertTrue(getTask().isExecuted());

        System.setProperty(getTask().getSkipProperties().get(0), "false");
        getTask().setExecuted(false);
        getTask().execute();
        assertTrue(actionsCalled.get(0));
        assertTrue(getTask().isExecuted());
        System.getProperties().remove(getTask().getSkipProperties().get(0));
    }

    @Test
    public void testAutoSkipProperties() {
        getTask().setActions(new ArrayList());
        final List<Boolean> actionsCalled = WrapUtil.toList(false);
        TaskAction action1 = new TaskAction() {
            public void execute(Task task, Dag tasksGraph) {
                actionsCalled.set(0, true);
            }
        };
        getTask().doFirst(action1);

        System.setProperty("skip." + getTask().getName(), "true");
        getTask().execute();
        assertFalse(actionsCalled.get(0));
        assertTrue(getTask().isExecuted());

        System.setProperty("skip." + getTask().getName(), "false");
        getTask().setExecuted(false);
        getTask().execute();
        assertTrue(actionsCalled.get(0));
        assertTrue(getTask().isExecuted());
        System.getProperties().remove("skip." + getTask().getName());
    }

//    private void checkConfigureEvent(Closure addMethod, Closure applyMethod) {
//        TaskAction action1 = {} as TaskAction
//        TaskAction action2 = {} as TaskAction
//        assert addMethod {
//            doFirst(action2)
//        }.is(task)
//        addMethod {
//            doFirst(action1)
//        }
//        assert !task.actions[0].is(action1)
//        assert applyMethod().is(task)
//        assert task.actions[0].is(action1)
//        assert task.actions[1].is(action2)
//    }

    public AbstractProject getProject() {
        return project;
    }

    public void setProject(AbstractProject project) {
        this.project = project;
    }

    public Dag getTasksGraph() {
        return tasksGraph;
    }

    public void setTasksGraph(Dag tasksGraph) {
        this.tasksGraph = tasksGraph;
    }
}
