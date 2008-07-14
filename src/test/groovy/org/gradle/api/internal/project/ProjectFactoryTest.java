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

package org.gradle.api.internal.project;

import org.gradle.api.DependencyManager;
import org.gradle.api.DependencyManagerFactory;
import org.gradle.api.Project;
import org.gradle.api.internal.dependencies.DefaultDependencyManager;
import org.gradle.util.HelperUtil;
import static org.junit.Assert.*;
import org.junit.Test;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;

import java.net.URLClassLoader;
import java.net.URL;
import java.io.File;

/**
 * @author Hans Dockter
 */
public class ProjectFactoryTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    @Test public void testProjectFactory() {
        final DependencyManagerFactory dependencyManagerFactoryMock = context.mock(DependencyManagerFactory.class);
        DependencyManager dependencyManagerMock = context.mock(DependencyManager.class);
        context.checking(new Expectations() {{
          allowing(dependencyManagerFactoryMock).createDependencyManager(with(any(Project.class)));
        }});

        ITaskFactory taskFactoryMock = context.mock(ITaskFactory.class);

        String expectedName = "somename";
        String expectedBuildFileName = "build.gradle";
        File rootDir = new File("/root");
        DefaultProject rootProject = HelperUtil.createRootProject(rootDir);
        Project parentProject = rootProject.addChildProject("parent");
        BuildScriptProcessor buildScriptProcessor = new BuildScriptProcessor();
        PluginRegistry expectedPluginRegistry = new PluginRegistry();

        ProjectRegistry expectedProjectRegistry = rootProject.getProjectRegistry();
        ClassLoader expectedBuildScriptClassLoader = new URLClassLoader(new URL[0]);

        ProjectFactory projectFactory = new ProjectFactory(taskFactoryMock, dependencyManagerFactoryMock, buildScriptProcessor, expectedPluginRegistry,
                expectedBuildFileName, expectedProjectRegistry);
        DefaultProject project = projectFactory.createProject(expectedName, parentProject, rootDir, rootProject, expectedBuildScriptClassLoader);

        assertEquals(expectedName, project.getName());
        assertEquals(expectedBuildFileName, project.getBuildFileName());
        assertSame(parentProject, project.getParent());
        assertSame(rootDir, project.getRootDir());
        assertSame(rootProject, project.getRootProject());
        assertSame(taskFactoryMock, project.getTaskFactory());
        assertSame(expectedBuildScriptClassLoader, project.getBuildScriptClassLoader());
        assertSame(dependencyManagerFactoryMock, project.getDependencyManagerFactory());
        assertSame(buildScriptProcessor, project.getBuildScriptProcessor());
        assertSame(expectedPluginRegistry, project.getPluginRegistry());
        assertSame(expectedProjectRegistry, project.getProjectRegistry());
    }

}
