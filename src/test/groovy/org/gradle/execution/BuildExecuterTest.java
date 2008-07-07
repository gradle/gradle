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

package org.gradle.execution;

import org.gradle.api.*;
import org.gradle.api.internal.DefaultTask;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.GroovyJavaHelper;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.*;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class BuildExecuterTest {
    static File TEST_ROOT_DIR = new File("/path/root");

    BuildExecuter buildExecuter;
    DefaultProject root;
    Project child;

    JUnit4Mockery context = new JUnit4Mockery();

    Dag dagMock;

    CollectDagTasksAction collectDagTasksAction;

    @Before
    public void setUp() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        dagMock = context.mock(Dag.class);
        root = HelperUtil.createRootProject(new File("root"));
        child = root.addChildProject("child");
        buildExecuter = new BuildExecuter(dagMock);
        collectDagTasksAction = new CollectDagTasksAction();
    }

    @Test
    public void testExecute() {
        String expectedTaskName = "test";
        boolean expectedRecursive = true;

        final Task rootCompile = new DefaultTask(root, "compile");
        final Task rootTest = new DefaultTask(root, "test");
        final Task childCompile = new DefaultTask(child, "compile");
        final Task childTest = new DefaultTask(child, "test");
        final Task childOther = new DefaultTask(child, "other");
        rootTest.setDependsOn(WrapUtil.toSet(rootCompile.getPath()));
        childTest.setDependsOn(WrapUtil.toSet(childCompile.getName(), childOther));
        final Map dagCalls = new HashMap();

        rootCompile.afterDag(GroovyJavaHelper.createExecuteClosure(new Object() {
            public void execute() {
                dagCalls.put("root", new Object());
            }
        }));
        childCompile.afterDag(GroovyJavaHelper.createExecuteClosure(new Object() {
            public void execute() {
                dagCalls.put("child", new Object());
            }
        }));
        root.setConfigureByDag(new DagAction() {
            public void execute(Dag dag) {
                dagCalls.put("rootConfigure", new Object());
            }
        });

        root.getTasks().put(rootCompile.getName(), rootCompile);
        root.getTasks().put(rootTest.getName(), rootTest);
        child.getTasks().put(childCompile.getName(), childCompile);
        child.getTasks().put(childTest.getName(), childTest);
        child.getTasks().put(childOther.getName(), childOther);

        setDagMockExpectations(WrapUtil.toSet(root, child), WrapUtil.toSet(rootCompile, rootTest, childCompile, childTest, childOther));

        buildExecuter.execute(expectedTaskName, expectedRecursive, root, root);

        assertEquals(collectDagTasksAction.tasksMap.size(), 5);
        assertEquals(WrapUtil.toSet(rootCompile), collectDagTasksAction.tasksMap.get(rootTest));
        assertEquals(WrapUtil.toSet(childCompile, childOther), collectDagTasksAction.tasksMap.get(childTest));
        assertEquals(new HashSet(), collectDagTasksAction.tasksMap.get(rootCompile));
        assertEquals(new HashSet(), collectDagTasksAction.tasksMap.get(childCompile));
        assert dagCalls.keySet().contains("root");
        assert dagCalls.keySet().contains("child");
        assert dagCalls.keySet().contains("rootConfigure");
    }

    public static class CollectDagTasksAction<T> implements Action {
        Map<DefaultTask, Set<DefaultTask>> tasksMap = new HashMap<DefaultTask, Set<DefaultTask>>();

        public CollectDagTasksAction() {
        }

        public void describeTo(Description description) {
            description.appendText("adds ");
        }

        public Object invoke(Invocation invocation) throws Throwable {
            DefaultTask task = (DefaultTask) invocation.getParameter(0);
            Set<DefaultTask> dependsOnTasks = (Set<DefaultTask>) invocation.getParameter(1);
            tasksMap.put(task, dependsOnTasks);
            return null;
        }
    }

    private void setDagMockExpectations(final Set<Project> projects, final Set<Task> tasks) {
        context.checking(new Expectations() {
            {
                one(dagMock).reset();
                allowing(dagMock).addTask(with(any(DefaultTask.class)), with(any(Set.class)));
                will(collectDagTasksAction);
                allowing(dagMock).getProjects();
                will(returnValue(projects));
                allowing(dagMock).getAllTasks();
                will(returnValue(tasks));
                one(dagMock).execute();
            }
        });
    }

    @Test
    public void testUnknownTasks() {
        DefaultTask rootCompile = new DefaultTask(root, "compile");
        DefaultTask rootTest = new DefaultTask(root, "test");
        DefaultTask childCompile = new DefaultTask(child, "compile");
        DefaultTask childOtherTask = new DefaultTask(child, "other");

        root.getTasks().put(rootCompile.getName(), rootCompile);
        root.getTasks().put(rootTest.getName(), rootTest);
        child.getTasks().put(childCompile.getName(), childCompile);
        child.getTasks().put(childOtherTask.getName(), childOtherTask);

        assertEquals(new ArrayList(), buildExecuter.unknownTasks(WrapUtil.toList("compile", "test"), false, root));
        assertEquals(WrapUtil.toList("test"), buildExecuter.unknownTasks(WrapUtil.toList("compile", "test"), true, child));
        assertEquals(new ArrayList(), buildExecuter.unknownTasks(WrapUtil.toList("compile", "other"), true, root));
        assertEquals(WrapUtil.toList("other"), buildExecuter.unknownTasks(WrapUtil.toList("compile", "other"), false, root));
    }

    @Test
    public void testExecuteWithTransitiveTargetDependecies() {
        Task task1 = new DefaultTask(root, "task1");
        Task task2 = new DefaultTask(root, "task2").dependsOn(new String[]{"task1"});
        Task task3 = new DefaultTask(root, "task3").dependsOn(new String[]{"task2"});
        root.getTasks().put("task1", task1);
        root.getTasks().put("task2", task2);
        root.getTasks().put("task3", task3);
        setDagMockExpectations(WrapUtil.toSet(root, child),
                WrapUtil.toSet(task1, task2, task3));

        buildExecuter.execute("task3", false, root, root);

        assertEquals(WrapUtil.toSet(task2), collectDagTasksAction.tasksMap.get(task3));
        assertEquals(WrapUtil.toSet(task1), collectDagTasksAction.tasksMap.get(task2));
        assertEquals(new HashSet(), collectDagTasksAction.tasksMap.get(task1));

    }

    @Test(expected = UnknownTaskException.class)
    public void testExecuteWithNonExistingProjectForDependentTask() {
        final Project root = context.mock(Project.class);
        final Task task = context.mock(Task.class);
        final Set result = new HashSet();
        final String taskName = ":unknownchild:compile";

        context.checking(new Expectations() {{
            allowing(root).getTasksByName("compile", false); will(returnValue(result));
            allowing(root).getRootProject(); will(returnValue(root));
            allowing(root).getPath(); will(returnValue("root"));
            allowing(root).absolutePath(taskName); will(returnValue(taskName));
            allowing(root).project(":unknownchild"); will(throwException(new UnknownProjectException()));
            allowing(dagMock).reset();
        }});
        result.add(new DefaultTask(root, "compile").dependsOn(new String[]{taskName}));
        buildExecuter.execute("compile", false, root, root);
    }

    @Test(expected = UnknownTaskException.class)
    public void testExecuteWithNonExistingDependentTask() {
        final Project root = context.mock(Project.class);
        final Task task = context.mock(Task.class);
        final Set result = new HashSet();
        final String taskName = ":child:compile";
        context.checking(new Expectations() {{
            allowing(root).getTasksByName("compile", false); will(returnValue(result));
            allowing(root).getRootProject(); will(returnValue(root));
            allowing(root).getPath(); will(returnValue("root"));
            allowing(root).absolutePath(taskName); will(returnValue(taskName));
            allowing(dagMock).reset();
        }});
        result.add(new DefaultTask(root, "compile").dependsOn(new String[]{taskName}));
        buildExecuter.execute("compile", false, child, root);
    }

    @Test(expected = UnknownTaskException.class)
    public void testExecuteWithNonExistingTask() {
        final Project root = context.mock(Project.class);
        context.checking(new Expectations() {{
            allowing(root).getTasksByName("compil", true); will(returnValue(new HashSet()));
        }});
            buildExecuter.execute("compil", true, root, root);
    }

}
