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
import org.gradle.api.Project

/**
 * @author Hans Dockter
 */
class Build {
    private static Logger logger = LoggerFactory.getLogger(Build)

    RootFinder rootFinder
    SettingsProcessor settingsProcessor
    ProjectsLoader projectLoader
    BuildConfigurer buildConfigurer
    BuildExecuter buildExecuter
    File gradleUserHomeDir

    Build() {}

    Build(File gradleUserHomeDir, RootFinder rootFinder, SettingsProcessor settingsProcessor,
          ProjectsLoader projectLoader, BuildConfigurer buildConfigurer, BuildExecuter buildExecuter) {
        this.gradleUserHomeDir = gradleUserHomeDir
        this.rootFinder = rootFinder
        this.settingsProcessor = settingsProcessor
        this.projectLoader = projectLoader
        this.buildConfigurer = buildConfigurer
        this.buildExecuter = buildExecuter
    }

    void run(List taskNames, File currentDir, Map projectProperties, Map systemPropertiesArgs) {
        runInternal(init(currentDir, projectProperties, systemPropertiesArgs), taskNames, currentDir, false,
                projectProperties)
    }

    void run(List taskNames, File currentDir, boolean recursive, boolean searchUpwards, Map projectProperties, Map systemPropertiesArgs) {
        DefaultSettings settings = init(currentDir, searchUpwards, projectProperties, systemPropertiesArgs)
        runInternal(settings, taskNames, currentDir, recursive, projectProperties)
    }

    private void runInternal(DefaultSettings settings, List taskNames, File currentDir, boolean recursive,
                             Map projectProperties) {
        ClassLoader classLoader = settings.createClassLoader()
        boolean unknownTaskCheck = false
        taskNames.each {String taskName ->
            logger.info("++++ Starting build for primary task: $taskName")
            projectLoader.load(settings, gradleUserHomeDir, projectProperties, allSystemProperties, allEnvProperties)
            buildConfigurer.process(projectLoader.rootProject, classLoader)
            if (!unknownTaskCheck) {
                List unknownTasks = buildExecuter.unknownTasks(taskNames, recursive, projectLoader.currentProject)
                if (unknownTasks) {throw new UnknownTaskException("Task(s) $unknownTasks are unknown!")}
                unknownTaskCheck = true
            }
            buildExecuter.execute(taskName, recursive, projectLoader.currentProject, projectLoader.rootProject)
        }
    }

    String taskList(File currentDir, Map projectProperties, Map systemPropertiesArgs) {
        taskListInternal(init(currentDir, projectProperties, systemPropertiesArgs), currentDir, false, projectProperties)
    }

    String taskList(File currentDir, boolean recursive, boolean searchUpwards, Map projectProperties, Map systemPropertiesArgs) {
        taskListInternal(init(currentDir, searchUpwards, projectProperties, systemPropertiesArgs), currentDir,
                recursive, projectProperties)
    }

    private String taskListInternal(DefaultSettings settings, File currentDir, boolean recursive, Map projectProperties) {
        projectLoader.load(settings, gradleUserHomeDir, projectProperties, allSystemProperties, allEnvProperties)
        buildConfigurer.taskList(projectLoader.rootProject, recursive, projectLoader.currentProject, settings.createClassLoader())
    }

    private DefaultSettings init(File currentDir, boolean searchUpwards, Map projectProperties, Map systemProperties) {
        rootFinder.find(currentDir, searchUpwards)
        setSystemProperties(systemProperties, rootFinder)
        DefaultSettings settings = settingsProcessor.process(rootFinder)
        settings
    }

    private DefaultSettings init(File currentDir, Map projectProperties, Map systemProperties) {
        rootFinder.find(currentDir, false)
        setSystemProperties(systemProperties, rootFinder)
        DefaultSettings settings = settingsProcessor.createBasicSettings(rootFinder)
        settings
    }

    private void setSystemProperties(Map properties, RootFinder rootFinder) {
        System.properties.putAll(properties)
        addSystemPropertiesFromGradleProperties(rootFinder)
    }

    private void addSystemPropertiesFromGradleProperties(RootFinder rootFinder) {
        Closure addSystemProps = { String key, value ->
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                System.properties[key.substring((Project.SYSTEM_PROP_PREFIX + '.').length())] = value
            }
        }
        [new File(rootFinder.rootDir, Project.GRADLE_PROPERTIES),
                new File(gradleUserHomeDir, Project.GRADLE_PROPERTIES)].each { File propertyFile ->
            if (propertyFile.isFile()) {
                Properties gradleProperties = new Properties()
                gradleProperties.load(new FileInputStream(propertyFile))
                gradleProperties.each(addSystemProps)
            }
        }
    }

    private Map getAllSystemProperties() {
        System.properties
    }

    private Map getAllEnvProperties() {
        System.getenv()
    }

    static Closure newInstanceFactory(File gradleUserHomeDir, File pluginProperties, File defaultImportsFile) {
        {BuildScriptFinder buildScriptFinder, File buildResolverDir ->
            DefaultDependencyManagerFactory dependencyManagerFactory = new DefaultDependencyManagerFactory()
            new Build(gradleUserHomeDir, new RootFinder(), new SettingsProcessor(
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