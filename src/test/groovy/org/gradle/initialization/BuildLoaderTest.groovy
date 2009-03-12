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
import org.gradle.api.Project
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.BuildInternal
import org.gradle.initialization.BuildLoader
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultProjectDescriptorRegistry
import org.gradle.initialization.IProjectDescriptorRegistry
import org.gradle.invocation.DefaultBuild
import org.gradle.util.GUtil
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.internal.project.*
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*
import org.gradle.api.InvalidUserDataException
import org.gradle.api.GradleException

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
    ClassLoader testClassLoader
    IProjectDescriptorRegistry projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
    StartParameter startParameter = new StartParameter()
    ProjectDescriptor rootDescriptor
    ProjectInternal rootProject
    ProjectDescriptor childDescriptor
    ProjectInternal childProject
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp()  {
        testClassLoader = new URLClassLoader([] as URL[])
        projectFactory = context.mock(IProjectFactory)
        buildLoader = new BuildLoader(projectFactory)
        testDir = HelperUtil.makeNewTestDir()
        (rootProjectDir = new File(testDir, 'root')).mkdirs()
        (childProjectDir = new File(rootProjectDir, 'child')).mkdirs()
        startParameter.currentDir = rootProjectDir
        rootDescriptor = descriptor('root', null, rootProjectDir)
        rootProject = project(rootDescriptor, null)
        childDescriptor = descriptor('child', rootDescriptor, childProjectDir)
        childProject = project(childDescriptor, rootProject)
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

        BuildInternal build = buildLoader.load(rootDescriptor, testClassLoader, startParameter, [:])

        assertThat(build.buildScriptClassLoader, sameInstance(testClassLoader))
        assertThat(build.startParameter, sameInstance(startParameter))
        assertThat(build.rootProject, sameInstance(rootProject))
        assertThat(build.defaultProject, sameInstance(rootProject))
    }

    @Test public void createsBuildWithMultipleProjects() {
        expectProjectsCreated()

        BuildInternal build = buildLoader.load(rootDescriptor, testClassLoader, startParameter, [:])

        assertThat(build.rootProject, sameInstance(rootProject))
        assertThat(build.defaultProject, sameInstance(rootProject))

        assertThat(build.rootProject.childProjects['child'], sameInstance(childProject))
    }

    @Test public void setsExternalPropertiesOnEachProject() {
        expectProjectsCreated()

        BuildInternal build = buildLoader.load(rootDescriptor, testClassLoader, startParameter, [buildDirName: 'target', prop: 'value'])

        assertThat(build.rootProject.buildDirName, equalTo('target'))
        assertThat(build.rootProject.prop, equalTo('value'))

        assertThat(build.rootProject.project('child').buildDirName, equalTo('target'))
        assertThat(build.rootProject.project('child').prop, equalTo('value'))
    }

    @Test public void setsProjectSpecificProperties() {
        GUtil.saveProperties(new Properties([buildDirName: 'target/root', prop: 'rootValue']), new File(rootProjectDir, Project.GRADLE_PROPERTIES))
        GUtil.saveProperties(new Properties([buildDirName: 'target/child', prop: 'childValue']), new File(childProjectDir, Project.GRADLE_PROPERTIES))

        expectProjectsCreated()

        BuildInternal build = buildLoader.load(rootDescriptor, testClassLoader, startParameter, [:])

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

        BuildInternal build = buildLoader.load(rootDescriptor, testClassLoader, startParameter, [:])
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
            buildLoader.load(rootDescriptor, testClassLoader, startParameter, [:])
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
            project = HelperUtil.createChildProject(parent, descriptor.name)
            project.projectDir = descriptor.projectDir
        } else {
            project = HelperUtil.createRootProject(descriptor.projectDir)
        }
        project
    }
}