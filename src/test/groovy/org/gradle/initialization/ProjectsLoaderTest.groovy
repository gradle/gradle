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
import org.gradle.api.internal.project.BuildScriptProcessor
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.IProjectRegistry
import org.gradle.api.internal.project.PluginRegistry
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.ProjectsLoader
import org.gradle.util.HelperUtil
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.integration.junit4.JMock
import org.junit.After
import static org.junit.Assert.*
import static org.hamcrest.Matchers.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.internal.BuildInternal
import org.gradle.invocation.DefaultBuild

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
class ProjectsLoaderTest {
    ProjectsLoader projectsLoader
    IProjectFactory projectFactory
    BuildScriptProcessor buildScriptProcessor
    PluginRegistry pluginRegistry
    File testDir
    File testRootProjectDir
    File testParentProjectDir
    ClassLoader testClassLoader
    Map testProjectProperties
    File testUserHomeDir
    DefaultProjectDescriptorRegistry projectDescriptorRegistry
    JUnit4GroovyMockery context = new JUnit4GroovyMockery()

    @Before public void setUp()  {
        testClassLoader = new URLClassLoader([] as URL[])
        testProjectProperties = [startProp1: 'startPropValue1', startProp2: 'startPropValue2']
        projectFactory = context.mock(IProjectFactory)
        buildScriptProcessor = new BuildScriptProcessor()
        pluginRegistry = new PluginRegistry()
        projectsLoader = new ProjectsLoader(projectFactory, context.mock(TaskExecutionGraph))
        testDir = HelperUtil.makeNewTestDir()
        (testRootProjectDir = new File(testDir, 'root')).mkdirs()
        (testParentProjectDir = new File(testRootProjectDir, 'parent')).mkdirs()
        testUserHomeDir = 'someUserHome' as File
        projectDescriptorRegistry = new DefaultProjectDescriptorRegistry()
    }


    @Test public void testProjectsLoader() {
        assertSame(projectFactory, projectsLoader.projectFactory)
    }

    @After
    public void tearDown() {
        HelperUtil.deleteTestDir()
    }

    @Test public void testCreateProjects() {
        ProjectInternal rootProject = context.mock(ProjectInternal, 'root')
        ProjectInternal parentProject = context.mock(ProjectInternal, 'parent')
        ProjectInternal child1 = context.mock(ProjectInternal, 'child1')
        ProjectInternal child2 = context.mock(ProjectInternal, 'child2')
        IProjectRegistry projectRegistry = context.mock(IProjectRegistry)

        StartParameter startParameter = new StartParameter(
                currentDir: new File(testRootProjectDir, 'parent'),
                gradleUserHomeDir: testUserHomeDir)
        DefaultProjectDescriptor rootProjectDescriptor = new DefaultProjectDescriptor(null, 'root', testRootProjectDir, projectDescriptorRegistry)
        DefaultProjectDescriptor parentProjectDescriptor = new DefaultProjectDescriptor(rootProjectDescriptor, 'parent',
                testParentProjectDir, projectDescriptorRegistry)
        DefaultProjectDescriptor child1ProjectDescriptor = new DefaultProjectDescriptor(parentProjectDescriptor, 'child1', new File('child1'), projectDescriptorRegistry)
        DefaultProjectDescriptor child2ProjectDescriptor = new DefaultProjectDescriptor(parentProjectDescriptor, 'child2', new File('child2'), projectDescriptorRegistry)

        Map testExternalProps = [prop1: 'value1', prop2: 'value2', prop3: 'value3']
        Map testRootProjectProps = [rootProp1: 'rootValue1', rootProp2: 'rootValue2', prop1: 'rootValue']
        Map testParentProjectProps = [parentProp1: 'parentValue1', parentProp2: 'parentValue2', prop1: 'parentValue']

        new Properties(testRootProjectProps).store(new FileOutputStream(new File(testRootProjectDir, Project.GRADLE_PROPERTIES)), '')
        new Properties(testParentProjectProps).store(new FileOutputStream(new File(testParentProjectDir, Project.GRADLE_PROPERTIES)), '')

        context.checking {
            allowing(rootProject).getProjectDir()
            will(returnValue(testRootProjectDir))
            allowing(parentProject).getProjectDir()
            will(returnValue(parentProjectDescriptor.dir))
            allowing(child1).getProjectDir()
            will(returnValue(child1ProjectDescriptor.dir))
            allowing(child2).getProjectDir()
            will(returnValue(child2ProjectDescriptor.dir))

            one(projectFactory).createProject(withParam(equalTo(rootProjectDescriptor.getName())),
                    withParam(nullValue()),
                    withParam(equalTo(testRootProjectDir)),
                    withParam(notNullValue()))
            will(returnValue(rootProject))

            one(rootProject).setGradleUserHome(testUserHomeDir.canonicalPath)

            one(rootProject).setProperty('rootProp1', 'rootValue1')
            one(rootProject).setProperty('rootProp2', 'rootValue2')
            one(rootProject).setProperty('prop1', 'value1')
            one(rootProject).setProperty('prop2', 'value2')
            one(rootProject).setProperty('prop3', 'value3')

            one(rootProject).addChildProject('parent', parentProjectDescriptor.dir)
            will(returnValue(parentProject))

            one(parentProject).setGradleUserHome(testUserHomeDir.canonicalPath)

            one(parentProject).setProperty('parentProp1', 'parentValue1')
            one(parentProject).setProperty('parentProp2', 'parentValue2')
            one(parentProject).setProperty('prop1', 'value1')
            one(parentProject).setProperty('prop2', 'value2')
            one(parentProject).setProperty('prop3', 'value3')

            one(parentProject).addChildProject('child1', child1ProjectDescriptor.dir)
            will(returnValue(child1))

            one(child1).setProperty('prop1', 'value1')
            one(child1).setProperty('prop2', 'value2')
            one(child1).setProperty('prop3', 'value3')

            one(child1).setGradleUserHome(testUserHomeDir.canonicalPath)

            one(parentProject).addChildProject('child2', child2ProjectDescriptor.dir)
            will(returnValue(child2))

            one(child2).setGradleUserHome(testUserHomeDir.canonicalPath)

            one(child2).setProperty('prop1', 'value1')
            one(child2).setProperty('prop2', 'value2')
            one(child2).setProperty('prop3', 'value3')

            one(rootProject).getProjectRegistry()
            will(returnValue(projectRegistry))

            one(projectRegistry).getProject(startParameter.currentDir)
            will(returnValue(child2))
        }

        BuildInternal build = projectsLoader.load(rootProjectDescriptor, testClassLoader, startParameter, testExternalProps)
        assertTrue(build.class.equals(DefaultBuild))
        assertThat(build.rootProject, sameInstance(rootProject))
        assertThat(build.currentProject, sameInstance(child2))
    }
}