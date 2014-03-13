/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.GradleLauncher;
import org.gradle.StartParameter;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.logging.LoggingManagerInternal;

public class DefaultGradleLauncher extends GradleLauncher {
    private enum Stage {
        Configure, PopulateTaskGraph, Build
    }

    private final GradleInternal gradle;
    private final SettingsHandler settingsHandler;
    private final BuildLoader buildLoader;
    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final InitScriptHandler initScriptHandler;
    private final LoggingManagerInternal loggingManager;
    private final ModelConfigurationListener modelConfigurationListener;
    private final TasksCompletionListener tasksCompletionListener;
    private final BuildExecuter buildExecuter;

    /**
     * Creates a new instance.  Don't call this directly, use {@link #newInstance(org.gradle.StartParameter)} or {@link
     * #newInstance(String...)} instead.
     */
    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsHandler settingsHandler,
                                 BuildLoader buildLoader, BuildConfigurer buildConfigurer, BuildListener buildListener,
                                 ExceptionAnalyser exceptionAnalyser, LoggingManagerInternal loggingManager,
                                 ModelConfigurationListener modelConfigurationListener, TasksCompletionListener tasksCompletionListener,
                                 BuildExecuter buildExecuter) {
        this.gradle = gradle;
        this.initScriptHandler = initScriptHandler;
        this.settingsHandler = settingsHandler;
        this.buildLoader = buildLoader;
        this.buildConfigurer = buildConfigurer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.loggingManager = loggingManager;
        this.modelConfigurationListener = modelConfigurationListener;
        this.tasksCompletionListener = tasksCompletionListener;
        this.buildExecuter = buildExecuter;
    }

    public GradleInternal getGradle() {
        return gradle;
    }

    /**
     * <p>Executes the build for this GradleLauncher instance and returns the result. Note that when the build fails,
     * the exception is available using {@link org.gradle.BuildResult#getFailure()}.</p>
     *
     * @return The result. Never returns null.
     */
    @Override
    public BuildResult run() {
        return doBuild(Stage.Build);
    }

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via
     * the {@link org.gradle.api.invocation.Gradle#getRootProject()} object.
     *
     * @return A BuildResult object. Never returns null.
     */
    @Override
    public BuildResult getBuildAnalysis() {
        return doBuild(Stage.Configure);
    }

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via
     * the {@link org.gradle.api.invocation.Gradle#getRootProject()} object. Fills the execution plan without running
     * the build. The tasks to be executed tasks are available via {@link org.gradle.api.invocation.Gradle#getTaskGraph()}.
     *
     * @return A BuildResult object. Never returns null.
     */
    @Override
    public BuildResult getBuildAndRunAnalysis() {
        return doBuild(Stage.PopulateTaskGraph);
    }

    private BuildResult doBuild(Stage upTo) {
        loggingManager.start();
        buildListener.buildStarted(gradle);

        Throwable failure = null;
        try {
            doBuildStages(upTo);
        } catch (Throwable t) {
            failure = exceptionAnalyser.transform(t);
        }
        BuildResult buildResult = new BuildResult(gradle, failure);
        buildListener.buildFinished(buildResult);

        // Switching Logging off is important if the Gradle factory is used to
        // run multiple Gradle builds (each one requiring a new instances of GradleLauncher).
        // Switching it off shouldn't be strictly necessary as StandardOutput capturing should
        // always be closed. But as we expose this functionality to the builds, we can't
        // guarantee this.
        loggingManager.stop();
        return buildResult;
    }

    private void doBuildStages(Stage upTo) {
        // Evaluate init scripts
        initScriptHandler.executeScripts(gradle);

        // Evaluate settings script
        SettingsInternal settings = settingsHandler.findAndLoadSettings(gradle);
        buildListener.settingsEvaluated(settings);

        // Load build
        buildLoader.load(settings.getRootProject(), gradle, settings.getClassLoaderScope().createSibling());
        buildListener.projectsLoaded(gradle);

        // Configure build
        buildConfigurer.configure(gradle);

        if (!gradle.getStartParameter().isConfigureOnDemand()) {
            buildListener.projectsEvaluated(gradle);
        }

        modelConfigurationListener.onConfigure(gradle);

        if (upTo == Stage.Configure) {
            return;
        }

        // Populate task graph
        buildExecuter.select(gradle);

        if (gradle.getStartParameter().isConfigureOnDemand()) {
            buildListener.projectsEvaluated(gradle);
        }

        if (upTo == Stage.PopulateTaskGraph) {
            return;
        }

        // Execute build
        buildExecuter.execute();
        tasksCompletionListener.onTasksFinished(gradle);

        assert upTo == Stage.Build;
    }

    // This is used for mocking

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the
     * execution of the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for supported listener
     * types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        gradle.addListener(listener);
    }

    /**
     * Use the given listener. See {@link org.gradle.api.invocation.Gradle#useLogger(Object)} for details.
     *
     * @param logger The logger to use.
     */
    @Override
    public void useLogger(Object logger) {
        gradle.useLogger(logger);
    }
    
    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to
     * standard output by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addStandardOutputListener(StandardOutputListener listener) {
        loggingManager.addStandardOutputListener(listener);
    }

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to
     * standard error by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addStandardErrorListener(StandardOutputListener listener) {
        loggingManager.addStandardErrorListener(listener);
    }

    @Override
    public StartParameter getStartParameter() {
        return gradle.getStartParameter();
    }
}
