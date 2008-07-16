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

package org.gradle;

import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory;
import org.gradle.api.internal.project.*;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.ProjectTasksPrettyPrinter;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.Dag;
import org.gradle.initialization.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.gradle.api.Project;
import org.gradle.api.InvalidUserDataException;
import org.gradle.configuration.ProjectDependencies2TaskResolver;
import org.gradle.util.GUtil;
import org.gradle.groovy.scripts.*;

import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * @author Hans Dockter
 */
public class Build {
    private static Logger logger = LoggerFactory.getLogger(Build.class);

    static CustomFactory customFactory = null;

    private RootFinder rootFinder;
    private SettingsProcessor settingsProcessor;
    private ProjectsLoader projectLoader;
    private BuildConfigurer buildConfigurer;
    private BuildExecuter buildExecuter;

    public Build() {
    }

    public Build(RootFinder rootFinder, SettingsProcessor settingsProcessor,
                 ProjectsLoader projectLoader, BuildConfigurer buildConfigurer, BuildExecuter buildExecuter) {
        this.rootFinder = rootFinder;
        this.settingsProcessor = settingsProcessor;
        this.projectLoader = projectLoader;
        this.buildConfigurer = buildConfigurer;
        this.buildExecuter = buildExecuter;
    }

    public void runNonRecursivelyWithCurrentDirAsRoot(StartParameter startParameter) {
        startParameter.setRecursive(false);
        runInternal(initWithCurrentDirAsRoot(startParameter), startParameter);
    }

    public void run(StartParameter startParameter) {
        DefaultSettings settings = init(startParameter);
        runInternal(settings, startParameter);
    }

    private void runInternal(DefaultSettings settings, StartParameter startParameter) {
        ClassLoader classLoader = settings.createClassLoader();
        Boolean rebuildDag = true;
        List<String> taskNames = startParameter.getTaskNames();
        int i = 0;
        do {
            if (rebuildDag) {
                projectLoader.load(settings, classLoader, startParameter, startParameter.getProjectProperties(),
                        getAllSystemProperties(), getAllEnvProperties());
                buildConfigurer.process(projectLoader.getRootProject());
            } else {
                logger.info("DAG must not be rebuild as the task chain before was dag neutral!");
            }
            if (i == 0) {
                if (taskNames.size() == 0) {
                    taskNames = projectLoader.getCurrentProject().getDefaultTasks();
                }
                if (!GUtil.isTrue(taskNames)) {
                    throw new InvalidUserDataException("No task names have been specified and the project has not defined any default tasks!");
                }
                List unknownTasks = buildExecuter.unknownTasks(taskNames, startParameter.isRecursive(),
                        projectLoader.getCurrentProject());
                if (GUtil.isTrue(unknownTasks)) {
                    throw new UnknownTaskException("Task(s) " + unknownTasks + " are unknown!");
                }
            }
            String taskName = taskNames.get(i);
            logger.info("++++ Starting build for primary task: " + taskName);
            boolean lastTask = isLastElement(taskNames, i);
            rebuildDag = buildExecuter.execute(taskName, startParameter.isRecursive(), projectLoader.getCurrentProject(),
                    projectLoader.getRootProject(), !lastTask);
        } while (++i < taskNames.size());
    }


    private boolean isLastElement(List<String> taskNames, int i) {
        return (i == taskNames.size() - 1);
    }

    public String taskListNonRecursivelyWithCurrentDirAsRoot(StartParameter startParameter) {
        StartParameter newStartParameter = StartParameter.newInstance(startParameter);
        newStartParameter.setRecursive(false);
        return taskListInternal(initWithCurrentDirAsRoot(newStartParameter), newStartParameter);
    }

    public String taskList(StartParameter startParameter) {
        return taskListInternal(init(startParameter), startParameter);
    }

    private String taskListInternal(DefaultSettings settings, StartParameter startParameter) {
        projectLoader.load(settings, settings.createClassLoader(), startParameter, startParameter.getProjectProperties(),
                getAllSystemProperties(), getAllEnvProperties());
        return buildConfigurer.taskList(projectLoader.getRootProject(), startParameter.isRecursive(), projectLoader.getCurrentProject());
    }

    private DefaultSettings init(StartParameter startParameter) {
        rootFinder.find(startParameter);
        setSystemProperties(startParameter.getSystemPropertiesArgs(), rootFinder);
        DefaultSettings settings = settingsProcessor.process(rootFinder, startParameter);
        return settings;
    }

    private DefaultSettings initWithCurrentDirAsRoot(StartParameter startParameter) {
        StartParameter startParameterArg = StartParameter.newInstance(startParameter);
        startParameterArg.setSearchUpwards(false);
        rootFinder.find(startParameter);
        setSystemProperties(startParameter.getSystemPropertiesArgs(), rootFinder);
        DefaultSettings settings = settingsProcessor.createBasicSettings(rootFinder, startParameter);
        return settings;
    }

    private void setSystemProperties(Map properties, RootFinder rootFinder) {
        System.getProperties().putAll(properties);
        addSystemPropertiesFromGradleProperties(rootFinder);
    }

    private void addSystemPropertiesFromGradleProperties(RootFinder rootFinder) {
        for (String key : rootFinder.getGradleProperties().keySet()) {
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                System.setProperty(key.substring((Project.SYSTEM_PROP_PREFIX + '.').length()), rootFinder.getGradleProperties().get(key));
            }
        }
    }

    private Map getAllSystemProperties() {
        return System.getProperties();
    }

    private Map getAllEnvProperties() {
        return System.getenv();
    }

    public static BuildFactory newInstanceFactory(final StartParameter startParameter) {
        if (customFactory != null) {
            return customFactory.newInstanceFactory(startParameter);
        }
        return new BuildFactory() {
            public Build newInstance(String inMemoryScriptText, File buildResolverDir) {
                DefaultDependencyManagerFactory dependencyManagerFactory = new DefaultDependencyManagerFactory();
                ImportsReader importsReader = new ImportsReader(startParameter.getDefaultImportsFile());
                DefaultScriptProcessor scriptProcessor = new DefaultScriptProcessor(new DefaultScriptHandler());
                Dag tasksGraph = new Dag();
                return new Build(
                        new RootFinder(),
                        new SettingsProcessor(
                                new DefaultSettingsScriptMetaData(),
                                scriptProcessor,
                                importsReader,
                                new SettingsFactory(),
                                dependencyManagerFactory,
                                null,
                                buildResolverDir),
                        new ProjectsLoader(
                                new ProjectFactory(
                                        new TaskFactory(tasksGraph),
                                        dependencyManagerFactory,
                                        new BuildScriptProcessor(
                                                scriptProcessor, 
                                                new DefaultProjectScriptMetaData(),
                                                importsReader,
                                                inMemoryScriptText,
                                                startParameter.getCacheUsage()),
                                        new PluginRegistry(
                                                startParameter.getPluginPropertiesFile()),
                                        startParameter.getBuildFileName(),
                                        new ProjectRegistry())

                        ),
                        new BuildConfigurer(
                                new ProjectDependencies2TaskResolver(),
                                new ProjectTasksPrettyPrinter()),
                        new BuildExecuter(tasksGraph
                        ));
            }
        };
    }

    // This is used for mocking
    public static void injectCustomFactory(CustomFactory customFactory) {
        Build.customFactory = customFactory;
    }

    public static interface BuildFactory {
        public Build newInstance(String inMemoryScriptText, File buildResolverDir);
    }

    public static interface CustomFactory {
        public BuildFactory newInstanceFactory(StartParameter startParameter);
    }

    public RootFinder getRootFinder() {
        return rootFinder;
    }

    public void setRootFinder(RootFinder rootFinder) {
        this.rootFinder = rootFinder;
    }

    public SettingsProcessor getSettingsProcessor() {
        return settingsProcessor;
    }

    public void setSettingsProcessor(SettingsProcessor settingsProcessor) {
        this.settingsProcessor = settingsProcessor;
    }

    public ProjectsLoader getProjectLoader() {
        return projectLoader;
    }

    public void setProjectLoader(ProjectsLoader projectLoader) {
        this.projectLoader = projectLoader;
    }

    public BuildConfigurer getBuildConfigurer() {
        return buildConfigurer;
    }

    public void setBuildConfigurer(BuildConfigurer buildConfigurer) {
        this.buildConfigurer = buildConfigurer;
    }

    public BuildExecuter getBuildExecuter() {
        return buildExecuter;
    }

    public void setBuildExecuter(BuildExecuter buildExecuter) {
        this.buildExecuter = buildExecuter;
    }
}