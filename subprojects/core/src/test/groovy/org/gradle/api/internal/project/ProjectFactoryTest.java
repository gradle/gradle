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

import org.apache.commons.io.FileUtils;
import org.gradle.StartParameter;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.internal.Factory;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.testfixtures.internal.GlobalTestServices;
import org.gradle.testfixtures.internal.TestTopLevelBuildServiceRegistry;
import org.gradle.util.JUnit4GroovyMockery;
import org.gradle.util.MultiParentClassLoader;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class ProjectFactoryTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final MultiParentClassLoader buildScriptClassLoader = new MultiParentClassLoader(getClass().getClassLoader());
    @Rule
    public TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider();
    private final File rootDir = testDir.getTestDirectory();
    private final File projectDir = new File(rootDir, "project");
    private Factory<RepositoryHandler> repositoryHandlerFactory = context.mock(Factory.class);
    private RepositoryHandler repositoryHandler = context.mock(RepositoryHandler.class);
    private StartParameter startParameterStub = new StartParameter();
    private ServiceRegistryFactory serviceRegistryFactory = new TestTopLevelBuildServiceRegistry(new GlobalTestServices(), startParameterStub, rootDir);
    private org.gradle.internal.reflect.Instantiator instantiatorMock = serviceRegistryFactory.get(org.gradle.internal.reflect.Instantiator.class);
    private GradleInternal gradle = context.mock(GradleInternal.class);

    private ProjectFactory projectFactory;

    @Before
    public void setUp() throws Exception {
        startParameterStub.setGradleUserHomeDir(testDir.createDir("home"));
        context.checking(new Expectations() {{
            allowing(repositoryHandlerFactory).create();
            will(returnValue(repositoryHandler));
        }});
        final ServiceRegistryFactory gradleServices = serviceRegistryFactory.createFor(gradle);
        context.checking(new Expectations() {{
            allowing(gradle).getServices();
            will(returnValue(gradleServices));
            allowing(gradle).getStartParameter();
            will(returnValue(startParameterStub));
            allowing(gradle).getProjectRegistry();
            will(returnValue(gradleServices.get(IProjectRegistry.class)));
            allowing(gradle).getScriptClassLoader();
            will(returnValue(buildScriptClassLoader));
            allowing(gradle).getGradleUserHomeDir();
            will(returnValue(new File("gradleUserHomeDir")));
            ignoring(gradle).getProjectEvaluationBroadcaster();
        }});

        projectFactory = new ProjectFactory(instantiatorMock);
    }

    @Test
    public void testConstructsRootProjectWithBuildFile() throws IOException {
        File buildFile = new File(rootDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, "build");
        ProjectDescriptor descriptor = descriptor("somename", rootDir, buildFile, "build.gradle");

        DefaultProject project = projectFactory.createProject(descriptor, null, gradle);

        assertEquals("somename", project.getName());
        assertEquals(buildFile, project.getBuildFile());
        assertNull(project.getParent());
        assertSame(rootDir, project.getRootDir());
        assertSame(rootDir, project.getProjectDir());
        assertSame(project, project.getRootProject());
        checkProjectResources(project);

        assertThat(project.getBuildScriptSource(), instanceOf(UriScriptSource.class));
        assertThat(project.getBuildScriptSource().getDisplayName(), startsWith("build file "));
        assertThat(project.getBuildScriptSource().getResource().getFile(), equalTo(buildFile));
    }

    @Test
    public void testConstructsChildProjectWithBuildFile() throws IOException {
        File buildFile = new File(projectDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, "build");

        ProjectDescriptor rootDescriptor = descriptor("root");
        ProjectDescriptor parentDescriptor = descriptor("parent");
        ProjectDescriptor projectDescriptor = descriptor("somename", projectDir, buildFile, "build.gradle");

        DefaultProject rootProject = projectFactory.createProject(rootDescriptor, null, gradle);
        DefaultProject parentProject = projectFactory.createProject(parentDescriptor, rootProject, gradle);

        DefaultProject project = projectFactory.createProject(projectDescriptor, parentProject, gradle);

        assertEquals("somename", project.getName());
        assertEquals(buildFile, project.getBuildFile());
        assertSame(parentProject, project.getParent());
        assertSame(rootDir, project.getRootDir());
        assertSame(projectDir, project.getProjectDir());
        assertSame(rootProject, project.getRootProject());
        assertSame(project, parentProject.getChildProjects().get("somename"));
        checkProjectResources(project);

        assertThat(project.getBuildScriptSource(), instanceOf(UriScriptSource.class));
        assertThat(project.getBuildScriptSource().getDisplayName(), startsWith("build file "));
        assertThat(project.getBuildScriptSource().getResource().getFile(), equalTo(buildFile));
    }

    @Test
    public void testAddsProjectToProjectRegistry() throws IOException {
        ProjectDescriptor rootDescriptor = descriptor("root");
        ProjectDescriptor parentDescriptor = descriptor("somename");

        DefaultProject rootProject = projectFactory.createProject(rootDescriptor, null, gradle);
        DefaultProject project = projectFactory.createProject(parentDescriptor, rootProject, gradle);

        assertThat(gradle.getProjectRegistry().getProject(":somename"), sameInstance((ProjectIdentifier) project));
    }

    @Test
    public void testUsesEmptyBuildFileWhenBuildFileIsMissing() {

        DefaultProject rootProject = projectFactory.createProject(descriptor("root"), null, gradle);
        DefaultProject project = projectFactory.createProject(descriptor("somename", projectDir), rootProject, gradle);

        assertThat(project.getBuildScriptSource(), instanceOf(StringScriptSource.class));
        assertThat(project.getBuildScriptSource().getDisplayName(), equalTo("empty build file"));
        assertThat(project.getBuildScriptSource().getResource().getText(), equalTo(""));
    }

    @Test
    public void testConstructsRootProjectWithEmbeddedBuildScript() {


        ProjectFactory projectFactory = new ProjectFactory(instantiatorMock);

        DefaultProject project = projectFactory.createProject(descriptor("somename"), null, gradle);

        assertEquals("somename", project.getName());
        assertSame(rootDir, project.getRootDir());
        assertSame(rootDir, project.getProjectDir());
        assertNull(project.getParent());
        assertSame(project, project.getRootProject());
        assertNotNull(project.getConvention());
        checkProjectResources(project);

    }

    private ProjectDescriptor descriptor(String name) {
        return descriptor(name, rootDir);
    }

    private ProjectDescriptor descriptor(String name, File projectDir) {
        return descriptor(name, projectDir, new File(projectDir, "build.gradle"), "build.gradle");
    }

    private ProjectDescriptor descriptor(final String name, final File projectDir, final File buildFile,
                                         final String buildFileName) {
        final ProjectDescriptor descriptor = context.mock(ProjectDescriptor.class, name);

        context.checking(new Expectations() {{
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
        assertSame(gradle, project.getGradle());
    }
}
