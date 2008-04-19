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

import org.gradle.api.UnknownTaskException
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory
import org.gradle.api.internal.project.*
import org.gradle.configuration.BuildClasspathLoader
import org.gradle.configuration.BuildConfigurer
import org.gradle.configuration.ProjectDependencies2TasksResolver
import org.gradle.configuration.ProjectTasksPrettyPrinter
import org.gradle.execution.BuildExecuter
import org.gradle.execution.Dag
import org.gradle.initialization.*
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
    File gradleUserHomeDir

    Build() {}

    Build(File gradleUserHomeDir, SettingsProcessor settingsProcessor, ProjectsLoader projectLoader, BuildConfigurer buildConfigurer,
          BuildExecuter buildExecuter) {
        this.gradleUserHomeDir = gradleUserHomeDir
        this.settingsProcessor = settingsProcessor
        this.projectLoader = projectLoader
        this.buildConfigurer = buildConfigurer
        this.buildExecuter = buildExecuter
    }

    void run(List taskNames, File currentDir, Map projectProperties, Map systemProperties) {
        runInternal(init(currentDir, projectProperties, systemProperties), taskNames, currentDir, false,
                projectProperties, systemProperties)
    }

    void run(List taskNames, File currentDir, boolean recursive, boolean searchUpwards, Map projectProperties, Map systemProperties) {
        DefaultSettings settings = init(currentDir, searchUpwards, projectProperties, systemProperties)
        runInternal(settings, taskNames, currentDir, recursive, projectProperties, systemProperties)
    }

    private void runInternal(DefaultSettings settings, List taskNames, File currentDir, boolean recursive,
                             Map projectProperties, Map systemProperties) {
        ClassLoader classLoader = settings.createClassLoader()
        boolean unknownTaskCheck = false
        taskNames.each {String taskName ->
            logger.info("++++ Starting build for primary task: $taskName")
            projectLoader.load(settings, gradleUserHomeDir, projectProperties, systemProperties)
            buildConfigurer.process(projectLoader.rootProject, classLoader)
            if (!unknownTaskCheck) {
                List unknownTasks = buildExecuter.unknownTasks(taskNames, recursive, projectLoader.currentProject)
                if (unknownTasks) {throw new UnknownTaskException("Task(s) $unknownTasks are unknown!")}
                unknownTaskCheck = true
            }
            buildExecuter.execute(taskName, recursive, projectLoader.currentProject, projectLoader.rootProject)
        }
    }

    String taskList(File currentDir, Map projectProperties, Map systemProperties) {
        taskListInternal(init(currentDir, projectProperties, systemProperties), currentDir, false, projectProperties, systemProperties)
    }

    String taskList(File currentDir, boolean recursive, boolean searchUpwards, Map projectProperties, Map systemProperties) {
        taskListInternal(init(currentDir, searchUpwards, projectProperties, systemProperties), currentDir,
                recursive, projectProperties, systemProperties)
    }

    private String taskListInternal(DefaultSettings settings, File currentDir, boolean recursive, Map projectProperties, Map systemProperties) {
        projectLoader.load(settings, gradleUserHomeDir, projectProperties, systemProperties)
        buildConfigurer.taskList(projectLoader.rootProject, recursive, projectLoader.currentProject, settings.createClassLoader())
    }

    private DefaultSettings init(File currentDir, boolean searchUpwards, Map projectProperties, Map systemProperties) {
        setSystemProperties(systemProperties)
        DefaultSettings settings = settingsProcessor.process(currentDir, searchUpwards)
        settings
    }

    private DefaultSettings init(File currentDir, Map projectProperties, Map systemProperties) {
        setSystemProperties(systemProperties)
        DefaultSettings settings = settingsProcessor.createBasicSettings(currentDir)
        settings
    }

    private void setSystemProperties(Map properties) {
        System.properties.putAll(properties)
    }

    static Closure newInstanceFactory(File gradleUserHomeDir, File pluginProperties, File defaultImportsFile) {
        {BuildScriptFinder buildScriptFinder, File buildResolverDir ->
            DefaultDependencyManagerFactory dependencyManagerFactory = new DefaultDependencyManagerFactory()
            new Build(gradleUserHomeDir, new SettingsProcessor(
                    new SettingsFileHandler(),
                    new ImportsReader(defaultImportsFile),
                    new SettingsFactory(),
                    dependencyManagerFactory,
                    null, gradleUserHomeDir, buildResolverDir),
                    new ProjectsLoader(new ProjectFactory(dependencyManagerFactory),
                            new BuildScriptProcessor(new ImportsReader(defaultImportsFile)),
                            buildScriptFinder, new PluginRegistry(pluginProperties)),
                    new BuildConfigurer(new ProjectDependencies2TasksResolver(), new BuildClasspathLoader(), new ProjectsTraverser(),
                            new ProjectTasksPrettyPrinter()),
                    new BuildExecuter(new Dag()))
        }
    }

}