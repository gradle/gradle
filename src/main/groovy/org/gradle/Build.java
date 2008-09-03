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

import org.gradle.api.Project;
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory;
import org.gradle.api.internal.project.*;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.ProjectDependencies2TaskResolver;
import org.gradle.configuration.ProjectTasksPrettyPrinter;
import org.gradle.execution.*;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class Build {
    private static Logger logger = LoggerFactory.getLogger(Build.class);

    static CustomFactory customFactory = null;

    private ISettingsFinder settingsFinder;
    private IGradlePropertiesLoader gradlePropertiesLoader;
    private SettingsProcessor settingsProcessor;
    private ProjectsLoader projectLoader;
    private BuildConfigurer buildConfigurer;
    private BuildExecuter buildExecuter;

    private List<BuildListener> buildListeners = new ArrayList<BuildListener>();

    public Build() {
    }

    public Build(ISettingsFinder settingsFinder, IGradlePropertiesLoader gradlePropertiesLoader, SettingsProcessor settingsProcessor,
                 ProjectsLoader projectLoader, BuildConfigurer buildConfigurer, BuildExecuter buildExecuter) {
        this.settingsFinder = settingsFinder;
        this.gradlePropertiesLoader = gradlePropertiesLoader;
        this.settingsProcessor = settingsProcessor;
        this.projectLoader = projectLoader;
        this.buildConfigurer = buildConfigurer;
        this.buildExecuter = buildExecuter;
    }

    public void runNonRecursivelyWithCurrentDirAsRoot(StartParameter startParameter) {
        runInternal(initWithCurrentDirAsRoot(startParameter), startParameter);
    }

    public void run(StartParameter startParameter) {
        DefaultSettings settings = init(startParameter);
        runInternal(settings, startParameter);
    }

    private void runInternal(DefaultSettings settings, StartParameter startParameter) {
        RuntimeException failure = null;
        try {
            ClassLoader classLoader = settings.createClassLoader();
            Boolean rebuildDag = true;
            List<String> taskNames = startParameter.getTaskNames();
            TaskSelector selector = taskNames.size() == 0
                    ? new ProjectDefaultsTaskSelector()
                    : new NameResolvingTaskSelector(taskNames);
            while (selector.hasNext()) {
                if (rebuildDag) {
                    projectLoader.load(settings, classLoader, startParameter, gradlePropertiesLoader.getGradleProperties(), 
                            getAllSystemProperties(), getAllEnvProperties());
                    buildConfigurer.process(projectLoader.getRootProject());
                } else {
                    logger.info("DAG must not be rebuild as the task chain before was dag neutral!");
                }
                selector.select(projectLoader.getCurrentProject());
                logger.info(String.format("++++ Starting build for %s.", selector.getDescription()));
                logger.debug(String.format("Selected for execution: %s.", selector.getTasks()));
                rebuildDag = buildExecuter.execute(selector.getTasks(), projectLoader.getRootProject());
            }
        } catch (RuntimeException t) {
            failure = t;
        }
        for (BuildListener buildListener : buildListeners) {
            buildListener.buildFinished(new BuildResult(settings, failure));
        }

        if (failure != null) {
            throw failure;
        }
    }

    public String taskListNonRecursivelyWithCurrentDirAsRoot(StartParameter startParameter) {
        StartParameter newStartParameter = StartParameter.newInstance(startParameter);
        return taskListInternal(initWithCurrentDirAsRoot(newStartParameter), newStartParameter);
    }

    public String taskList(StartParameter startParameter) {
        return taskListInternal(init(startParameter), startParameter);
    }

    private String taskListInternal(DefaultSettings settings, StartParameter startParameter) {
        projectLoader.load(settings, settings.createClassLoader(), startParameter, gradlePropertiesLoader.getGradleProperties(),
                getAllSystemProperties(), getAllEnvProperties());
        return buildConfigurer.taskList(projectLoader.getRootProject(), true, projectLoader.getCurrentProject());
    }

    private DefaultSettings init(StartParameter startParameter) {
        initInternal(startParameter);
        return settingsProcessor.process(settingsFinder, startParameter);
    }

    private DefaultSettings initWithCurrentDirAsRoot(StartParameter startParameter) {
        startParameter.setSearchUpwards(false);
        initInternal(startParameter);
        return settingsProcessor.createBasicSettings(settingsFinder, startParameter);
    }

    private void initInternal(StartParameter startParameter) {
        settingsFinder.find(startParameter);
        gradlePropertiesLoader.loadProperties(settingsFinder.getSettingsDir(), startParameter);
        setSystemProperties(startParameter.getSystemPropertiesArgs());
    }

    private void setSystemProperties(Map properties) {
        System.getProperties().putAll(properties);
        addSystemPropertiesFromGradleProperties();
    }

    private void addSystemPropertiesFromGradleProperties() {
        for (String key : gradlePropertiesLoader.getGradleProperties().keySet()) {
            if (key.startsWith(Project.SYSTEM_PROP_PREFIX + '.')) {
                System.setProperty(key.substring((Project.SYSTEM_PROP_PREFIX + '.').length()), gradlePropertiesLoader.getGradleProperties().get(key));
            }
        }
    }

    private Map getAllSystemProperties() {
        return System.getProperties();
    }

    private Map getAllEnvProperties() {
        // The reason why we have an try-catch block here is for JDK 1.4 compatibility. We use the retrotranslator to produce
        // a 1.4 compatible version. But the retrotranslator is not capable of translating System.getenv to 1.4.
        // The System.getenv call is only available in 1.5. In fact 1.4 does not offer any API to read
        // environment variables. Therefore this call leads to an exception when used with 1.4. We ignore the exception in this
        // case and simply return an empty hashmap.
        try {
            return System.getenv();
        } catch (Throwable e) {
            logger.debug("The System.getenv() call has lead to an exception. Probably you are running on Java 1.4.", e);
            return new HashMap();
        }
    }

    public static BuildFactory newInstanceFactory(final StartParameter startParameter) {
        if (customFactory != null) {
            return customFactory.newInstanceFactory(startParameter);
        }
        return new BuildFactory() {
            public Build newInstance(String inMemoryScriptText, File buildResolverDir) {
                DefaultDependencyManagerFactory dependencyManagerFactory = new DefaultDependencyManagerFactory();
                ImportsReader importsReader = new ImportsReader(startParameter.getDefaultImportsFile());
                IScriptProcessor scriptProcessor = new DefaultScriptProcessor(new DefaultScriptHandler(), startParameter.getCacheUsage());
                Dag tasksGraph = new Dag();
                Build build = new Build(
                        new ParentDirSettingsFinder(),
                        new DefaultGradlePropertiesLoader(),
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
                                                importsReader
                                        ),
                                        new PluginRegistry(
                                                startParameter.getPluginPropertiesFile()),
                                        startParameter.getBuildFileName(),
                                        new ProjectRegistry(),
                                        inMemoryScriptText)

                        ),
                        new BuildConfigurer(
                                new ProjectDependencies2TaskResolver(),
                                new ProjectTasksPrettyPrinter()),
                        new BuildExecuter(tasksGraph
                        ));
                if (buildResolverDir == null) {
                    build.addBuildListener(new DefaultBuildListener());
                }
                return build;
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

    public ISettingsFinder getSettingsFinder() {
        return settingsFinder;
    }

    public void setSettingsFinder(ISettingsFinder settingsFinder) {
        this.settingsFinder = settingsFinder;
    }

    public IGradlePropertiesLoader getGradlePropertiesLoader() {
        return gradlePropertiesLoader;
    }

    public void setGradlePropertiesLoader(IGradlePropertiesLoader gradlePropertiesLoader) {
        this.gradlePropertiesLoader = gradlePropertiesLoader;
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

    public List<BuildListener> getBuildListeners() {
        return buildListeners;
    }

    protected void setBuildListeners(List<BuildListener> buildListeners) {
        this.buildListeners = buildListeners;
    }

    public void addBuildListener(BuildListener buildListener) {
        buildListeners.add(buildListener);
    }

    // In a future release Gradle will have a listener framework. Then this class will be replaced by a standard
    // listener class.
    public static class Cleaner {
        public void clean(File buildResolverDir) {
            GFileUtils.deleteDirectory(buildResolverDir);
        }
    }
}
