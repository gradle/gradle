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

package org.gradle.initialization

import org.gradle.StartParameter
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.InternalRepository
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.IProjectRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.util.GUtil
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.invocation.DefaultGradle

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
class BuildLoaderTest {

    BuildLoader buildLoader
    IProjectFactory projectFactory
    File testDir
    File rootProjectDir
    File childProjectDir
    IProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
    StartParameter startParameter = new StartParameter()
    ProjectDescriptor rootDescriptor
    ProjectInternal rootProject
    ProjectDescriptor childDescriptor
    ProjectInternal childProject
    InternalRepository internalRepository
    GradleInternal build
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp()  {
        projectFactory = context.mock(IProjectFactory)
        internalRepository = context.mock(InternalRepository)
        buildLoader = new BuildLoader(projectFactory)
        testDir = HelperUtil.makeNewTestDir()
        (rootProjectDir = new File(testDir, 'root')).mkdirs()
        (childProjectDir = new File(rootProjectDir, 'child')).mkdirs()
        startParameter.currentDir = rootProjectDir
        startParameter.pluginPropertiesFile = new File('plugin.properties')
        rootDescriptor = descriptor('root', null, rootProjectDir)
        rootProject = project(rootDescriptor, null)
        childDescriptor = descriptor('child', rootDescriptor, childProjectDir)
        childProject = project(childDescriptor, rootProject)
        build = new DefaultGradle(startParameter, internalRepository)
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
    }

    @Test public void createsBuildWithRootProject() {
        ProjectDescriptor rootDescriptor = descriptor('root', null, rootProjectDir)
        ProjectInternal rootProject = project(rootDescriptor, null)

        context.checking {
            one(projectFactory).createProject(withParam(equalTo(rootDescriptor)),
                    withParam(nullValue()),
                    withParam(notNullValue()))
            will(returnValue(rootProject))
        }

        buildLoader.load(rootDescriptor, build, [:])

        assertThat(build.startParameter, sameInstance(startParameter))
        assertThat(build.rootProject, sameInstance(rootProject))
        assertThat(build.defaultProject, sameInstance(rootProject))
        assertThat(build.internalRepository, sameInstance(internalRepository))
    }

    @Test public void createsBuildWithMultipleProjects() {
        expectProjectsCreated()

        buildLoader.load(rootDescriptor, build, [:])

        assertThat(build.rootProject, sameInstance(rootProject))
        assertThat(build.defaultProject, sameInstance(rootProject))

        assertThat(build.rootProject.childProjects['child'], sameInstance(childProject))
    }

    @Test public void setsExternalPropertiesOnEachProject() {
        expectProjectsCreated()

        buildLoader.load(rootDescriptor, build, [buildDirName: 'target', prop: 'value'])

        assertThat(build.rootProject.buildDirName, equalTo('target'))
        assertThat(build.rootProject.prop, equalTo('value'))

        assertThat(build.rootProject.project('child').buildDirName, equalTo('target'))
        assertThat(build.rootProject.project('child').prop, equalTo('value'))
    }

    @Test public void setsProjectSpecificProperties() {
        GUtil.saveProperties(new Properties([buildDirName: 'target/root', prop: 'rootValue']), new File(rootProjectDir, Project.GRADLE_PROPERTIES))
        GUtil.saveProperties(new Properties([buildDirName: 'target/child', prop: 'childValue']), new File(childProjectDir, Project.GRADLE_PROPERTIES))

        expectProjectsCreated()

        buildLoader.load(rootDescriptor, build, [:])

        assertThat(build.rootProject.buildDirName, equalTo('target/root'))
        assertThat(build.rootProject.prop, equalTo('rootValue'))

        assertThat(build.rootProject.project('child').buildDirName, equalTo('target/child'))
        assertThat(build.rootProject.project('child').prop, equalTo('childValue'))
    }

    @Test public void selectsDefaultProject() {
        expectProjectsCreated()

        ProjectSpec selector = context.mock(ProjectSpec)
        startParameter.defaultProjectSelector = selector
        context.checking {
            one(selector).selectProject(withParam(instanceOf(IProjectRegistry)))
            will(returnValue(childProject))
        }

        buildLoader.load(rootDescriptor, build, [:])
        assertThat(build.defaultProject, sameInstance(childProject))
    }

    @Test public void wrapsDefaultProjectSelectionException() {
        expectProjectsCreated()

        ProjectSpec selector = context.mock(ProjectSpec)
        startParameter.defaultProjectSelector = selector
        context.checking {
            one(selector).selectProject(withParam(instanceOf(IProjectRegistry)))
            will(throwException(new InvalidUserDataException("<error>")))
        }

        try {
            buildLoader.load(rootDescriptor, build, [:])
            fail()
        } catch (GradleException e) {
            assertThat(e.message, equalTo('Could not select the default project for this build. <error>'))
        }
    }

    private def expectProjectsCreated() {
        context.checking {
            one(projectFactory).createProject(withParam(equalTo(rootDescriptor)),
                    withParam(nullValue()),
                    withParam(notNullValue()))
            will(returnValue(rootProject))

            one(projectFactory).createProject(withParam(equalTo(childDescriptor)),
                    withParam(equalTo(rootProject)),
                    withParam(notNullValue()))
            will(returnValue(childProject))
        }
    }

    private ProjectDescriptor descriptor(String name, ProjectDescriptor parent, File projectDir) {
        new DefaultProjectDescriptor(parent, name, projectDir, projectDescriptorRegistry)
    }

    private ProjectInternal project(ProjectDescriptor descriptor, ProjectInternal parent) {
        DefaultProject project
        if (parent) {
            project = HelperUtil.createChildProject(parent, descriptor.name, descriptor.projectDir)
        } else {
            project = HelperUtil.createRootProject(descriptor.projectDir)
        }
        project
    }
}
