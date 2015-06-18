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
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.progress.BuildOperationInternal;
import org.gradle.internal.progress.BuildOperationType;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.progress.OperationIdGenerator;
import org.gradle.logging.LoggingManagerInternal;

import java.io.Closeable;

public class DefaultGradleLauncher extends GradleLauncher {
    private enum Stage {
        Configure, Build
    }

    private final GradleInternal gradle;
    private final InitScriptHandler initScriptHandler;
    private final SettingsHandler settingsHandler;
    private final BuildLoader buildLoader;
    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final LoggingManagerInternal loggingManager;
    private final BuildListener buildListener;
    private final ModelConfigurationListener modelConfigurationListener;
    private final TasksCompletionListener tasksCompletionListener;
    private final BuildCompletionListener buildCompletionListener;
    private final InternalBuildListener internalBuildListener;
    private final BuildExecuter buildExecuter;
    private final Closeable buildServices;

    /**
     * Creates a new instance.
     */
    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsHandler settingsHandler,
                                 BuildLoader buildLoader, BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 LoggingManagerInternal loggingManager, BuildListener buildListener,
                                 ModelConfigurationListener modelConfigurationListener, TasksCompletionListener tasksCompletionListener,
                                 BuildCompletionListener buildCompletionListener, InternalBuildListener internalBuildListener,
                                 BuildExecuter buildExecuter, Closeable buildServices) {
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
        this.buildCompletionListener = buildCompletionListener;
        this.buildServices = buildServices;
        this.internalBuildListener = internalBuildListener;
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

    private BuildResult doBuild(final Stage upTo) {
        loggingManager.start();

        return runRootBuildOperation(BuildOperationType.RUNNING_BUILD, new Factory<BuildResult>() {
            @Override
            public BuildResult create() {
                buildListener.buildStarted(gradle);

                Throwable failure = null;
                try {
                    doBuildStages(upTo);
                } catch (Throwable t) {
                    failure = exceptionAnalyser.transform(t);
                }
                BuildResult buildResult = new BuildResult(gradle, failure);
                buildListener.buildFinished(buildResult);

                return buildResult;
            }
        });
    }

    private void doBuildStages(Stage upTo) {
        // Evaluate init scripts
        runBuildOperation(BuildOperationType.EVALUATING_INIT_SCRIPTS, new Factory<Void>() {
            @Override
            public Void create() {
                initScriptHandler.executeScripts(gradle);
                return null;
            }
        });

        // Evaluate settings script
        runBuildOperation(BuildOperationType.EVALUATING_SETTINGS, new Factory<Void>() {
            @Override
            public Void create() {
                SettingsInternal settings = settingsHandler.findAndLoadSettings(gradle);
                buildListener.settingsEvaluated(settings);
                buildLoader.load(settings.getRootProject(), settings.getDefaultProject(), gradle, settings.getRootClassLoaderScope());
                buildListener.projectsLoaded(gradle);
                return null;
            }
        });

        // Configure build
        runBuildOperation(BuildOperationType.CONFIGURING_BUILD, new Factory<Void>() {
            @Override
            public Void create() {
                buildConfigurer.configure(gradle);

                if (!gradle.getStartParameter().isConfigureOnDemand()) {
                    buildListener.projectsEvaluated(gradle);
                }

                modelConfigurationListener.onConfigure(gradle);
                return null;
            }
        });


        if (upTo == Stage.Configure) {
            return;
        }

        // Populate task graph
        runBuildOperation(BuildOperationType.POPULATING_TASK_GRAPH, new Factory<Void>() {
            @Override
            public Void create() {
                buildExecuter.select(gradle);

                if (gradle.getStartParameter().isConfigureOnDemand()) {
                    buildListener.projectsEvaluated(gradle);
                }

                return null;
            }
        });

        // Execute build
        runBuildOperation(BuildOperationType.EXECUTING_TASKS, new Factory<Void>() {
            @Override
            public Void create() {
                buildExecuter.execute();
                tasksCompletionListener.onTasksFinished(gradle);
                return null;
            }
        });

        assert upTo == Stage.Build;
    }

    private <T> T runRootBuildOperation(BuildOperationType operationType, Factory<T> factory) {
        Object id = OperationIdGenerator.generateId(gradle);
        Object parentId = OperationIdGenerator.generateId(gradle.getParent());
        return runBuildOperation(id, parentId, operationType, factory);
    }

    private <T> T runBuildOperation(BuildOperationType operationType, Factory<T> factory) {
        Object id = OperationIdGenerator.generateId(operationType, gradle);
        Object parentId = OperationIdGenerator.generateId(gradle);
        return runBuildOperation(id, parentId, operationType, factory);
    }

    private <T> T runBuildOperation(Object id, Object parentId, BuildOperationType operationType, Factory<T> factory) {
        long startTime = System.currentTimeMillis();
        BuildOperationInternal startEvent = new BuildOperationInternal(id, parentId, operationType, gradle, startTime);
        internalBuildListener.started(startEvent);

        T result = null;
        Throwable error = null;
        try {
            result = factory.create();
        } catch (Throwable e) {
            error = e;
        }

        BuildOperationInternal endEvent = new BuildOperationInternal(id, parentId, operationType, error != null ? error : result, startTime, System.currentTimeMillis());
        internalBuildListener.finished(endEvent);

        if (error != null) {
            UncheckedException.throwAsUncheckedException(error);
        }
        return result;
    }

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

    public void stop() {
        try {
            loggingManager.stop();
            CompositeStoppable.stoppable(buildServices).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }
}
