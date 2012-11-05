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

package org.gradle.api.internal

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.listener.ListenerManager
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.util.concurrent.Callable

import static org.gradle.util.Matchers.isEmpty
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class DefaultTaskTest extends AbstractTaskTest {
    ClassLoader cl
    DefaultTask defaultTask

    Object testCustomPropValue;

    @Before public void setUp() {
        testCustomPropValue = new Object()
        defaultTask = createTask(DefaultTask.class)
        cl = Thread.currentThread().contextClassLoader
    }

    @After public void cleanup() {
        Thread.currentThread().contextClassLoader = cl
    }

    AbstractTask getTask() {
        defaultTask
    }

    @Test public void testDefaultTask() {
        assertThat(defaultTask.dependsOn, isEmpty())
        assertEquals([], defaultTask.actions)
    }

    @Test public void testHasUsefulToString() {
        assertEquals('task \':taskname\'', task.toString())
    }

    @Test public void testCanInjectValuesIntoTaskWhenUsingNoArgsConstructor() {
        DefaultTask task = AbstractTask.injectIntoNewInstance(project, TEST_TASK_NAME, { new DefaultTask() } as Callable)
        assertThat(task.project, sameInstance(project))
        assertThat(task.name, equalTo(TEST_TASK_NAME))
    }

    @Test
    public void testConfigure() {
        Closure action1 = { Task t -> }
        assertSame(task, task.configure {
            doFirst(action1)
        });
        assertEquals(1, task.actions.size())
    }

    @Test
    public void testDoFirstAddsActionToTheStartOfActionsList() {
        Action<Task> action1 = Actions.doNothing();
        Action<Task> action2 = Actions.doNothing();

        assertSame(defaultTask, defaultTask.doFirst(action1));
        assertEquals(1, defaultTask.actions.size());

        assertSame(defaultTask, defaultTask.doFirst(action2));
        assertEquals(2, defaultTask.actions.size());

        assertSame(action2, defaultTask.actions[0].action)
        assertSame(action1, defaultTask.actions[1].action)
    }

    @Test
    public void testDoLastAddsActionToTheEndOfActionsList() {
        Action<Task> action1 = Actions.doNothing();
        Action<Task> action2 = Actions.doNothing();

        assertSame(defaultTask, defaultTask.doLast(action1));
        assertEquals(1, defaultTask.actions.size());

        assertSame(defaultTask, defaultTask.doLast(action2));
        assertEquals(2, defaultTask.actions.size());

        assertSame(action1, defaultTask.actions[0].action)
        assertSame(action2, defaultTask.actions[1].action)
    }

    @Test public void testSetsContextClassLoaderWhenExecutingAction() {
        Action<Task> testAction = context.mock(Action)
        context.checking {
            one(testAction).execute(defaultTask)
            will {
                assert Thread.currentThread().contextClassLoader == testAction.getClass().classLoader
            }
        }

        defaultTask.doFirst(testAction)

        Thread.currentThread().contextClassLoader = new ClassLoader(getClass().classLoader) {}
        defaultTask.actions[0].execute(defaultTask)
    }

    @Test public void testClosureActionDelegatesToTask() {
        Closure testAction = {
            assert delegate == defaultTask
            assert resolveStrategy == Closure.DELEGATE_FIRST
        }
        defaultTask.doFirst(testAction)
        defaultTask.actions[0].execute(defaultTask)
    }

    @Test public void testSetsContextClassLoaderWhenRunningClosureAction() {
        Closure testAction = {
            assert Thread.currentThread().contextClassLoader == getClass().classLoader
        }

        defaultTask.doFirst(testAction)

        Thread.currentThread().contextClassLoader = new ClassLoader(getClass().classLoader) {}
        defaultTask.actions[0].execute(defaultTask)
    }

    @Test public void testDoFirstWithClosureAddsActionToTheStartOfActionsList() {
        Closure testAction1 = { }
        Closure testAction2 = { }
        Closure testAction3 = { }
        defaultTask.doLast(testAction1)
        defaultTask.doLast(testAction2)
        defaultTask.doLast(testAction3)

        assertSame(defaultTask.actions[0].closure, testAction1)
        assertSame(defaultTask.actions[1].closure, testAction2)
        assertSame(defaultTask.actions[2].closure, testAction3)
    }

    @Test public void testDoLastWithClosureAddsActionToTheEndOfActionsList() {
        Closure testAction1 = { }
        Closure testAction2 = { }
        Closure testAction3 = { }
        defaultTask.doFirst(testAction1)
        defaultTask.doFirst(testAction2)
        defaultTask.doFirst(testAction3)

        assertSame(defaultTask.actions[0].closure, testAction3)
        assertSame(defaultTask.actions[1].closure, testAction2)
        assertSame(defaultTask.actions[2].closure, testAction1)
    }

    @Test public void testExecuteThrowsExecutionFailure() {
        def failure = new RuntimeException()
        defaultTask.doFirst { throw failure }

        try {
            defaultTask.execute()
            fail()
        } catch (TaskExecutionException e) {
            assertThat(e.cause, sameInstance(failure))
        }

        assertThat(defaultTask.state.failure, instanceOf(TaskExecutionException))
        assertThat(defaultTask.state.failure.cause, sameInstance(failure))
    }

    @Test public void testExecuteWithoutThrowingTaskFailureThrowsExecutionFailure() {
        def failure = new RuntimeException()
        defaultTask.doFirst { throw failure }

        defaultTask.executeWithoutThrowingTaskFailure()

        assertThat(defaultTask.state.failure, instanceOf(TaskExecutionException))
        assertThat(defaultTask.state.failure.cause, sameInstance(failure))
    }

    @Test
    void getAndSetConventionProperties() {
        TestConvention convention = new TestConvention()
        defaultTask.convention.plugins.test = convention
        assertTrue(defaultTask.hasProperty('conventionProperty'))
        defaultTask.conventionProperty = 'value'
        assertEquals(defaultTask.conventionProperty, 'value')
        assertEquals(convention.conventionProperty, 'value')
    }

    @Test
    void canCallConventionMethods() {
        defaultTask.convention.plugins.test = new TestConvention()
        assertEquals(defaultTask.conventionMethod('a', 'b').toString(), "a.b")
    }

    @Test(expected = MissingPropertyException)
    void accessNonExistingProperty() {
        defaultTask."unknownProp"
    }

    @Test
    void canGetTemporaryDirectory() {
        File tmpDir = new File(project.buildDir, "tmp/taskname")
        assertFalse(tmpDir.exists())

        assertThat(defaultTask.temporaryDir, equalTo(tmpDir))
        assertTrue(tmpDir.isDirectory())
    }

    @Test
    void canAccessServices() {
        assertNotNull(defaultTask.services.get(ListenerManager))
    }
}

class TestConvention {
    def conventionProperty

    def conventionMethod(a, b) {
        "$a.$b"
    }
}
