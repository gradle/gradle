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

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import static org.gradle.util.ReflectionEqualsMatcher.*;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class ProjectFactoryTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private final ClassLoader buildScriptClassLoader = new URLClassLoader(new URL[0]);
    private final File rootDir = HelperUtil.makeNewTestDir();
    private final File projectDir = new File(rootDir, "project");
    private DependencyManagerFactory dependencyManagerFactoryMock;
    private ITaskFactory taskFactoryMock;
    private BuildScriptProcessor buildScriptProcessor;
    private PluginRegistry pluginRegistry;
    private IProjectRegistry projectRegistry;
    private BuildInternal build;
    private ProjectFactory projectFactory;
    private AntBuilderFactory antBuilderFactory;

    @Before
    public void setUp() throws Exception {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        dependencyManagerFactoryMock = context.mock(DependencyManagerFactory.class);
        taskFactoryMock = context.mock(ITaskFactory.class);
        buildScriptProcessor = context.mock(BuildScriptProcessor.class);
        pluginRegistry = context.mock(PluginRegistry.class);
        build = context.mock(BuildInternal.class);
        projectRegistry = context.mock(IProjectRegistry.class);
        antBuilderFactory = context.mock(AntBuilderFactory.class);

        context.checking(new Expectations() {{
            allowing(dependencyManagerFactoryMock).createDependencyManager(with(any(Project.class)));
            allowing(projectRegistry).addProject(with(any(Project.class)));
            allowing(build).getProjectRegistry();
            will(returnValue(projectRegistry));
            allowing(build).getBuildScriptClassLoader();
            will(returnValue(buildScriptClassLoader));
        }});

        projectFactory = new ProjectFactory(taskFactoryMock, dependencyManagerFactoryMock, buildScriptProcessor, pluginRegistry,
                new StartParameter(), null, antBuilderFactory);
    }

    @Test
    public void testConstructsRootProjectWithBuildFile() throws IOException {
        File buildFile = new File(rootDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, "build");

        DefaultProject project = projectFactory.createProject("somename", null, rootDir, build);

        assertEquals("somename", project.getName());
        assertEquals("build.gradle", project.getBuildFileName());
        assertNull(project.getParent());
        assertSame(rootDir, project.getRootDir());
        assertSame(rootDir, project.getProjectDir());
        assertSame(project, project.getRootProject());
        checkProjectResources(project);

        ScriptSource expectedScriptSource = new FileScriptSource("build file", buildFile);
        assertThat(project.getBuildScriptSource(), reflectionEquals(expectedScriptSource));
    }

    @Test
    public void testConstructsChildProjectWithBuildFile() throws IOException {
        File buildFile = new File(projectDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, "build");

        DefaultProject rootProject = projectFactory.createProject("root", null, rootDir, build);
        DefaultProject parentProject = projectFactory.createProject("parent", rootProject, rootDir, build);

        DefaultProject project = projectFactory.createProject("somename", parentProject, projectDir, build);

        assertEquals("somename", project.getName());
        assertEquals("build.gradle", project.getBuildFileName());
        assertSame(parentProject, project.getParent());
        assertSame(rootDir, project.getRootDir());
        assertSame(projectDir, project.getProjectDir());
        assertSame(rootProject, project.getRootProject());
        checkProjectResources(project);

        ScriptSource expectedScriptSource = new FileScriptSource("build file", buildFile);
        assertThat(project.getBuildScriptSource(), reflectionEquals(expectedScriptSource));
    }

    @Test
    public void testUsesEmptyBuildFileWhenBuildFileIsMissing() {

        DefaultProject rootProject = projectFactory.createProject("root", null, rootDir, build);
        DefaultProject project = projectFactory.createProject("somename", rootProject, projectDir, build);

        ScriptSource expectedScriptSource = new StringScriptSource("empty build file", "");
        assertThat(project.getBuildScriptSource(), reflectionEquals(expectedScriptSource));
    }

    @Test
    public void testConstructsRootProjectWithEmbeddedBuildScript() {
        ScriptSource expectedScriptSource = context.mock(ScriptSource.class);

        ProjectFactory projectFactory = new ProjectFactory(taskFactoryMock, dependencyManagerFactoryMock, buildScriptProcessor, pluginRegistry,
                new StartParameter(), expectedScriptSource, antBuilderFactory);

        DefaultProject project = projectFactory.createProject("somename", null, rootDir, build);

        assertEquals("somename", project.getName());
        assertEquals("build.gradle", project.getBuildFileName());
        assertSame(rootDir, project.getRootDir());
        assertSame(rootDir, project.getProjectDir());
        assertNull(project.getParent());
        assertSame(project, project.getRootProject());
        checkProjectResources(project);
        assertSame(project.getBuildScriptSource(), expectedScriptSource);
    }

    private void checkProjectResources(DefaultProject project) {
        assertSame(taskFactoryMock, project.getTaskFactory());
        assertSame(buildScriptClassLoader, project.getBuildScriptClassLoader());
        assertSame(dependencyManagerFactoryMock, project.getDependencyManagerFactory());
        assertSame(buildScriptProcessor, project.getBuildScriptProcessor());
        assertSame(pluginRegistry, project.getPluginRegistry());
        assertSame(projectRegistry, project.getProjectRegistry());
        assertSame(antBuilderFactory, project.getAntBuilderFactory());
        assertSame(build, project.getBuild());
    }
}
