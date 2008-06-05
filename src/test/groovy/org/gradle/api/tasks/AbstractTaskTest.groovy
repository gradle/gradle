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

package org.gradle.api.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
abstract class AbstractTaskTest extends GroovyTestCase {
    static final String TEST_TASK_NAME = 'taskname'

    static final String TEST_PROJECT_NAME = '/projectTestName'

    DefaultProject project

    void setUp() {
        project = [getPath: {AbstractTaskTest.TEST_PROJECT_NAME},
                getProjectDir: {new File(HelperUtil.TMP_DIR_FOR_TEST)},
                file: {path ->
                    (path as File).isAbsolute() ? path as File :
                        new File("$HelperUtil.TMP_DIR_FOR_TEST/$path").absoluteFile
                }] as DefaultProject
    }

    abstract Task getTask()

    Task createTask(DefaultProject project, String name) {
        task.class.newInstance(project, name)
    }

    void testTask() {
        assertTrue(task.enabled)
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals(project, task.project)
        assertNotNull(task.skipProperties)
    }

    void testPath() {
        DefaultProject rootProject = HelperUtil.createRootProject(new File('parent', 'root'))
        DefaultProject childProject = HelperUtil.createChildProject(rootProject, 'child')
        DefaultProject childchildProject = HelperUtil.createChildProject(childProject, 'childchild')

        DefaultTask task = createTask(rootProject, TEST_TASK_NAME)
        assertEquals Project.PATH_SEPARATOR + "$TEST_TASK_NAME", task.path
        task = createTask(childProject, TEST_TASK_NAME)
        assertEquals Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.path
        task = createTask(childchildProject, TEST_TASK_NAME)
        assertEquals Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + "childchild" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.path
    }

    void testDependsOn() {
        Task dependsOnTask = createTask(project, 'somename')
        Task task = createTask(project, TEST_TASK_NAME)
        task.dependsOn(Project.PATH_SEPARATOR + 'path1')
        assertEquals([Project.PATH_SEPARATOR + 'path1'] as Set, task.dependsOn)
        task.dependsOn('path2', dependsOnTask)
        assertEquals([Project.PATH_SEPARATOR + 'path1', 'path2', dependsOnTask] as Set, task.dependsOn)
    }

    void testDependsOnWithIllegalArguments() {
        shouldFail(InvalidUserDataException) {
            task.dependsOn('path1', '')
        }
        shouldFail(InvalidUserDataException) {
            task.dependsOn('', 'path1')
        }
        shouldFail(InvalidUserDataException) {
            task.dependsOn(null, 'path1')
        }
    }

    void testToString() {
        assertEquals(task.path, task.toString())
    }

    void testDoFirst() {
        Closure action1 = {}
        Closure action2 = {}
        int actionSizeBefore = task.actions.size()
        assert task.is(task.doFirst(action2))
        assertEquals(actionSizeBefore + 1, task.actions.size())
        assertEquals(action2, task.actions[0])
        task.is(task.doFirst(action1))
        assertEquals(action1, task.actions[0])
    }

    void testDoLast() {
        Closure action1 = {}
        Closure action2 = {}
        int actionSizeBefore = task.actions.size()
        assert task.is(task.doLast(action1))
        assertEquals(actionSizeBefore + 1, task.actions.size())
        assertEquals(action1, task.actions[task.actions.size() - 1])
        task.is(task.doLast(action2))
        assertEquals(action2, task.actions[task.actions.size() - 1])
    }

    void testDeleteAllActions() {
        Closure action1 = {}
        Closure action2 = {}
        task.doLast(action1)
        task.doLast(action2)
        assert task.is(task.deleteAllActions())
        assertEquals([], task.actions)
    }

    void testAddActionWithNull() {
        shouldFail(InvalidUserDataException) {
            task.doLast(null)
        }
    }

    void testBasicExecute() {
        task.actions = []
        assertFalse(task.executed)
        boolean action1Called = false
        Closure action1 = {Task task -> action1Called = true}
        boolean action2Called = false
        Closure action2 = {Task task -> action2Called = true}
        task.doLast(action1)
        task.doLast(action2)
        task.execute()
        assertTrue(task.executed)
        assertTrue(action1Called)
        assertTrue(action2Called)
    }

    void testConfigure() {
        Closure action1 = {}
        assert task.configure {
            doFirst(action1)
        }.is(task)
        assertEquals(action1, task.actions[0])
    }

    void testStopExecution() {
        Closure action1 = {throw new StopExecutionException()}
        boolean action2Called = false
        Closure action2 = {action2Called = true}
        task.doFirst(action2)
        task.doFirst(action1)
        task.execute()
        assertFalse(action2Called)
        assertTrue(task.executed)
    }

    void testStopAction() {
        task.actions = []
        Closure action1 = {
            throw new StopActionException()
            fail()
        }
        boolean action2Called = false
        Closure action2 = {action2Called = true}
        task.doFirst(action2)
        task.doFirst(action1)
        task.execute()
        assertTrue(action2Called)
        assertTrue(task.executed)
    }

    void testDisabled() {
        task.actions = []
        Closure action1 = {
            fail()
        }
        task.doFirst(action1)
        task.enabled = false
        task.execute()
        assert task.executed
    }

    void testSkipProperties() {
        task.skipProperties = ['prop1']
        boolean action1Called = false
        Closure action1 = {
            action1Called = true
            throw new StopExecutionException()
        }
        task.doFirst(action1)

        System.setProperty(task.skipProperties[0], 'true')
        task.execute()
        assertFalse(action1Called)
        assertTrue(task.executed)

        System.setProperty(task.skipProperties[0], '')
        task.executed = false
        task.execute()
        assertFalse(action1Called)
        assertTrue(task.executed)

        System.setProperty(task.skipProperties[0], 'false')
        task.executed = false
        task.execute()
        assertTrue(action1Called)
        assertTrue(task.executed)
        System.properties.remove(task.skipProperties[0])
    }

    void testAutoSkipProperties() {
        boolean action1Called = false
        Closure action1 = {
            action1Called = true
            throw new StopExecutionException()
        }
        task.doFirst(action1)

        System.setProperty("skip.$task.name", 'true')
        task.execute()
        assertFalse(action1Called)
        assertTrue(task.executed)

        System.setProperty("skip.$task.name", 'false')
        task.executed = false
        task.execute()
        assertTrue(action1Called)
        assertTrue(task.executed)
        System.properties.remove("skip.$task.name")
    }

    void testAfterDag() {
        checkConfigureEvent(task.&afterDag, task.&applyAfterDagClosures)
    }

    void testLateInitialize() {
        assert !task.lateInitialized
        checkConfigureEvent(task.&lateInitialize, task.&applyLateInitialize)
        assert task.lateInitialized
    }

    void checkConfigureEvent(Closure addMethod, Closure applyMethod) {
        Closure action1 = {}
        Closure action2 = {}
        assert addMethod {
            doFirst(action2)
        }.is(task)
        addMethod {
            doFirst(action1)
        }
        assert !task.actions[0].is(action1)
        assert applyMethod().is(task)
        assert task.actions[0].is(action1)
        assert task.actions[1].is(action2)
    }
}
