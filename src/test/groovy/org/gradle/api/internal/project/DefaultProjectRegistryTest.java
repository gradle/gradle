/*
 * Copyright 2007-2008 the original author or authors.
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

import static junit.framework.Assert.assertSame;
import org.gradle.api.Project;
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.HelperUtil;
import org.gradle.util.WrapUtil;
import org.gradle.util.GUtil;
import org.jmock.integration.junit4.JUnit4Mockery;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * @author Hans Dockter
 */
public class DefaultProjectRegistryTest {
    public static final String CHILD_NAME = "child";
    public static final String CHILD_CHILD_NAME = "childchild";
    private DefaultProject rootMock;
    private DefaultProject childMock;
    private DefaultProject childChildMock;

    private DefaultProjectRegistry projectRegistry;
    
    @Before
    public void setUp() {
        projectRegistry = new DefaultProjectRegistry();
        rootMock = HelperUtil.createRootProject(new File("root"));
        childMock = (DefaultProject) rootMock.addChildProject(CHILD_NAME, new File(CHILD_NAME));
        childChildMock = (DefaultProject) childMock.addChildProject(CHILD_CHILD_NAME, new File(CHILD_CHILD_NAME));
        projectRegistry.addProject(rootMock);
        projectRegistry.addProject(childMock);
        projectRegistry.addProject(childChildMock);
    }

    @Test
    public void addProject() {
        checkAccessMethods(rootMock, WrapUtil.toSortedSet(rootMock, childMock, childChildMock), WrapUtil.toSortedSet(childMock, childChildMock), rootMock);
        checkAccessMethods(childMock, WrapUtil.toSortedSet(childMock, childChildMock), WrapUtil.toSortedSet(childChildMock), childMock);
        checkAccessMethods(childChildMock, WrapUtil.toSortedSet(childChildMock), new TreeSet(), childChildMock);
    }

    private void checkAccessMethods(Project project, SortedSet<DefaultProject> expectedAllProjects,
                                    SortedSet<DefaultProject> expectedSubProjects, Project expectedGetProject) {
        assertSame(expectedGetProject, projectRegistry.getProject(project.getPath()));
        assertEquals(expectedAllProjects, projectRegistry.getAllProjects(project.getPath()));
        assertEquals(expectedSubProjects, projectRegistry.getSubProjects(project.getPath()));
        assertEquals(expectedGetProject, projectRegistry.getProject(new File(project.getName())));
    }

    @Test(expected = InvalidUserDataException.class)
    public void addProjectWithExistingProjectDir() {
        Project duplicateProjectDirProject = childMock.addChildProject("childchild2", childMock.getProjectDir());
        projectRegistry.addProject(duplicateProjectDirProject);
    }

    @Test
    public void accessMethodsForNonExistingsPaths() {
        projectRegistry = new DefaultProjectRegistry();
        Project otherRoot = HelperUtil.createRootProject(new File("otherRoot"));
        checkAccessMethods(otherRoot, new TreeSet(), new TreeSet(), null);
    }
}
