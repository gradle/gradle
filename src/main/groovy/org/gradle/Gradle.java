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
import org.gradle.initialization.*;
import org.gradle.util.ListenerBroadcast;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>{@code Gradle} is the main entry point for embedding Gradle. You use this class to manage a Gradle build, as
 * follows:</p>
 *
 * <ol>
 *
 * <li>Create a {@link StartParameter} instance and configure it with the desired properties. The properties of {@code
 * StartParameter} generally correspond to the command-line options of Gradle.</li>
 *
 * <li>Obtain a {@code Gradle} instance by calling {@link #newInstance}, passing in the {@code StartParameter}.</li>
 *
 * <li>Optionally, add one or more {@link BuildListener}s to receive events as the build executes by calling {@link
 * #addBuildListener}.</li>
 *
 * <li>Call {@link #run} to execute the build. This will return a {@link BuildResult}. Note that if the build fails, the
 * resulting exception will be contained in the {@code BuildResult}.</li>
 *
 * <li>Query the build result. You might want to call {@link BuildResult#rethrowFailure()} to rethrow any build
 * failure.</li>
 *
 * </ol>
 *
 * @author Hans Dockter
 */
public class Gradle {
    private static Logger logger = LoggerFactory.getLogger(Gradle.class);

    private static GradleFactory factory = new DefaultGradleFactory(new DefaultLoggingConfigurer());

    private StartParameter startParameter;
    private ISettingsFinder settingsFinder;
    private IGradlePropertiesLoader gradlePropertiesLoader;
    private SettingsProcessor settingsProcessor;
    private BuildLoader buildLoader;
    private BuildConfigurer buildConfigurer;

    private final ListenerBroadcast<BuildListener> buildListeners = new ListenerBroadcast<BuildListener>(
            BuildListener.class);

    public Gradle(StartParameter startParameter, ISettingsFinder settingsFinder,
                  IGradlePropertiesLoader gradlePropertiesLoader, SettingsProcessor settingsProcessor,
                  BuildLoader buildLoader, BuildConfigurer buildConfigurer) {
        this.startParameter = startParameter;
        this.settingsFinder = settingsFinder;
        this.gradlePropertiesLoader = gradlePropertiesLoader;
        this.settingsProcessor = settingsProcessor;
        this.buildLoader = buildLoader;
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

        // Load build
        BuildInternal build = buildLoader.load(settings.getRootProject(), classLoader, startParameter,
                gradlePropertiesLoader.getGradleProperties());
        fireProjectsLoaded(build);

        // Configure build
        buildConfigurer.process(build.getRootProject());
        fireProjectsEvaluated(build);
        attachTaskGraphListener(build);

        // Execute build
        BuildExecuter executer = startParameter.getBuildExecuter();
        executer.select(build.getDefaultProject());
        logger.info(String.format("Starting build for %s.", executer.getDescription()));
        executer.execute(build.getTaskGraph());
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
        buildListeners.getSource().buildStarted(startParameter);
    }

    private void fireSettingsEvaluated(SettingsInternal settings) {
        buildListeners.getSource().settingsEvaluated(settings);
    }

    private void fireTaskGraphPrepared(TaskExecutionGraph graph) {
        buildListeners.getSource().taskGraphPopulated(graph);
    }

    private void fireProjectsLoaded(BuildInternal build) {
        buildListeners.getSource().projectsLoaded(build);
    }

    private void fireProjectsEvaluated(BuildInternal build) {
        buildListeners.getSource().projectsEvaluated(build);
    }

    private void fireBuildFinished(BuildResult buildResult) {
        buildListeners.getSource().buildFinished(buildResult);
    }

    // This is used for mocking
    public static void injectCustomFactory(GradleFactory gradleFactory) {
        factory = gradleFactory == null ? new DefaultGradleFactory(new DefaultLoggingConfigurer()) : gradleFactory;
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

    public BuildLoader getBuildLoader() {
        return buildLoader;
    }

    public void setBuildLoader(BuildLoader buildLoader) {
        this.buildLoader = buildLoader;
    }

    public BuildConfigurer getBuildConfigurer() {
        return buildConfigurer;
    }

    public void setBuildConfigurer(BuildConfigurer buildConfigurer) {
        this.buildConfigurer = buildConfigurer;
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
