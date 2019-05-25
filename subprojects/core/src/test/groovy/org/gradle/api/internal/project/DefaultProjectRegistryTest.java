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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.specs.Spec;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.TestUtil;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.SortedSet;
import java.util.TreeSet;

import static org.gradle.util.WrapUtil.toSet;
import static org.gradle.util.WrapUtil.toSortedSet;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class DefaultProjectRegistryTest {
    public static final String CHILD_NAME = "child";
    public static final String CHILD_CHILD_NAME = "childchild";
    private ProjectInternal rootMock;
    private ProjectInternal childMock;
    private ProjectInternal childChildMock;

    private DefaultProjectRegistry<ProjectInternal> projectRegistry;

    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider();

    @Before
    public void setUp() {
        projectRegistry = new DefaultProjectRegistry<ProjectInternal>();
        rootMock = TestUtil.create(temporaryFolder).rootProject();
        childMock = TestUtil.createChildProject(rootMock, CHILD_NAME);
        childChildMock = TestUtil.createChildProject(childMock, CHILD_CHILD_NAME);
        projectRegistry.addProject(rootMock);
        projectRegistry.addProject(childMock);
        projectRegistry.addProject(childChildMock);
    }

    @Test
    public void rootProject() {
        assertSame(rootMock, projectRegistry.getRootProject());
    }

    @Test
    public void addProject() {
        checkAccessMethods(rootMock, toSortedSet(rootMock, childMock, childChildMock), toSortedSet(childMock,
                childChildMock), rootMock);
        checkAccessMethods(childMock, toSortedSet(childMock, childChildMock), toSortedSet(childChildMock), childMock);
        checkAccessMethods(childChildMock, toSortedSet(childChildMock), new TreeSet(), childChildMock);
    }

    private void checkAccessMethods(Project project, SortedSet<ProjectInternal> expectedAllProjects,
                                    SortedSet<ProjectInternal> expectedSubProjects, Project expectedGetProject) {
        assertSame(expectedGetProject, projectRegistry.getProject(project.getPath()));
        assertEquals(expectedAllProjects, projectRegistry.getAllProjects(project.getPath()));
        assertEquals(expectedSubProjects, projectRegistry.getSubProjects(project.getPath()));
        assertSame(expectedGetProject, projectRegistry.getProject(project.getProjectDir()));
        assertTrue(projectRegistry.getAllProjects().contains(project));
    }

    @Test
    public void cannotLocateProjectsWithAmbiguousProjectDir() {
        ProjectInternal duplicateProjectDirProject = TestUtil.createChildProject(childMock, "childchild2", childMock.getProjectDir());
        projectRegistry.addProject(duplicateProjectDirProject);

        try {
            projectRegistry.getProject(childMock.getProjectDir());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), startsWith("Found multiple projects with project directory "));
        }
    }

    @Test
    public void accessMethodsForNonexistentsPaths() {
        projectRegistry = new DefaultProjectRegistry<ProjectInternal>();
        Project otherRoot = TestUtil.create(temporaryFolder.getTestDirectory()).rootProject();
        assertNull(projectRegistry.getProject(otherRoot.getPath()));
        assertEquals(new TreeSet<ProjectInternal>(), projectRegistry.getAllProjects(otherRoot.getPath()));
        assertEquals(new TreeSet<ProjectInternal>(), projectRegistry.getSubProjects(otherRoot.getPath()));
        assertNull(projectRegistry.getProject(otherRoot.getProjectDir()));
    }

    @Test
    public void canLocalAllProjects() {
        assertThat(projectRegistry.getAllProjects(), equalTo(toSet((ProjectInternal) rootMock, childMock,
                childChildMock)));
    }

    @Test
    public void canLocateAllProjectsWhichMatchSpec() {
        Spec<Project> spec = new Spec<Project>() {
            public boolean isSatisfiedBy(Project element) {
                return element.getName().contains("child");
            }
        };

        assertThat(projectRegistry.findAll(spec), equalTo(toSet((ProjectInternal) childMock, childChildMock)));
    }

    @Test
    public void canRemoveProject() {
        String path = childChildMock.getPath();
        assertThat(projectRegistry.removeProject(path), sameInstance((ProjectInternal) childChildMock));
        assertThat(projectRegistry.getProject(path), nullValue());
        assertThat(projectRegistry.getProject(childChildMock.getProjectDir()), nullValue());
        assertTrue(projectRegistry.getAllProjects(path).isEmpty());
        assertTrue(projectRegistry.getSubProjects(path).isEmpty());
        assertFalse(projectRegistry.getAllProjects().contains(childChildMock));
        assertFalse(projectRegistry.getAllProjects(":").contains(childChildMock));
        assertFalse(projectRegistry.getSubProjects(":").contains(childChildMock));
    }
}
