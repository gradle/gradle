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
import org.gradle.api.internal.project.AbstractProject;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.TaskFactory;
import org.gradle.test.util.Check;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.gradle.util.GUtil;
import org.gradle.api.logging.StandardOutputCapture;
import org.gradle.api.logging.DefaultStandardOutputCapture;
import org.gradle.api.logging.LogLevel;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;
import org.jmock.Sequence;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;

/**
 * @author Hans Dockter
 */
public abstract class AbstractTaskTest {
    public static final String TEST_TASK_NAME = "taskname";

    public static final String TEST_PROJECT_NAME = "/projectTestName";

    private AbstractProject project;

    private JUnit4Mockery context = new JUnit4Mockery();
    private static final TaskFactory taskFactory = new TaskFactory();

    @Before
    public void setUp() {
        project = HelperUtil.createRootProject(new File(HelperUtil.TMP_DIR_FOR_TEST).getAbsoluteFile());
    }

    public abstract AbstractTask getTask();

    public Task createTask(Project project, String name) {
        return createTask(getTask().getClass(), project, name);
    }

    public Task createTask(Class<? extends AbstractTask> type, Project project, String name) {
        Task task = taskFactory.createTask(project, GUtil.map(Task.TASK_TYPE, type, Task.TASK_NAME, name));
        assertTrue(type.isAssignableFrom(task.getClass()));
        return task;
    }

    @Test
    public void testTask() {
        assertTrue(getTask().isEnabled());
        assertEquals(TEST_TASK_NAME, getTask().getName());
        assertNull(getTask().getDescription());
        assertSame(project, getTask().getProject());
        assertNotNull(getTask().getSkipProperties());
        assertEquals(new DefaultStandardOutputCapture(true, LogLevel.QUIET), getTask().getStandardOutputCapture());
        assertEquals(new HashMap(), getTask().getAdditionalProperties());
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
        assertEquals("task '" + getTask().getPath() + "'", getTask().toString());
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
        final Sequence captureOutput = context.sequence("captureOutput");
        final StandardOutputCapture standardOutputCaptureMock = context.mock(StandardOutputCapture.class);
        getTask().setStandardOutputCapture(standardOutputCaptureMock);
        context.checking(new Expectations() {{
            one(standardOutputCaptureMock).start(); inSequence(captureOutput); will(returnValue(standardOutputCaptureMock));
            one(standardOutputCaptureMock).stop(); inSequence(captureOutput); will(returnValue(standardOutputCaptureMock));
        }});
        getTask().setActions(new ArrayList());
        assertFalse(getTask().isExecuted());
        final List<Boolean> actionsCalled = WrapUtil.toList(false, false);
        TaskAction action1 = new TaskAction() {
            public void execute(Task task) {
                actionsCalled.set(0, true);
            }
        };
        TaskAction action2 = new TaskAction() {
            public void execute(Task task) {
                actionsCalled.set(1, true);
            }
        };
        getTask().doLast(action1);
        getTask().doLast(action2);
        getTask().execute();
        assertTrue(getTask().isExecuted());
        assertTrue(actionsCalled.get(0));
        assertTrue(actionsCalled.get(1));
        context.assertIsSatisfied();
    }

    @Test
    public void testCaptureOutputWithException() {
        final Sequence captureOutput = context.sequence("captureOutput");
        final StandardOutputCapture standardOutputCaptureMock = context.mock(StandardOutputCapture.class);
        getTask().setStandardOutputCapture(standardOutputCaptureMock);
        context.checking(new Expectations() {{
            one(standardOutputCaptureMock).start(); inSequence(captureOutput); will(returnValue(standardOutputCaptureMock));
            one(standardOutputCaptureMock).stop(); inSequence(captureOutput); will(returnValue(standardOutputCaptureMock));
        }});
        getTask().setActions(new ArrayList());
        TaskAction action1 = new TaskAction() {
            public void execute(Task task) {
                throw new RuntimeException();
            }
        };
        getTask().doLast(action1);
        try {
            getTask().execute();
        } catch (Exception e) {
            // ignore
        }
        context.assertIsSatisfied();
    }

    @Test
    public void testConfigure() {
        getTask().setActions(new ArrayList());
        GroovyTaskTestHelper.checkConfigure(getTask());
    }

    @Test
    public void testActionWithThrowable() {
        RuntimeException failure = new RuntimeException();
        getTask().doFirst(createExceptionAction(failure));
        checkException(failure);
    }

    @Test
    public void testActionWithInvokerInvocationExceptionAndWrappedThrowable() {
        RuntimeException failure = new RuntimeException("x");
        getTask().doFirst(createExceptionAction(new InvokerInvocationException(failure)));
        checkException(failure);
    }

    @Test
    public void testActionWithInvokerInvocationExceptionAndNoWrappedThrowable() {
        InvokerInvocationException failure = new InvokerInvocationException(new Throwable()) {
            @Override
            public Throwable getCause() {
                return null;
            }
        };
        getTask().doFirst(createExceptionAction(failure));
        checkException(failure);
    }

    private void checkException(Throwable cause) {
        try {
            getTask().execute();
            fail();
        } catch (GradleScriptException e) {
            assertThat(e.getOriginalMessage(), equalTo("Execution failed for task '" + getTask().getPath() + "'."));
            assertThat(e.getScriptSource(), sameInstance(project.getBuildScriptSource()));
            assertThat(e.getCause(), sameInstance(cause));
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
            public void execute(Task task) {
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
            public void execute(Task task) {
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
            public void execute(Task task) {
                throw e;
            }
        };
    }

    @Test
    public void testDisabled() {
        getTask().setActions(new ArrayList());
        TaskAction action1 = new TaskAction() {
            public void execute(Task task) {
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
            public void execute(Task task) {
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
            public void execute(Task task) {
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

    public AbstractProject getProject() {
        return project;
    }

    public void setProject(AbstractProject project) {
        this.project = project;
    }

    @Test
    public void disableStandardOutCapture() {
        getTask().disableStandardOutputCapture();
        assertEquals(new DefaultStandardOutputCapture(), getTask().getStandardOutputCapture());
    }

    @Test
    public void captureStandardOut() {
        getTask().captureStandardOutput(LogLevel.DEBUG);
        assertEquals(new DefaultStandardOutputCapture(true, LogLevel.DEBUG), getTask().getStandardOutputCapture());
    }

    @Test(expected=GradleScriptException.class)
    public void disabledStandardOutCaptureDuringExecution() {
        ((AbstractTask)getTask().doFirst(new TaskAction() {
            public void execute(Task task) {
                task.disableStandardOutputCapture();
            }
        })).execute();
    }

    @Test(expected=GradleScriptException.class)
    public void captureStandardOutDuringExecution() {
        ((AbstractTask)getTask().doFirst(new TaskAction() {
            public void execute(Task task) {
                task.captureStandardOutput(LogLevel.DEBUG);
            }
        })).execute();
    }

    @Test
    public void setGetDescription() {
        String testDescription = "testDescription";
        getTask().setDescription(testDescription);
        assertEquals(testDescription, getTask().getDescription());
    }
}
