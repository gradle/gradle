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

import org.gradle.api.CircularReferenceException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.TaskAction;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.DefaultTask;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.util.HelperUtil;
import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
    List<Task> executedTasks = new ArrayList<Task>();

    @Before
    public void setUp() {
        root = HelperUtil.createRootProject(new File("root"));
        child = root.addChildProject("child", new File("childProjectDir"));
        buildExecuter = new BuildExecuter(new Dag<Task>());
    }

    @Test public void testExecutesTasksInDependencyOrder() {
        Task a = createTask("a");
        Task b = createTask("b", a);
        Task c = createTask("c", b, a);
        Task d = createTask("d", c);

        buildExecuter.execute(toList(d));

        assertThat(executedTasks, equalTo(toList(a, b, c, d)));
    }

    @Test public void testExecutesTasksWithNoDependenciesInNameOrder() {
        Task a = createTask("a");
        Task b = createTask("b");
        Task c = createTask("c");

        buildExecuter.execute(toList(b, c, a));

        assertThat(executedTasks, equalTo(toList(a, b, c)));
    }

    @Test public void testExecuteWithRebuildDagAndDagNeutralTask() {
        Task neutral = createTask("a");
        neutral.setDagNeutral(true);
        Task notNeutral = createTask("b");
        notNeutral.setDagNeutral(false);

        assertFalse(buildExecuter.execute(toList(neutral)));
        assertTrue(buildExecuter.execute(toList(notNeutral)));
    }

    @Test public void testAddTasksAddsDependencies() {
        Task a = createTask("a");
        Task b = createTask("b", a);
        Task c = createTask("c", b, a);
        Task d = createTask("d", c);
        buildExecuter.addTasks(toList(d));

        assertTrue(buildExecuter.hasTask(":a"));
        assertTrue(buildExecuter.hasTask(":b"));
        assertTrue(buildExecuter.hasTask(":c"));
        assertTrue(buildExecuter.hasTask(":d"));
        assertThat(buildExecuter.getAllTasks(), equalTo(toSet(a, b, c, d)));
    }

    @Test public void testDiscardsTasksAfterExecute() {
        Task a = createTask("a");
        Task b = createTask("b", a);

        buildExecuter.addTasks(toList(b));

        assertThat(buildExecuter.getAllTasks(), equalTo(toSet(a, b)));

        buildExecuter.execute();

        assertTrue(buildExecuter.getAllTasks().isEmpty());
    }
    
    @Test public void testCannotAddTaskWithCircularReference() {
        Task a = createTask("a");
        Task b = createTask("b", a);
        Task c = createTask("c", b);
        a.dependsOn(c);

        try {
            buildExecuter.addTasks(toList(c));
            fail();
        } catch (CircularReferenceException e) {
            // Expected
        }
    }

    @Test public void testNotifiesListenerBeforeExecute() {
        final TaskExecutionGraphListener listener = context.mock(TaskExecutionGraphListener.class);
        Task a = createTask("a");

        buildExecuter.addTaskExecutionGraphListener(listener);
        buildExecuter.addTasks(toList(a));

        context.checking(new Expectations(){{
            one(listener).graphPrepared(buildExecuter);
        }});

        buildExecuter.execute();
    }

    @Test public void testExecutesClosureBeforeExecute() {
        final Runnable runnable = context.mock(Runnable.class);
        Task a = createTask("a");

        buildExecuter.whenReady(BuildExecuterTestHelper.toClosure(runnable));

        buildExecuter.addTasks(toList(a));

        context.checking(new Expectations(){{
            one(runnable).run();
        }});

        buildExecuter.execute();
    }

    private Task createTask(String name, final Task... dependsOn) {
        final TaskInternal task = new DefaultTask(root, name);
        task.dependsOn((Object[]) dependsOn);
        task.setDagNeutral(true);
        task.doFirst(new TaskAction() {
            public void execute(Task task) {
                executedTasks.add(task);
            }
        });
        return task;
    }

}
