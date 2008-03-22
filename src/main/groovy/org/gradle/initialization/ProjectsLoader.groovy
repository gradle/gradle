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
import org.gradle.api.internal.project.*
import org.gradle.initialization.DefaultSettings
import org.gradle.util.PathHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class ProjectsLoader {
    Logger logger = LoggerFactory.getLogger(ProjectsLoader)

    static final String GRADLE_PROPERTIES = 'gradle.properties'

    ProjectFactory projectFactory

    DefaultProject rootProject

    DefaultProject currentProject

    BuildScriptProcessor buildScriptProcessor

    BuildScriptFinder buildScriptFinder

    PluginRegistry pluginRegistry

    ProjectsLoader() {

    }

    ProjectsLoader(ProjectFactory projectFactory, BuildScriptProcessor buildScriptProcessor,
                   BuildScriptFinder buildScriptFinder, PluginRegistry pluginRegistry) {
        this.projectFactory = projectFactory
        this.buildScriptProcessor = buildScriptProcessor
        this.buildScriptFinder = buildScriptFinder
        this.pluginRegistry = pluginRegistry
    }

    ProjectsLoader load(DefaultSettings settings, File gradleUserHomeDir, Map startProperties) {
        rootProject = createProjects(settings, gradleUserHomeDir, startProperties)
        currentProject = DefaultProject.findProject(rootProject, PathHelper.getCurrentProjectPath(rootProject.rootDir, settings.currentDir))
        this
    }

    private DefaultProject createProjects(DefaultSettings settings, File gradleUserHomeDir, Map startProperties) {
        assert startProperties != null
                
        logger.debug("Creating the projects and evaluating the project files!")
        File propertyFile = new File(gradleUserHomeDir, GRADLE_PROPERTIES)
        Properties userHomeProperties = new Properties()
        logger.debug("Looking for user properties from: $propertyFile")
        if (!propertyFile.isFile()) {
            logger.debug('user property file does not exists. We continue!')
        } else {
            userHomeProperties.load(new FileInputStream(propertyFile))
            logger.debug("Adding user properties: $userHomeProperties")
        }
        DefaultProject rootProject = projectFactory.createProject(settings.rootDir.name, null, settings.rootDir, null, projectFactory, buildScriptProcessor, buildScriptFinder, pluginRegistry)
        addPropertiesToProject(gradleUserHomeDir, userHomeProperties + startProperties, rootProject)
        settings.projectPaths.each {
            List folders = it.split(Project.PATH_SEPARATOR)
            DefaultProject parent = rootProject
            folders.each {name ->
                if (!parent.childProjects[name]) {
                    parent.childProjects[name] = parent.addChildProject(name)
                    addPropertiesToProject(gradleUserHomeDir, userHomeProperties, parent.childProjects[name])
                }
                parent = parent.childProjects[name]
            }
        }
        rootProject
    }

    private addPropertiesToProject(File gradleUserHomeDir, Map userProperties, Project project) {
        Properties projectProperties = new Properties()
        File projectPropertiesFile = new File(project.projectDir, GRADLE_PROPERTIES)
        logger.debug("Looking for project properties from: $projectPropertiesFile")
        if (projectPropertiesFile.isFile()) {
            projectProperties.load(new FileInputStream(projectPropertiesFile))
            logger.debug("Adding project properties (if not overwritten by user properties): $projectProperties")
        } else {
            logger.debug('project property file does not exists. We continue!')
        }
        projectProperties.putAll(userProperties)
        project.gradleUserHome = gradleUserHomeDir.canonicalPath
        projectProperties.each {key, value ->
            project."$key" = value
        }
    }

}
