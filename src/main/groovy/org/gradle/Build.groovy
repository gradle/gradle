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
 
package org.gradle

import org.gradle.configuration.BuildConfigurer
import org.gradle.execution.BuildExecuter
import org.gradle.initialization.DefaultSettings
import org.gradle.initialization.ProjectsLoader
import org.gradle.initialization.SettingsProcessor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
* @author Hans Dockter
*/
class Build {
    private static Logger logger = LoggerFactory.getLogger(Build)

    SettingsProcessor settingsProcessor
    ProjectsLoader projectLoader
    BuildConfigurer buildConfigurer
    BuildExecuter buildExecuter

    Build(SettingsProcessor settingsProcessor, ProjectsLoader projectLoader, BuildConfigurer buildConfigurer,
          BuildExecuter buildExecuter) {
        this.settingsProcessor = settingsProcessor
        this.projectLoader = projectLoader
        this.buildConfigurer = buildConfigurer
        this.buildExecuter = buildExecuter
    }

    void run(List taskNames, File currentDir, File gradleUserHomeDir, String buildFileName, boolean recursive, boolean searchUpwards) {
        DefaultSettings settings = init(currentDir, gradleUserHomeDir, searchUpwards)
        buildConfigurer.process(projectLoader.rootProject, settings.createClassLoader())
        buildExecuter.execute(taskNames, recursive, projectLoader.currentProject, projectLoader.rootProject)
    }

    String taskList(File currentDir, File gradleUserHomeDir, String buildFileName, boolean recursive, boolean searchUpwards) {
        DefaultSettings settings = init(currentDir, gradleUserHomeDir, searchUpwards)
        buildConfigurer.taskList(projectLoader.rootProject, recursive, projectLoader.currentProject, settings.createClassLoader())
    }

    private DefaultSettings init(File currentDir, File gradleUserHomeDir, boolean searchUpwards) {
        DefaultSettings settings = settingsProcessor.process(currentDir, searchUpwards)
        projectLoader.load(settings, gradleUserHomeDir)
        settings
    }
}