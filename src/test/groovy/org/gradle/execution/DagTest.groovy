/** *****************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 ****************************************************************************** */
/*
 * For the additions made to this class:
 *
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

import org.gradle.api.CircularReferenceException
import org.gradle.api.Project
import org.gradle.api.internal.DefaultTask
import org.gradle.execution.Dag
import org.gradle.util.HelperUtil
import org.junit.Before
import static org.junit.Assert.*
import org.junit.Test;

class DagTest {
    private static final Object A = "A";
    private static final Object B = "B";
    private static final Object C = "C";
    private static final Object D = "D";
    private static final Set AS = Collections.singleton(A);
    private static final Set BS = Collections.singleton(B);
    private static final Set CS = Collections.singleton(C);
    private static final Set AD = new LinkedHashSet([A, D]);
    private static final Set CD = new LinkedHashSet([C, D]);
    private static final Set ACD = new LinkedHashSet([A, C, D]);
    private static final Set BD = new LinkedHashSet([B, D]);

    private Project root;

    private Dag dag

    @Before public void setUp() {
        dag = new Dag()
        root = HelperUtil.createRootProject(new File('root'))
    }

    @Test
    public void testEmpty() throws Exception {
        assertTrue(dag.getChildren(new Object()).isEmpty());
        assertTrue(dag.getSources().isEmpty());
        assertTrue(dag.getSinks().isEmpty());
    }

    @Test
    public void testIllegal() throws Exception {
        assertFalse(dag.addEdge(A, A));
        try {
            dag.addEdge(A, null);
            fail();
        } catch (AssertionError x) {
        }
        try {
            dag.addEdge(null, A);
            fail();
        } catch (AssertionError x) {
        }
        try {
            dag.addEdge(null, null);
            fail();
        } catch (AssertionError x) {
        }
        try {
            dag.addVertex(null);
            fail();
        } catch (AssertionError x) {
        }
    }

    @Test
    public void testDag() throws Exception {
        assertTrue(dag.addEdge(A, B));
        assertEquals(AS, dag.getSources());
        assertEquals(BS, dag.getSinks());
        assertFalse(dag.addEdge(B, A));
        assertEquals(AS, dag.getSources());
        assertEquals(BS, dag.getSinks());
        assertEquals(BS, dag.getChildren(A));
        assertTrue(dag.getChildren(B).isEmpty());
        assertTrue(dag.getChildren(C).isEmpty());
        assertTrue(dag.getChildren(D).isEmpty());

        assertTrue(dag.addEdge(B, C));
        assertEquals(AS, dag.getSources());
        assertEquals(CS, dag.getSinks());
        assertEquals(BS, dag.getChildren(A));
        assertEquals(CS, dag.getChildren(B));
        assertTrue(dag.getChildren(C).isEmpty());
        assertTrue(dag.getChildren(D).isEmpty());

        dag.addVertex(C);
        assertEquals(AS, dag.getSources());
        assertEquals(CS, dag.getSinks());
        assertEquals(BS, dag.getChildren(A));
        assertEquals(CS, dag.getChildren(B));
        assertTrue(dag.getChildren(C).isEmpty());
        assertTrue(dag.getChildren(D).isEmpty());

        dag.addVertex(D);
        assertEquals(AD, dag.getSources());
        assertEquals(CD, dag.getSinks());
        assertEquals(BS, dag.getChildren(A));
        assertEquals(CS, dag.getChildren(B));
        assertTrue(dag.getChildren(C).isEmpty());
        assertTrue(dag.getChildren(D).isEmpty());

        dag.removeVertex(A);
        assertEquals(BD, dag.getSources());
        assertEquals(CD, dag.getSinks());
        assertTrue(dag.getChildren(A).isEmpty());
        assertEquals(CS, dag.getChildren(B));
        assertTrue(dag.getChildren(C).isEmpty());
        assertTrue(dag.getChildren(D).isEmpty());

        assertTrue(dag.addEdge(A, B));
        assertTrue(dag.addEdge(D, B));
        assertEquals(AD, dag.getSources());
        assertEquals(CS, dag.getSinks());
        assertEquals(BS, dag.getChildren(A));
        assertEquals(CS, dag.getChildren(B));
        assertTrue(dag.getChildren(C).isEmpty());
        assertEquals(BS, dag.getChildren(D));

        dag.removeVertex(B);
        assertEquals(ACD, dag.getSources());
        assertEquals(ACD, dag.getSinks());
        assertTrue(dag.getChildren(A).isEmpty());
        assertTrue(dag.getChildren(B).isEmpty());
        assertTrue(dag.getChildren(C).isEmpty());
        assertTrue(dag.getChildren(D).isEmpty());
    }

    @Test public void testAddTask() {
        DefaultTask dummyTask = new DefaultTask(root, 'a')
        Set dependsOnTasks = [new DefaultTask(root, 'b'), new DefaultTask(root, 'c')]
        dag.addTask(dummyTask, dependsOnTasks)
        assertEquals(new HashSet([dummyTask]), dag.sources)
        assertEquals(new HashSet(dependsOnTasks), dag.sinks)
    }



    @Test (expected = CircularReferenceException) public void testAddTaskWithCircularReference() {
        DefaultTask dummyTask = new DefaultTask(root, 'a')
        DefaultTask dummyTask2 = new DefaultTask(root, 'b')
        dag.addTask(dummyTask, [dummyTask2] as Set)
        dag.addTask(dummyTask2, [dummyTask] as Set)
    }

    @Test public void testExecute() {
        Project child = root.addChildProject('child')
        List executedIdList = []
        DefaultTask dummyTask0 = createTask(root, 'a', executedIdList, 2)
        println dummyTask0.getPath()
        Set dependsOnTasks0 = [createTask(root, 'child2', executedIdList, 1),
                createTask(root, 'child1', executedIdList, 0)]
        DefaultTask dummyTask1 = createTask(root, 'b', executedIdList, 5)
        Set dependsOnTasks1 = [createTask(child, 'child', executedIdList, 4),
                createTask(root, 'longlonglonglongchild', executedIdList, 3)]
        dag.addTask(dummyTask0, dependsOnTasks0)
        println dag.sources
        println dag.sinks
        dag.addTask(dummyTask1, dependsOnTasks1)
        dag.execute()
        assertEquals([0, 1, 2, 3, 4, 5], executedIdList)
    }

    private DefaultTask createTask(Project project, String name, List executedIdList, int executedId) {
        DefaultTask dummyTask0 = new DefaultTask(project, name)
        dummyTask0.doFirst { executedIdList << executedId }
        dummyTask0
    }

    @Test public void testReset() {
        DefaultTask dummyTask0 = new DefaultTask(root, 'a')
        Set dependsOnTasks0 = [new DefaultTask(root, 'child2'), new DefaultTask(root, 'child1')]
        dag.addTask(dummyTask0, dependsOnTasks0)
        assertTrue(dag.sources.size() > 0)
        assertTrue(dag.sinks.size() > 0)
        dag.reset()
        assertEquals(0, dag.sources.size())
        assertEquals(0, dag.sinks.size())
    }

    @Test public void testGetProjects() {
        Project root = HelperUtil.createRootProject(new File('/root'))
        Project child = HelperUtil.createProjectMock([:], 'child', root)
        DefaultTask task1 = new DefaultTask(root, 'task1')
        DefaultTask task2 = new DefaultTask(child, 'task2')
        dag.addTask(task1, [] as Set)
        dag.addTask(task2, [] as Set)
        assertEquals([root, child] as Set, dag.projects)
    }

    @Test public void testHasTask() {
        Project root = HelperUtil.createRootProject(new File('/root'))
        Project child = HelperUtil.createProjectMock([:], 'child', root)
        DefaultTask task1 = new DefaultTask(root, 'task1')
        DefaultTask task2 = new DefaultTask(child, 'task2')
        dag.addTask(task1, [] as Set)
        dag.addTask(task2, [] as Set)
        assertTrue(dag.hasTask(':task1'))
        assertFalse(dag.hasTask(':task2'))
        assertTrue(dag.hasTask(':child:task2'))
    }

    @Test public void testAddProjectDependencies() {

    }
}
