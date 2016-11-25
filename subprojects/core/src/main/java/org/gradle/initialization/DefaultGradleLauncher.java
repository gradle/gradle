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
import org.gradle.api.Action;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.service.scopes.BuildScopeServices;

import java.util.ArrayList;
import java.util.List;

public class DefaultGradleLauncher implements GradleLauncher {

    private enum Stage {
        Load, Configure, Build
    }

    private final InitScriptHandler initScriptHandler;
    private final SettingsLoader settingsLoader;
    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final LoggingManagerInternal loggingManager;
    private final BuildListener buildListener;
    private final ModelConfigurationListener modelConfigurationListener;
    private final BuildCompletionListener buildCompletionListener;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildConfigurationActionExecuter buildConfigurationActionExecuter;
    private final BuildExecuter buildExecuter;
    private final BuildScopeServices buildServices;
    private final ListenerManager globalListenerManager;
    private final List<?> servicesToStop;
    private final List<Stoppable> listeners = new ArrayList<Stoppable>();
    private GradleInternal gradle;
    private SettingsInternal settings;
    private Stage stage;

    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsLoader settingsLoader,
                                 BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 LoggingManagerInternal loggingManager, BuildListener buildListener,
                                 ModelConfigurationListener modelConfigurationListener,
                                 BuildCompletionListener buildCompletionListener, BuildOperationExecutor operationExecutor,
                                 BuildConfigurationActionExecuter buildConfigurationActionExecuter, BuildExecuter buildExecuter,
                                 BuildScopeServices buildServices, ListenerManager globalListenerManager, List<?> servicesToStop) {
        this.gradle = gradle;
        this.initScriptHandler = initScriptHandler;
        this.settingsLoader = settingsLoader;
        this.buildConfigurer = buildConfigurer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.loggingManager = loggingManager;
        this.modelConfigurationListener = modelConfigurationListener;
        this.buildOperationExecutor = operationExecutor;
        this.buildConfigurationActionExecuter = buildConfigurationActionExecuter;
        this.buildExecuter = buildExecuter;
        this.buildCompletionListener = buildCompletionListener;
        this.buildServices = buildServices;
        this.globalListenerManager = globalListenerManager;
        this.servicesToStop = servicesToStop;
        loggingManager.start();
    }

    @Override
    public GradleInternal getGradle() {
        return gradle;
    }

    @Override
    public SettingsInternal getSettings() {
        return settings;
    }

    @Override
    public BuildResult run() {
        return doBuild(Stage.Build);
    }

    @Override
    public BuildResult getBuildAnalysis() {
        return doBuild(Stage.Configure);
    }

    @Override
    public BuildResult load() throws ReportedException {
        return doBuild(Stage.Load);
    }

    private BuildResult doBuild(final Stage upTo) {
        Throwable failure = null;
        try {
            buildListener.buildStarted(gradle);
            doBuildStages(upTo);
            flushPendingCacheOperations();
        } catch (Throwable t) {
            failure = exceptionAnalyser.transform(t);
        }
        BuildResult buildResult = new BuildResult(upTo.name(), gradle, failure);
        buildListener.buildFinished(buildResult);
        if (failure != null) {
            throw new ReportedException(failure);
        }

        return buildResult;
    }

    private void flushPendingCacheOperations() {
        gradle.getServices().get(TaskHistoryStore.class).flush();
    }

    private void doBuildStages(Stage upTo) {
        if (stage == Stage.Build) {
            throw new IllegalStateException("Cannot build with GradleLauncher multiple times");
        }

        if (stage == null) {
            // Evaluate init scripts
            initScriptHandler.executeScripts(gradle);

            // Build `buildSrc`, load settings.gradle, and construct composite (if appropriate)
            settings = settingsLoader.findAndLoadSettings(gradle);

            stage = Stage.Load;
        }

        if (upTo == Stage.Load) {
            return;
        }

        if (stage == Stage.Load) {
            // Configure build
            buildOperationExecutor.run("Configure build", new Action<BuildOperationContext>() {
                @Override
                public void execute(BuildOperationContext buildOperationContext) {
                    buildConfigurer.configure(gradle);

                    if (!gradle.getStartParameter().isConfigureOnDemand()) {
                        buildListener.projectsEvaluated(gradle);
                    }

                    modelConfigurationListener.onConfigure(gradle);
                }
            });

            stage = Stage.Configure;
        }

        if (upTo == Stage.Configure) {
            return;
        }

        // After this point, the GradleLauncher cannot be reused
        stage = Stage.Build;

        // Populate task graph
        buildOperationExecutor.run("Calculate task graph", new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                buildConfigurationActionExecuter.select(gradle);
                if (gradle.getStartParameter().isConfigureOnDemand()) {
                    buildListener.projectsEvaluated(gradle);
                }
            }
        });

        // Execute build
        buildOperationExecutor.run("Run tasks", new Action<BuildOperationContext>() {
            @Override
            public void execute(BuildOperationContext buildOperationContext) {
                buildExecuter.execute(gradle);
            }
        });
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for
     * supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addListener(Object listener) {
        gradle.addListener(listener);
    }

    @Override
    public void addNestedListener(final Object listener) {
        // Add the listener and remove when stopped
        globalListenerManager.addListener(listener);
        listeners.add(new Stoppable() {
            @Override
            public void stop() {
                globalListenerManager.removeListener(listener);
            }
        });
    }

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to standard output by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addStandardOutputListener(StandardOutputListener listener) {
        loggingManager.addStandardOutputListener(listener);
    }

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to standard error by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    @Override
    public void addStandardErrorListener(StandardOutputListener listener) {
        loggingManager.addStandardErrorListener(listener);
    }

    public void stop() {
        try {
            loggingManager.stop();
            CompositeStoppable.stoppable(listeners).add(buildServices).add(servicesToStop).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }
}
