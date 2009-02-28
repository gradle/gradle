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

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.artifacts.DependencyManagerFactory;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.util.HelperUtil;
import org.gradle.util.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
            allowing(dependencyManagerFactoryMock).createDependencyManager(with(any(Project.class)), with(any(File.class)));
            allowing(projectRegistry).addProject(with(any(ProjectInternal.class)));
            allowing(build).getProjectRegistry();
            will(returnValue(projectRegistry));
            allowing(build).getBuildScriptClassLoader();
            will(returnValue(buildScriptClassLoader));
            allowing(build).getGradleUserHomeDir();
            will(returnValue(new File("gradleUserHomeDir")));
        }});

        projectFactory = new ProjectFactory(taskFactoryMock, dependencyManagerFactoryMock, buildScriptProcessor, pluginRegistry,
                null, antBuilderFactory);
    }

    @Test
    public void testConstructsRootProjectWithBuildFile() throws IOException {
        File buildFile = new File(rootDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, "build");
        ProjectDescriptor descriptor = descriptor("somename", rootDir, buildFile, "build.gradle");

        DefaultProject project = projectFactory.createProject(descriptor, null, build);

        assertEquals("somename", project.getName());
        assertEquals(buildFile, project.getBuildFile());
        assertNull(project.getParent());
        assertSame(rootDir, project.getRootDir());
        assertSame(rootDir, project.getProjectDir());
        assertSame(project, project.getRootProject());
        checkProjectResources(project);

        ScriptSource expectedScriptSource = new FileScriptSource("build file", buildFile);
        assertThat(project.getBuildScriptSource(), Matchers.reflectionEquals(expectedScriptSource));
    }

    @Test
    public void testConstructsChildProjectWithBuildFile() throws IOException {
        File buildFile = new File(projectDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, "build");

        ProjectDescriptor rootDescriptor = descriptor("root");
        ProjectDescriptor parentDescriptor = descriptor("parent");
        ProjectDescriptor projectDescriptor = descriptor("somename", projectDir, buildFile, "build.gradle");

        DefaultProject rootProject = projectFactory.createProject(rootDescriptor, null, build);
        DefaultProject parentProject = projectFactory.createProject(parentDescriptor, rootProject, build);

        DefaultProject project = projectFactory.createProject(projectDescriptor, parentProject, build);

        assertEquals("somename", project.getName());
        assertEquals(buildFile, project.getBuildFile());
        assertSame(parentProject, project.getParent());
        assertSame(rootDir, project.getRootDir());
        assertSame(projectDir, project.getProjectDir());
        assertSame(rootProject, project.getRootProject());
        assertSame(project, parentProject.getChildProjects().get("somename"));
        checkProjectResources(project);

        ScriptSource expectedScriptSource = new FileScriptSource("build file", buildFile);
        assertThat(project.getBuildScriptSource(), Matchers.reflectionEquals(expectedScriptSource));
    }

    @Test
    public void testUsesEmptyBuildFileWhenBuildFileIsMissing() {

        DefaultProject rootProject = projectFactory.createProject(descriptor("root"), null, build);
        DefaultProject project = projectFactory.createProject(descriptor("somename", projectDir), rootProject, build);

        ScriptSource expectedScriptSource = new StringScriptSource("empty build file", "");
        assertThat(project.getBuildScriptSource(), Matchers.reflectionEquals(expectedScriptSource));
    }

    @Test
    public void testConstructsRootProjectWithEmbeddedBuildScript() {
        ScriptSource expectedScriptSource = context.mock(ScriptSource.class);

        ProjectFactory projectFactory = new ProjectFactory(taskFactoryMock, dependencyManagerFactoryMock, buildScriptProcessor, pluginRegistry,
                expectedScriptSource, antBuilderFactory);

        DefaultProject project = projectFactory.createProject(descriptor("somename"), null, build);

        assertEquals("somename", project.getName());
        assertEquals(new File(rootDir, "build.gradle"), project.getBuildFile());
        assertSame(rootDir, project.getRootDir());
        assertSame(rootDir, project.getProjectDir());
        assertNull(project.getParent());
        assertSame(project, project.getRootProject());
        checkProjectResources(project);
        assertSame(project.getBuildScriptSource(), expectedScriptSource);
    }

    private ProjectDescriptor descriptor(String name) {
        return descriptor(name, rootDir);
    }

    private ProjectDescriptor descriptor(String name, File projectDir) {
        return descriptor(name, projectDir, new File(projectDir, "build.gradle"), "build.gradle");
    }

    private ProjectDescriptor descriptor(final String name, final File projectDir, final File buildFile, final String buildFileName) {
        final ProjectDescriptor descriptor = context.mock(ProjectDescriptor.class, name);

        context.checking(new Expectations(){{
            allowing(descriptor).getName();
            will(returnValue(name));
            allowing(descriptor).getProjectDir();
            will(returnValue(projectDir));
            allowing(descriptor).getBuildFile();
            will(returnValue(buildFile));
            allowing(descriptor).getBuildFileName();
            will(returnValue(buildFileName));
        }});
        return descriptor;
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
