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
                file: {String path -> new File("$HelperUtil.TMP_DIR_FOR_TEST/$path")}] as DefaultProject
    }

    abstract Task getTask()

    Task createTask(DefaultProject project, String name) {
        task.class.newInstance(project, name)
    }

    void testTask() {
        assertEquals(TEST_TASK_NAME, task.name)
        assertEquals(project, task.project)
        assertNotNull(task.skipProperties)
    }

    void testPath() {
        DefaultProject rootProject = new DefaultProject()
        rootProject.rootProject = rootProject
        DefaultProject childProject = new DefaultProject()
        childProject.rootProject = rootProject
        childProject.parent = rootProject
        childProject.name = 'child'
        DefaultProject childchildProject = new DefaultProject()
        childchildProject.rootProject = rootProject
        childchildProject.parent = childProject
        childchildProject.name = 'childchild'

        DefaultTask task = createTask(rootProject, TEST_TASK_NAME)
        assertEquals Project.PATH_SEPARATOR + "$TEST_TASK_NAME", task.path
        task = createTask(childProject, TEST_TASK_NAME)
        assertEquals Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.path
        task = createTask(childchildProject, TEST_TASK_NAME)
        assertEquals Project.PATH_SEPARATOR + "child" + Project.PATH_SEPARATOR + "childchild" + Project.PATH_SEPARATOR + TEST_TASK_NAME, task.path
    }

    void testDependsOn() {
        Task task = createTask(project, TEST_TASK_NAME)
        task.dependsOn(Project.PATH_SEPARATOR + 'path1')
        assertEquals(new TreeSet([Project.PATH_SEPARATOR + 'path1']), task.dependsOn)
        task.dependsOn Project.PATH_SEPARATOR + 'path2', 'path3'
        assertEquals(new TreeSet([Project.PATH_SEPARATOR + 'path1', Project.PATH_SEPARATOR + 'path2', "path3" as String]), task.dependsOn)
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

    void testLateInitialize() {
        assertFalse(task.lateInitialized)
        List calledLateInitClosures = []
        Closure initializeClosure1 = {calledLateInitClosures << 'closure1'}
        Closure initializeClosure2 = {calledLateInitClosures << 'closure2'}
        task.lateInitalizeClosures << initializeClosure1 << initializeClosure2
        assert task.is(task.lateInitialize())
        assertEquals(['closure1', 'closure2'], calledLateInitClosures)
        assertTrue(task.lateInitialized)
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
        Closure action1 = { throw new StopExecutionException() }
        boolean action2Called = false
        Closure action2 = {action2Called = true}
        task.doFirst(action2)
        task.doFirst(action1)
        task.execute()
        assertFalse(action2Called)
        assertTrue(task.executed)
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

        System.setProperty(task.skipProperties[0], 'false')
        task.executed = false
        task.execute()
        assertTrue(action1Called)
        assertTrue(task.executed)
    }
}
