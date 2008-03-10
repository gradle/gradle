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

import org.gradle.api.Project
import org.gradle.api.internal.dependencies.DefaultDependencyManager
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.ProjectsLoader
import org.gradle.util.HelperUtil

/**
* @author Hans Dockter
*/
class ProjectsLoaderTest extends GroovyTestCase {
    static File TEST_ROOT_DIR = new File("/path/root")

    ProjectsLoader projectLoader
    ProjectFactory projectFactory
    BuildScriptProcessor buildScriptProcessor
    BuildScriptFinder buildScriptFinder
    PluginRegistry pluginRegistry

    void setUp() {
        projectFactory = new ProjectFactory(new DefaultDependencyManagerFactory())
        buildScriptProcessor = new BuildScriptProcessor()
        buildScriptFinder = new BuildScriptFinder()
        pluginRegistry = new PluginRegistry()
        projectLoader = new ProjectsLoader(projectFactory, buildScriptProcessor, buildScriptFinder, pluginRegistry)
    }

    void testProjectsLoader() {
        assertSame(projectFactory, projectLoader.projectFactory)
        assertSame(buildScriptProcessor, projectLoader.buildScriptProcessor)
        assertSame(buildScriptFinder, projectLoader.buildScriptFinder)
        assertSame(pluginRegistry, projectLoader.pluginRegistry)
    }

    void tearDown() {
        HelperUtil.deleteTestDir()
    }

    void testCreateProjects() {
        DefaultSettings settings = new DefaultSettings(new File(TEST_ROOT_DIR, 'parent'), TEST_ROOT_DIR, new DefaultDependencyManager())
        settings.include('parent' + Project.PATH_SEPARATOR + 'child1', 'parent' + Project.PATH_SEPARATOR + 'child2',
                'parent' + Project.PATH_SEPARATOR + 'folder' + Project.PATH_SEPARATOR + 'child3')
        File gradleUserHomeDir = HelperUtil.makeNewTestDir()
        Map testUserProps = [prop1: 'value1', prop2: 'value2']
        new Properties(testUserProps).store(new FileOutputStream(new File(gradleUserHomeDir, 'gradle.properties')), '')

        projectLoader.load(settings, gradleUserHomeDir)

        DefaultProject rootProject = projectLoader.rootProject
        assertSame(TEST_ROOT_DIR, rootProject.rootDir)
        assertEquals(Project.PATH_SEPARATOR, rootProject.path)
        assertEquals("$TEST_ROOT_DIR.name", rootProject.name)
        assertEquals 1, rootProject.childProjects.size()
        assertNotNull rootProject.childProjects.parent
        assertEquals 3, rootProject.childProjects.parent.childProjects.size()
        assertNotNull rootProject.childProjects.parent.childProjects.child1
        assertNotNull rootProject.childProjects.parent.childProjects.child2
        assertNotNull rootProject.childProjects.parent.childProjects.folder
        assertEquals 1, rootProject.childProjects.parent.childProjects.folder.childProjects.size()
        assertNotNull rootProject.childProjects.parent.childProjects.folder.childProjects.child3

        checkProperties(gradleUserHomeDir, testUserProps, rootProject, rootProject.childProjects.parent,
                rootProject.childProjects.parent.childProjects.child1,
                rootProject.childProjects.parent.childProjects.child2,
                rootProject.childProjects.parent.childProjects.folder,
                rootProject.childProjects.parent.childProjects.folder.childProjects.child3)
    }

    void testCreateProjectsWithonExistingGradleProperties() {
        DefaultSettings settings = new DefaultSettings(new File(TEST_ROOT_DIR, 'parent'), TEST_ROOT_DIR, new DefaultDependencyManager())
        settings.include('parent' + Project.PATH_SEPARATOR + 'child1', 'parent' + Project.PATH_SEPARATOR + 'child2',
                'parent' + Project.PATH_SEPARATOR + 'folder' + Project.PATH_SEPARATOR + 'child3')

        File nonExistingGradleUserHomeDir = new File('nonexistingGradleHome')
        projectLoader.load(settings, nonExistingGradleUserHomeDir)

        DefaultProject rootProject = projectLoader.rootProject
        assertSame(TEST_ROOT_DIR, rootProject.rootDir)
        assertEquals(Project.PATH_SEPARATOR, rootProject.path)
        assertEquals("$TEST_ROOT_DIR.name", rootProject.name)
        assertEquals 1, rootProject.childProjects.size()
        assertNotNull rootProject.childProjects.parent
        assertEquals 3, rootProject.childProjects.parent.childProjects.size()
        assertNotNull rootProject.childProjects.parent.childProjects.child1
        assertNotNull rootProject.childProjects.parent.childProjects.child2
        assertNotNull rootProject.childProjects.parent.childProjects.folder
        assertEquals 1, rootProject.childProjects.parent.childProjects.folder.childProjects.size()
        assertNotNull rootProject.childProjects.parent.childProjects.folder.childProjects.child3

        checkProperties(nonExistingGradleUserHomeDir, [:], rootProject, rootProject.childProjects.parent,
                rootProject.childProjects.parent.childProjects.child1,
                rootProject.childProjects.parent.childProjects.child2,
                rootProject.childProjects.parent.childProjects.folder,
                rootProject.childProjects.parent.childProjects.folder.childProjects.child3)
    }

    private void checkProperties(File gradleUserHomeDir, Map properties, Project[] projects) {
        projects.each {Project project ->
            assertEquals(gradleUserHomeDir.canonicalPath, project.gradleUserHome)
            properties.each {key, value -> assertEquals(project."$key", value)}
        }
    }

}