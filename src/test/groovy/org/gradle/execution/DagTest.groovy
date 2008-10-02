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

import org.gradle.execution.Dag
import static org.junit.Assert.*
import org.junit.Test

class DagTest {
    private static final String A = "A";
    private static final String B = "B";
    private static final String C = "C";
    private static final String D = "D";
    private static final Set AS = Collections.singleton(A);
    private static final Set BS = Collections.singleton(B);
    private static final Set CS = Collections.singleton(C);
    private static final Set AD = new LinkedHashSet([A, D]);
    private static final Set CD = new LinkedHashSet([C, D]);
    private static final Set ACD = new LinkedHashSet([A, C, D]);
    private static final Set BD = new LinkedHashSet([B, D]);

    private Dag<String> dag = new Dag<String>();

    @Test
    public void testEmpty() throws Exception {
        assertTrue(dag.getChildren("not in graph").isEmpty());
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
}
