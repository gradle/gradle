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
package org.gradle.api.internal.project;

import org.gradle.api.Project;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.SortedSet;
import java.util.TreeSet;

import static org.gradle.util.internal.WrapUtil.toSet;
import static org.gradle.util.internal.WrapUtil.toSortedSet;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DefaultProjectRegistryTest {
    public static final String CHILD_NAME = "child";
    public static final String CHILD_CHILD_NAME = "childchild";
    private ProjectInternal rootMock;
    private ProjectInternal childMock;
    private ProjectInternal childChildMock;

    private DefaultProjectRegistry projectRegistry;

    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());

    @Before
    public void setUp() {
        projectRegistry = new DefaultProjectRegistry();
        rootMock = TestUtil.create(temporaryFolder).rootProject();
        childMock = TestUtil.createChildProject(rootMock, CHILD_NAME);
        childChildMock = TestUtil.createChildProject(childMock, CHILD_CHILD_NAME);
        projectRegistry.addProject(rootMock);
        projectRegistry.addProject(childMock);
        projectRegistry.addProject(childChildMock);
    }

    @Test
    public void addProject() {
        checkAccessMethods(rootMock, toSortedSet(rootMock, childMock, childChildMock), toSortedSet(childMock,
                childChildMock), rootMock);
        checkAccessMethods(childMock, toSortedSet(childMock, childChildMock), toSortedSet(childChildMock), childMock);
        checkAccessMethods(childChildMock, toSortedSet(childChildMock), new TreeSet<>(), childChildMock);
    }

    private void checkAccessMethods(
        ProjectInternal project,
        SortedSet<ProjectInternal> expectedAllProjects,
        SortedSet<ProjectInternal> expectedSubProjects,
        Project expectedGetProject
    ) {
        assertSame(expectedGetProject, projectRegistry.getProject(project.getPath()));
        assertSame(expectedGetProject, projectRegistry.getProjectInternal(project.getPath()));
        assertEquals(expectedAllProjects, projectRegistry.getAllProjects(project.getPath()));
        assertEquals(expectedSubProjects, projectRegistry.getSubProjects(project.getPath()));
        assertTrue(projectRegistry.getAllProjects(project.getPath()).contains(project));
    }

    @Test
    public void accessMethodsForNonexistentsPaths() {
        projectRegistry = new DefaultProjectRegistry();
        Project otherRoot = TestUtil.create(temporaryFolder.getTestDirectory()).rootProject();
        assertNull(projectRegistry.getProject(otherRoot.getPath()));
        assertNull(projectRegistry.getProjectInternal(otherRoot.getPath()));
        assertEquals(new TreeSet<ProjectInternal>(), projectRegistry.getAllProjects(otherRoot.getPath()));
        assertEquals(new TreeSet<ProjectInternal>(), projectRegistry.getSubProjects(otherRoot.getPath()));
    }

    @Test
    public void canLocalAllProjects() {
        assertThat(projectRegistry.getAllProjects(rootMock.getPath()), equalTo(toSet(rootMock, childMock, childChildMock)));
    }
}
