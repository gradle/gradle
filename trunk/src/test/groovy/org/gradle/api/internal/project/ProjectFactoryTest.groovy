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

package org.gradle.api.internal.project

import org.gradle.api.DependencyManager
import org.gradle.api.DependencyManagerFactory
import org.gradle.api.Project
import org.gradle.api.internal.dependencies.DefaultDependencyManager

/**
 * @author Hans Dockter
 */
class ProjectFactoryTest extends GroovyTestCase {
    void testProjectFactory() {
        DependencyManager dependencyManager = new DefaultDependencyManager()
        DependencyManagerFactory dependencyManagerFactory = [createDependencyManager: {dependencyManager}] as DependencyManagerFactory

        ProjectFactory projectFactory = new ProjectFactory(dependencyManagerFactory)
        assert projectFactory.dependencyManagerFactory.is(dependencyManagerFactory)

        String expectedName = 'somename'
        Project parentProject = new DefaultProject()
        File rootDir = '/root' as File
        Project rootProject = new DefaultProject()
        BuildScriptProcessor buildScriptProcessor = new BuildScriptProcessor()
        BuildScriptFinder buildScriptFinder = new BuildScriptFinder()
        PluginRegistry pluginRegistry = new PluginRegistry()

        Project project = projectFactory.createProject(expectedName, parentProject, rootDir, rootProject,
                projectFactory, buildScriptProcessor, buildScriptFinder, pluginRegistry)

        assert project.name.is(expectedName)
        assert project.parent.is(parentProject)
        assert project.rootDir.is(rootDir)
        assert project.rootProject.is(rootProject)
        assert project.projectFactory.is(projectFactory)
        assert project.dependencies.is(dependencyManager)
        assert project.buildScriptProcessor.is(buildScriptProcessor)
        assert project.buildScriptFinder.is(buildScriptFinder)
        assert project.pluginRegistry.is(pluginRegistry)
    }

}
