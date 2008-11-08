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
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.initialization.IGradlePropertiesLoader;
import org.gradle.initialization.ISettingsFinder;
import org.gradle.initialization.BuildLoader;
import org.gradle.initialization.SettingsProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private BuildLoader projectLoader;
    private BuildConfigurer buildConfigurer;

    private final List<BuildListener> buildListeners = new ArrayList<BuildListener>();

    public Gradle(StartParameter startParameter, ISettingsFinder settingsFinder,
                  IGradlePropertiesLoader gradlePropertiesLoader, SettingsProcessor settingsProcessor,
                  BuildLoader projectLoader, BuildConfigurer buildConfigurer) {
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

    private void runInternal(SettingsInternal settings, StartParameter startParameter) {
        ClassLoader classLoader = settings.createClassLoader();
        Boolean rebuildDag = true;
        BuildInternal build = null;
        BuildExecuter executer = startParameter.getBuildExecuter();
        while (executer.hasNext()) {
            if (rebuildDag) {
                build = projectLoader.load(settings.getRootProject(), classLoader,
                        startParameter,
                        gradlePropertiesLoader.getGradleProperties());
                fireProjectsLoaded(build);
                buildConfigurer.process(build.getRootProject());
                fireProjectsEvaluated(build);
                attachTaskGraphListener(build);
            } else {
                logger.info("DAG must not be rebuild as the task chain before was dag neutral!");
            }
            executer.select(build.getCurrentProject());
            logger.info(String.format("++++ Starting build for %s.", executer.getDescription()));
            executer.execute(build.getTaskGraph());
            rebuildDag = executer.requiresProjectReload();
        }
    }

    private void attachTaskGraphListener(BuildInternal build) {
        build.getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            public void graphPopulated(TaskExecutionGraph graph) {
                fireTaskGraphPrepared(graph);
            }
        });
    }

    private SettingsInternal init(StartParameter startParameter) {
        SettingsInternal settings = settingsProcessor.process(settingsFinder, startParameter, gradlePropertiesLoader);
        fireSettingsEvaluated(settings);
        return settings;
    }

    public static Gradle newInstance(final StartParameter startParameter) {
        return factory.newInstance(startParameter);
    }

    private void fireBuildStarted(StartParameter startParameter) {
        for (BuildListener buildListener : buildListeners) {
            buildListener.buildStarted(startParameter);
        }
    }

    private void fireSettingsEvaluated(SettingsInternal settings) {
        for (BuildListener listener : buildListeners) {
            listener.settingsEvaluated(settings);
        }
    }

    private void fireTaskGraphPrepared(TaskExecutionGraph graph) {
        for (BuildListener listener : buildListeners) {
            listener.taskGraphPopulated(graph);
        }
    }

    private void fireProjectsLoaded(BuildInternal build) {
        for (BuildListener listener : buildListeners) {
            listener.projectsLoaded(build);
        }
    }

    private void fireProjectsEvaluated(BuildInternal build) {
        for (BuildListener listener : buildListeners) {
            listener.projectsEvaluated(build);
        }
    }

    private void fireBuildFinished(BuildResult buildResult) {
        for (BuildListener buildListener : buildListeners) {
            buildListener.buildFinished(buildResult);
        }
    }

    // This is used for mocking
    public static void injectCustomFactory(GradleFactory gradleFactory) {
        factory = gradleFactory == null ? new DefaultGradleFactory() : gradleFactory;
    }

    public StartParameter getStartParameter() {
        return startParameter;
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

    public BuildLoader getProjectLoader() {
        return projectLoader;
    }

    public void setProjectLoader(BuildLoader projectLoader) {
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

}
