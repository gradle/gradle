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
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.ProjectsLoader
import org.gradle.util.HelperUtil
import org.junit.After
import static org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.util.JUnit4GroovyMockery
import org.jmock.lib.legacy.ClassImposteriser

/**
 * @author Hans Dockter
 */
class ProjectsLoaderTest {
    ProjectsLoader projectsLoader
    ProjectFactory projectFactory
    BuildScriptProcessor buildScriptProcessor
    PluginRegistry pluginRegistry
    File testDir
    File testRootProjectDir
    File testParentProjectDir
    ClassLoader testClassLoader
    Map testProjectProperties
    File testUserHomeDir
    DefaultProjectDescriptorRegistry projectDescriptorRegistry



    @Before public void setUp()  {
        testClassLoader = new URLClassLoader([] as URL[])
        testProjectProperties = [startProp1: 'startPropValue1', startProp2: 'startPropValue2']
        projectFactory = new ProjectFactory(new TaskFactory(), new DefaultDependencyManagerFactory(new File('root')), null, null, "build.gradle", new DefaultProjectRegistry(), null)
        buildScriptProcessor = new BuildScriptProcessor()
        pluginRegistry = new PluginRegistry()
        projectsLoader = new ProjectsLoader(projectFactory)
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
        StartParameter startParameter = new StartParameter(
                currentDir: new File(testRootProjectDir, 'parent'),
                gradleUserHomeDir: testUserHomeDir)
        DefaultProjectDescriptor rootProjectDescriptor = new DefaultProjectDescriptor(null, 'root', testRootProjectDir, projectDescriptorRegistry)
        DefaultProjectDescriptor parentProjectDescriptor = new DefaultProjectDescriptor(rootProjectDescriptor, 'parent',
                testParentProjectDir, projectDescriptorRegistry)
        new DefaultProjectDescriptor(parentProjectDescriptor, 'child1', new File('child1'), projectDescriptorRegistry)
        new DefaultProjectDescriptor(parentProjectDescriptor, 'child2', new File('child2'), projectDescriptorRegistry)

        Map testExternalProps = [prop1: 'value1', prop2: 'value2', prop3: 'value3']
        Map testRootProjectProps = [rootProp1: 'rootValue1', rootProp2: 'rootValue2', prop1: 'rootValue']
        Map testParentProjectProps = [parentProp1: 'parentValue1', parentProp2: 'parentValue2', prop1: 'parentValue']

        new Properties(testRootProjectProps).store(new FileOutputStream(new File(testRootProjectDir, Project.GRADLE_PROPERTIES)), '')
        new Properties(testParentProjectProps).store(new FileOutputStream(new File(testParentProjectDir, Project.GRADLE_PROPERTIES)), '')

        projectsLoader.load(rootProjectDescriptor, testClassLoader, startParameter, testExternalProps)

        ProjectInternal rootProject = projectsLoader.rootProject
        assert rootProject.buildScriptClassLoader.is(testClassLoader)
        assertSame(testRootProjectDir.getAbsolutePath(), rootProject.rootDir.getAbsolutePath())
        assertEquals(Project.PATH_SEPARATOR, rootProject.path)
        assertEquals(rootProjectDescriptor.getName(), rootProject.name)
        assertEquals 1, rootProject.childProjects.size()
        assertNotNull rootProject.childProjects.parent
        assertEquals 2, rootProject.childProjects.parent.childProjects.size()
        assertNotNull rootProject.childProjects.parent.childProjects.child1
        assertNotNull rootProject.childProjects.parent.childProjects.child2
        
        checkProjects(testExternalProps, ['prop2', 'prop3'], rootProject, rootProject.childProjects.parent,
                rootProject.childProjects.parent.childProjects.child1,
                rootProject.childProjects.parent.childProjects.child2)
        checkProjectProperties(testRootProjectProps, rootProject, ['prop1'])
        checkProjectProperties(testParentProjectProps, rootProject.childProjects.parent, ['prop1'])
        assertNull(rootProject.childProjects.parent.additionalProperties[new ArrayList(testProjectProperties.keySet())[0]])
    }

    private void checkProjects(Map properties, List excludeKeys, Project[] projects) {
        projects.each {Project project ->
            assertEquals(testUserHomeDir.canonicalPath, project.gradleUserHome)
            checkProjectProperties(properties, project, excludeKeys)
        }
    }

    private checkProjectProperties(Map properties, Project project, List excludeKeys = []) {
        properties.each {key, value ->
            if (excludeKeys.contains(key)) {return}
            assertEquals(project."$key", value)
        }
    }

    @Test public void testReset() {
        JUnit4GroovyMockery context = new JUnit4GroovyMockery()
        context.setImposteriser(ClassImposteriser.INSTANCE)
        ProjectFactory projectFactoryMock = context.mock(ProjectFactory)
        projectsLoader.setProjectFactory(projectFactoryMock)
        context.checking {
            one(projectFactoryMock).reset()
        }
        projectsLoader.reset()
        context.assertIsSatisfied()
    }

}