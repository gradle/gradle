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
import org.gradle.util.Clock

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

    Build() {}

    Build(RootFinder rootFinder, SettingsProcessor settingsProcessor,
          ProjectsLoader projectLoader, BuildConfigurer buildConfigurer, BuildExecuter buildExecuter) {
        this.rootFinder = rootFinder
        this.settingsProcessor = settingsProcessor
        this.projectLoader = projectLoader
        this.buildConfigurer = buildConfigurer
        this.buildExecuter = buildExecuter
    }

    void runNonRecursivelyWithCurrentDirAsRoot(StartParameter startParameter) {
        startParameter.recursive = false
        runInternal(initWithCurrentDirAsRoot(startParameter), startParameter)
    }

    void run(StartParameter startParameter) {
        DefaultSettings settings = init(startParameter)
        runInternal(settings, startParameter)
    }

    private void runInternal(DefaultSettings settings, StartParameter startParameter) {
        ClassLoader classLoader = settings.createClassLoader()
        boolean unknownTaskCheck = false
        startParameter.taskNames.each {String taskName ->
            logger.info("++++ Starting build for primary task: $taskName")
            projectLoader.load(settings, startParameter.gradleUserHomeDir, startParameter.projectProperties, allSystemProperties, allEnvProperties)
            buildConfigurer.process(projectLoader.rootProject, classLoader)
            if (!unknownTaskCheck) {
                List unknownTasks = buildExecuter.unknownTasks(startParameter.taskNames, startParameter.recursive, projectLoader.currentProject)
                if (unknownTasks) {throw new UnknownTaskException("Task(s) $unknownTasks are unknown!")}
                unknownTaskCheck = true
            }
            buildExecuter.execute(taskName, startParameter.recursive, projectLoader.currentProject, projectLoader.rootProject)
        }
    }

    String taskListNonRecursivelyWithCurrentDirAsRoot(StartParameter startParameter) {
        StartParameter newStartParameter = StartParameter.newInstance(startParameter, recursive: false)
        taskListInternal(initWithCurrentDirAsRoot(newStartParameter), newStartParameter)
    }

    String taskList(StartParameter startParameter) {
        taskListInternal(init(startParameter), startParameter)
    }

    private String taskListInternal(DefaultSettings settings, StartParameter startParameter) {
        projectLoader.load(settings, startParameter.gradleUserHomeDir, startParameter.projectProperties, allSystemProperties, allEnvProperties)
        buildConfigurer.taskList(projectLoader.rootProject, startParameter.recursive, projectLoader.currentProject, settings.createClassLoader())
    }

    private DefaultSettings init(StartParameter startParameter) {
        rootFinder.find(startParameter)
        setSystemProperties(startParameter.systemPropertiesArgs, rootFinder)
        DefaultSettings settings = settingsProcessor.process(rootFinder, startParameter)
        settings
    }

    private DefaultSettings initWithCurrentDirAsRoot(StartParameter startParameter) {
        StartParameter startParameterArg = StartParameter.newInstance(startParameter, searchUpwards: false)
        rootFinder.find(startParameter)
        setSystemProperties(startParameter.systemPropertiesArgs, rootFinder)
        DefaultSettings settings = settingsProcessor.createBasicSettings(rootFinder, startParameter)
        settings
    }

    private void setSystemProperties(Map properties, RootFinder rootFinder) {
        System.properties.putAll(properties)
        addSystemPropertiesFromGradleProperties(rootFinder)
    }

    private void addSystemPropertiesFromGradleProperties(RootFinder rootFinder) {
        rootFinder.gradleProperties.each {String key, value ->
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                System.properties[key.substring((Project.SYSTEM_PROP_PREFIX + '.').length())] = value
            }
        }
    }

    private Map getAllSystemProperties() {
        System.properties
    }

    private Map getAllEnvProperties() {
        System.getenv()
    }

    static Closure newInstanceFactory(File pluginProperties, File defaultImportsFile) {
        {BuildScriptFinder buildScriptFinder, File buildResolverDir ->
            DefaultDependencyManagerFactory dependencyManagerFactory = new DefaultDependencyManagerFactory()
            new Build(new RootFinder(),
                    new SettingsProcessor(new ImportsReader(defaultImportsFile),
                            new SettingsFactory(),
                            dependencyManagerFactory,
                            null, buildResolverDir),
                    new ProjectsLoader(new ProjectFactory(dependencyManagerFactory),
                            new BuildScriptProcessor(new ImportsReader(defaultImportsFile)),
                            buildScriptFinder, new PluginRegistry(pluginProperties)),
                    new BuildConfigurer(new ProjectDependencies2TasksResolver(), new BuildClasspathLoader(), new ProjectsTraverser(),
                            new ProjectTasksPrettyPrinter()),
                    new BuildExecuter(new Dag()))
        }
    }

}