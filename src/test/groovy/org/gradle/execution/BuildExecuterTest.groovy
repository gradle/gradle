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
        List expectedTaskNames = ['compile', 'test']
        boolean expectedRecursive = true

        DefaultTask rootCompile = new DefaultTask(root, 'compile')
        DefaultTask rootTest = new DefaultTask(root, 'test')
        DefaultTask childCompile = new DefaultTask(child, 'compile')
        DefaultTask childTest = new DefaultTask(child, 'test')
        rootTest.dependsOn = [rootCompile.path]
        childTest.dependsOn = [childCompile.name]

        root.tasks = [(rootCompile.name): rootCompile, (rootTest.name): rootTest]
        child.tasks = [(childCompile.name): childCompile, (childTest.name): childTest]

        MockFor dagMocker = new MockFor(Dag)
        Map checkerFirstRun = [:]
        dagMocker.demand.addTask(0..100) {task, dependencies ->
            checkerFirstRun[task] = dependencies
        }
        dagMocker.demand.execute(1..1) {[:] as Dag}
        dagMocker.demand.reset(1..1) {}
        Map checkerSecondRun = [:]
        dagMocker.demand.addTask(0..100) {task , dependencies ->
            checkerSecondRun[task ] = dependencies
        }
        dagMocker.demand.execute(1..1) {[:] as Dag}
        dagMocker.demand.reset(1..1) {}

        dagMocker.use() {
            buildExecuter.execute(expectedTaskNames, expectedRecursive, root, root)
        }

        assertEquals(checkerFirstRun.size(), 2)
        assertEquals([] as Set, checkerFirstRun[rootCompile])
        assertEquals([] as Set, checkerFirstRun[childCompile])
        assertEquals(checkerSecondRun.size(), 4)
        assertEquals([rootCompile] as Set, checkerSecondRun[rootTest])
        assertEquals([childCompile] as Set, checkerSecondRun[childTest])
        assertEquals([] as Set, checkerSecondRun[rootCompile])
        assertEquals([] as Set, checkerSecondRun[childCompile])

    }

    void testExecuteWithTransitiveTargetDependecies() {
        DefaultTask task1 = new DefaultTask(root, 'task1')
        DefaultTask task2 = new DefaultTask(root, 'task2').dependsOn('task1')
        DefaultTask task3 = new DefaultTask(root, 'task3').dependsOn('task2')
        root.tasks = [task1: task1, task2: task2, task3: task3]
        MockFor dagMocker = new MockFor(Dag)
        Map checker = [:]
        dagMocker.demand.addTask(3..3) {task, dependencies ->
            checker[task] = dependencies
        }
        dagMocker.demand.execute(1..1) {[:] as Dag}
        dagMocker.demand.reset(1..1) {}

        dagMocker.use() {
            buildExecuter.execute(['task3'], false, root, root)
        }

        assertEquals([task2] as Set, checker[task3])
        assertEquals([task1] as Set, checker[task2])
        assertEquals([] as Set, checker[task1])

    }

    void testExecuteWithNonExistingProjectForDependentTarget() {
        DefaultProject child = HelperUtil.createProjectMock([getTargetsByName: {a, b -> [(child): new DefaultTask(child, 'compile').dependsOn('/root/unknownchild/compile')] as TreeMap}], 'child', root)
        root.childProjects['child'] = child
        shouldFail(UnknownTaskException) {
            buildExecuter.execute(['compile'], true, child, root)
        }
    }

    void testExecuteWithNonExistingDependentTarget() {
        DefaultProject child = HelperUtil.createProjectMock([getTargetsByName: {a, b -> [(child): new DefaultTask(child, 'compile').dependsOn('/root/child/unknownTarget')] as TreeMap}], 'child', root)
        root.childProjects['child'] = child
        shouldFail(UnknownTaskException) {
            buildExecuter.execute(['compile'], true, child, root)
        }
    }

    void testExecuteWithNonExistingTarget() {
        shouldFail(UnknownTaskException) {
            buildExecuter.execute(['compil'], true, root, root)
        }
    }

    void testExecuteWithOneNonExistingTarget() {
        DefaultTask rootTarget = new DefaultTask(root, 'compile')
        DefaultTask childTarget = new DefaultTask(child, 'compile')
        root.tasks = [(rootTarget.name): rootTarget]
        child.tasks = [(childTarget.name): childTarget]
        MockFor dagMocker = new MockFor(Dag)
        dagMocker.demand.addTask(2..2) {task, dependsOnTasks ->}
        dagMocker.demand.execute(1..1) {[:] as Dag}
        dagMocker.demand.reset(1..1) {}
        dagMocker.use() {
            shouldFail(UnknownTaskException) {
                buildExecuter.execute(['compile', 'unknown'], true, root, root)
            }
        }
    }
}