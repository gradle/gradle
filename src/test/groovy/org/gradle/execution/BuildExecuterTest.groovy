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

package org.gradle.execution

import groovy.mock.interceptor.MockFor
import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.DefaultTask
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.util.HelperUtil



/**
* @author Hans Dockter
*/
class BuildExecuterTest extends GroovyTestCase {
    static File TEST_ROOT_DIR = new File("/path/root")

    BuildExecuter buildExecuter
    DefaultProject root
    DefaultProject child

    void setUp() {
        root = new DefaultProject("root", null, new File(""), null, new ProjectFactory(new DefaultDependencyManagerFactory()), new DefaultDependencyManager(), new BuildScriptProcessor(), new BuildScriptFinder('somebuildfile'), new PluginRegistry())
        child = root.addChildProject("child")
        buildExecuter = new BuildExecuter(new Dag())
    }

    void testExecute() {
        String expectedTaskName = 'test'
        boolean expectedRecursive = true

        DefaultTask rootCompile = new DefaultTask(root, 'compile')
        DefaultTask rootTest = new DefaultTask(root, 'test')
        DefaultTask childCompile = new DefaultTask(child, 'compile')
        DefaultTask childTest = new DefaultTask(child, 'test')
        rootTest.dependsOn = [rootCompile.path]
        childTest.dependsOn = [childCompile.name]
        boolean configureByDagCalled = false
        root.configureByDag = {
               configureByDagCalled = true
        }
        child.configureByDag  = null

        root.tasks = [(rootCompile.name): rootCompile, (rootTest.name): rootTest]
        child.tasks = [(childCompile.name): childCompile, (childTest.name): childTest]

        MockFor dagMocker = new MockFor(Dag)
        Map checkerFirstRun = [:]
        dagMocker.demand.reset(1..1) {}
        dagMocker.demand.addTask(0..100) {task, dependencies ->
            checkerFirstRun[task] = dependencies
        }
        dagMocker.demand.getProjects(1..1) {[root, child]}
        dagMocker.demand.execute(1..1) {[:] as Dag}

        dagMocker.use() {
            buildExecuter.execute(expectedTaskName, expectedRecursive, root, root)
        }

        assertEquals(checkerFirstRun.size(), 4)
        assertEquals([rootCompile] as Set, checkerFirstRun[rootTest])
        assertEquals([childCompile] as Set, checkerFirstRun[childTest])
        assertEquals([] as Set, checkerFirstRun[rootCompile])
        assertEquals([] as Set, checkerFirstRun[childCompile])
    }

    void testUnknownTasks() {
        DefaultTask rootCompile = new DefaultTask(root, 'compile')
        DefaultTask rootTest = new DefaultTask(root, 'test')
        DefaultTask childCompile = new DefaultTask(child, 'compile')
        DefaultTask childOtherTask = new DefaultTask(child, 'other')

        root.tasks = [(rootCompile.name): rootCompile, (rootTest.name): rootTest]
        child.tasks = [(childCompile.name): childCompile, (childOtherTask.name): childOtherTask]

        assertEquals([], buildExecuter.unknownTasks(['compile', 'test'], false, root))
        assertEquals(['test'], buildExecuter.unknownTasks(['compile', 'test'], true, child))
        assertEquals([], buildExecuter.unknownTasks(['compile', 'other'], true, root))
        assertEquals(['other'], buildExecuter.unknownTasks(['compile', 'other'], false, root))
    }

    void testExecuteWithTransitiveTargetDependecies() {
        DefaultTask task1 = new DefaultTask(root, 'task1')
        DefaultTask task2 = new DefaultTask(root, 'task2').dependsOn('task1')
        DefaultTask task3 = new DefaultTask(root, 'task3').dependsOn('task2')
        root.tasks = [task1: task1, task2: task2, task3: task3]
        MockFor dagMocker = new MockFor(Dag)
        Map checker = [:]
        dagMocker.demand.reset(1..1) {}
        dagMocker.demand.addTask(3..3) {task, dependencies ->
            checker[task] = dependencies
        }
        dagMocker.demand.getProjects(1..1) {[root, child]}
        dagMocker.demand.execute(1..1) {[:] as Dag}

        dagMocker.use() {
            buildExecuter.execute('task3', false, root, root)
        }

        assertEquals([task2] as Set, checker[task3])
        assertEquals([task1] as Set, checker[task2])
        assertEquals([] as Set, checker[task1])

    }

    void testExecuteWithNonExistingProjectForDependentTarget() {
        DefaultProject child = HelperUtil.createProjectMock([getTargetsByName: {a, b -> [(child): new DefaultTask(child, 'compile').dependsOn('/root/unknownchild/compile')] as TreeMap}], 'child', root)
        root.childProjects['child'] = child
        shouldFail(UnknownTaskException) {
            buildExecuter.execute('compile', true, child, root)
        }
    }

    void testExecuteWithNonExistingDependentTarget() {
        DefaultProject child = HelperUtil.createProjectMock([getTargetsByName: {a, b -> [(child): new DefaultTask(child, 'compile').dependsOn('/root/child/unknownTarget')] as TreeMap}], 'child', root)
        root.childProjects['child'] = child
        shouldFail(UnknownTaskException) {
            buildExecuter.execute('compile', true, child, root)
        }
    }

    void testExecuteWithNonExistingTarget() {
        shouldFail(UnknownTaskException) {
            buildExecuter.execute('compil', true, root, root)
        }
    }

}