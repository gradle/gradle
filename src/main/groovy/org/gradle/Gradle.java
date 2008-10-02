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

import org.gradle.api.internal.BuildInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.dependencies.DefaultDependencyManagerFactory;
import org.gradle.api.internal.dependencies.DependencyManagerFactory;
import org.gradle.api.internal.project.BuildScriptProcessor;
import org.gradle.api.internal.project.DefaultAntBuilderFactory;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.PluginRegistry;
import org.gradle.api.internal.project.ProjectFactory;
import org.gradle.api.internal.project.TaskFactory;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.ProjectDependencies2TaskResolver;
import org.gradle.configuration.ProjectTasksPrettyPrinter;
import org.gradle.execution.TaskExecuter;
import org.gradle.groovy.scripts.DefaultProjectScriptMetaData;
import org.gradle.groovy.scripts.DefaultScriptHandler;
import org.gradle.groovy.scripts.DefaultScriptProcessor;
import org.gradle.groovy.scripts.DefaultSettingsScriptMetaData;
import org.gradle.groovy.scripts.IScriptProcessor;
import org.gradle.initialization.BuildSourceBuilder;
import org.gradle.initialization.DefaultGradlePropertiesLoader;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.initialization.DefaultSettingsFinder;
import org.gradle.initialization.EmbeddedBuildExecuter;
import org.gradle.initialization.EmbeddedScriptSettingsFinder;
import org.gradle.initialization.IGradlePropertiesLoader;
import org.gradle.initialization.ISettingsFileSearchStrategy;
import org.gradle.initialization.ISettingsFinder;
import org.gradle.initialization.MasterDirSettingsFinderStrategy;
import org.gradle.initialization.ParentDirSettingsFinderStrategy;
import org.gradle.initialization.ProjectsLoader;
import org.gradle.initialization.ScriptEvaluatingSettingsProcessor;
import org.gradle.initialization.ScriptLocatingSettingsProcessor;
import org.gradle.initialization.SettingsFactory;
import org.gradle.initialization.SettingsProcessor;
import org.gradle.logging.AntLoggingAdapter;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>{@code Gradle} is the main entry point for embedding Gradle. You use this class to manage a Gradle build, as
 * follows:</p>
 *
 * <ul>
 *
 * <li>Obtain a {@code Gradle} instance by calling {@link #newInstance}, passing in a {@link StartParameter} configured
 * appropriately.  The properties of {@code StartParameter} generally correspond to the command-line options of
 * Gradle.</li>
 *
 * <li>Add one or more {@link BuildListener}s to receive events as the build executes by calling {@link
 * #addBuildListener}.</li>
 *
 * <li>Call {@link #run} to execute the build. This will return a {@link BuildResult}</li>
 *
 * <li>Query the build result. You might want to call {@link BuildResult#rethrowFailure()} to rethrow any build
 * failure.</li>
 *
 * </ul>
 *
 * @author Hans Dockter
 */
public class Gradle {
    private static Logger logger = LoggerFactory.getLogger(Gradle.class);

    private static GradleFactory factory = new DefaultGradleFactory();

    private StartParameter startParameter;
    private ISettingsFinder settingsFinder;
    private IGradlePropertiesLoader gradlePropertiesLoader;
    private SettingsProcessor settingsProcessor;
    private ProjectsLoader projectLoader;
    private BuildConfigurer buildConfigurer;

    private final List<BuildListener> buildListeners = new ArrayList<BuildListener>();

    public Gradle(StartParameter startParameter, ISettingsFinder settingsFinder,
                  IGradlePropertiesLoader gradlePropertiesLoader, SettingsProcessor settingsProcessor,
                  ProjectsLoader projectLoader, BuildConfigurer buildConfigurer) {
        this.startParameter = startParameter;
        this.settingsFinder = settingsFinder;
        this.gradlePropertiesLoader = gradlePropertiesLoader;
        this.settingsProcessor = settingsProcessor;
        this.projectLoader = projectLoader;
        this.buildConfigurer = buildConfigurer;
    }

    /**
     * <p>Executes the build for this Gradle instance and returns the result. Note that when the build fails, the
     * exception is available using {@link BuildResult#getFailure()}.</p>
     *
     * @return The result. Never returns null.
     */
    public BuildResult run() {
        fireBuildStarted(startParameter);

        SettingsInternal settings = null;
        Throwable failure = null;
        try {
            settings = init(startParameter);
            runInternal(settings, startParameter);
        } catch (Throwable t) {
            failure = t;
        }

        BuildResult buildResult = new BuildResult(settings, failure);
        fireBuildFinished(buildResult);

        return buildResult;
    }

    private void fireBuildStarted(StartParameter startParameter) {
        for (BuildListener buildListener : buildListeners) {
            buildListener.buildStarted(startParameter);
        }
    }

    private void fireBuildFinished(BuildResult buildResult) {
        for (BuildListener buildListener : buildListeners) {
            buildListener.buildFinished(buildResult);
        }
    }

    private void runInternal(SettingsInternal settings, StartParameter startParameter) {
        ClassLoader classLoader = settings.createClassLoader();
        Boolean rebuildDag = true;
        BuildInternal build = null;
        TaskExecuter executer = startParameter.getTaskExecuter();
        while (executer.hasNext()) {
            if (rebuildDag) {
                build = projectLoader.load(settings.getRootProjectDescriptor(), classLoader,
                        startParameter,
                        gradlePropertiesLoader.getGradleProperties());
                buildConfigurer.process(build.getRootProject());
            } else {
                logger.info("DAG must not be rebuild as the task chain before was dag neutral!");
            }
            executer.select(build.getCurrentProject());
            logger.info(String.format("++++ Starting build for %s.", executer.getDescription()));
            executer.execute(build.getTaskGraph());
            rebuildDag = executer.requiresProjectReload();
        }
    }

    private SettingsInternal init(StartParameter startParameter) {
        return settingsProcessor.process(settingsFinder, startParameter, gradlePropertiesLoader);
    }

    public static Gradle newInstance(final StartParameter startParameter) {
        return factory.newInstance(startParameter);
    }

    // This is used for mocking
    public static void injectCustomFactory(GradleFactory gradleFactory) {
        factory = gradleFactory == null ? new DefaultGradleFactory() : gradleFactory;
    }

    public static interface GradleFactory {
        public Gradle newInstance(StartParameter startParameter);
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

    public List<BuildListener> getBuildListeners() {
        return buildListeners;
    }

    /**
     * <p>Adds a {@link BuildListener} to this Gradle instance. The listener is notified of events which occur during a
     * build.</p>
     *
     * @param buildListener The listener to add.
     */
    public void addBuildListener(BuildListener buildListener) {
        buildListeners.add(buildListener);
    }

    private static class DefaultGradleFactory implements GradleFactory {
        public Gradle newInstance(StartParameter startParameter) {
            DependencyManagerFactory dependencyManagerFactory = new DefaultDependencyManagerFactory();
            ImportsReader importsReader = new ImportsReader(startParameter.getDefaultImportsFile());
            IScriptProcessor scriptProcessor = new DefaultScriptProcessor(new DefaultScriptHandler(),
                    startParameter.getCacheUsage());
            File buildResolverDir = startParameter.getBuildResolverDir();
            ISettingsFinder settingsFinder = startParameter.getSettingsScriptSource() == null
                    ? new DefaultSettingsFinder(WrapUtil.<ISettingsFileSearchStrategy>toList(
                    new MasterDirSettingsFinderStrategy(),
                    new ParentDirSettingsFinderStrategy()))
                    : new EmbeddedScriptSettingsFinder();
            Gradle gradle = new Gradle(
                    startParameter,
                    settingsFinder,
                    new DefaultGradlePropertiesLoader(),
                    new ScriptLocatingSettingsProcessor(
                            new ScriptEvaluatingSettingsProcessor(
                                    new DefaultSettingsScriptMetaData(),
                                    scriptProcessor,
                                    importsReader,
                                    new SettingsFactory(
                                            new DefaultProjectDescriptorRegistry(),
                                            dependencyManagerFactory,
                                            new BuildSourceBuilder(new EmbeddedBuildExecuter(this))),
                                    dependencyManagerFactory,
                                    buildResolverDir)
                    ),
                    new ProjectsLoader(
                            new ProjectFactory(
                                    new TaskFactory(),
                                    dependencyManagerFactory,
                                    new BuildScriptProcessor(
                                            scriptProcessor,
                                            new DefaultProjectScriptMetaData(),
                                            importsReader
                                    ),
                                    new PluginRegistry(
                                            startParameter.getPluginPropertiesFile()),
                                    startParameter,
                                    startParameter.getBuildScriptSource(),
                                    new DefaultAntBuilderFactory(new AntLoggingAdapter()))
                    ),
                    new BuildConfigurer(
                            new ProjectDependencies2TaskResolver(),
                            new ProjectTasksPrettyPrinter()));

            if (buildResolverDir == null) {
                gradle.addBuildListener(new BuildCleanupListener());
            }
            return gradle;
        }
    }
}
