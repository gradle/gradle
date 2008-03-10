/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/ 
package org.gradle.execution

import org.gradle.api.CircularReferenceException
import org.gradle.api.Project
import org.gradle.api.internal.DefaultTask
import org.gradle.execution.Dag

class DagTest extends GroovyTestCase {
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

    private Dag dag = new Dag();

    public void testEmpty() throws Exception {
        assertTrue(dag.getChildren(new Object()).isEmpty());
        assertTrue(dag.getSources().isEmpty());
        assertTrue(dag.getSinks().isEmpty());
    }

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

    void testAddTarget() {
        DefaultTask dummyTarget = [getPath: {'/a'}] as DefaultTask
        Set dependsOnTargets = [[getPath: {'/b'}] as DefaultTask, [getPath: {'/c'}] as DefaultTask]
        dag.addTask(dummyTarget, dependsOnTargets)
        assertEquals(new HashSet([dummyTarget]), dag.sources)
        assertEquals(new HashSet(dependsOnTargets), dag.sinks)
    }

    void testAddTargetWithCircularReference() {
        DefaultTask dummyTarget = [getPath: {'/a'}] as DefaultTask
        DefaultTask dummyTarget2 = [getPath: {'/b'}] as DefaultTask
        dag.addTask(dummyTarget, [dummyTarget2] as Set)
        shouldFail(CircularReferenceException) {
            dag.addTask(dummyTarget2, [dummyTarget] as Set)
        }
    }

    void testExecute() {
        List executed = []
        DefaultTask dummyTarget0 = [getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'a'},
                execute: {executed << 2}] as DefaultTask
        Set dependsOnTargets0 = [[getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'child2'},
                execute: {executed << 1}] as DefaultTask, [getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'child1'},
                execute: {executed << 0}] as DefaultTask]
        DefaultTask dummyTarget1 = [getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'b'},
                execute: {executed << 5}] as DefaultTask
        Set dependsOnTargets1 = [[getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'child' + Project.PATH_SEPARATOR + 'child'},
                execute: {executed << 4}] as DefaultTask, [getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'longlonglonglongchild'},
                execute: {executed << 3}] as DefaultTask]
        dag.addTask(dummyTarget0, dependsOnTargets0)
        dag.addTask(dummyTarget1, dependsOnTargets1)
        dag.execute()
        assertEquals([0, 1, 2, 3, 4, 5], executed)
    }

    void testReset() {
        DefaultTask dummyTarget0 = [getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'a'}] as DefaultTask
        Set dependsOnTargets0 = [[getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'child2'}] as DefaultTask,
                [getPath: {Project.PATH_SEPARATOR + 'root' + Project.PATH_SEPARATOR + 'child1'}] as DefaultTask]
        dag.addTask(dummyTarget0, dependsOnTargets0)
        assertTrue(dag.sources.size() > 0)
        assertTrue(dag.sinks.size() > 0)
        dag.reset()
        assertEquals(0, dag.sources.size())
        assertEquals(0, dag.sinks.size())
    }

    void testAddProjectDependencies() {
        
    }
}
