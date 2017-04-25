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
import org.gradle.StartParameter;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.execution.taskgraph.CalculateTaskGraphDescriptor;
import org.gradle.execution.taskgraph.CalculateTaskGraphOperationResult;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultGradleLauncher implements GradleLauncher {

    private enum Stage {
        Load, Configure, Build
    }

    private final InitScriptHandler initScriptHandler;
    private final SettingsLoader settingsLoader;
    private final BuildConfigurer buildConfigurer;
    private final ExceptionAnalyser exceptionAnalyser;
    private final BuildListener buildListener;
    private final ModelConfigurationListener modelConfigurationListener;
    private final BuildCompletionListener buildCompletionListener;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildConfigurationActionExecuter buildConfigurationActionExecuter;
    private final BuildExecuter buildExecuter;
    private final BuildScopeServices buildServices;
    private final List<?> servicesToStop;
    private GradleInternal gradle;
    private SettingsInternal settings;
    private Stage stage;

    public DefaultGradleLauncher(GradleInternal gradle, InitScriptHandler initScriptHandler, SettingsLoader settingsLoader,
                                 BuildConfigurer buildConfigurer, ExceptionAnalyser exceptionAnalyser,
                                 BuildListener buildListener, ModelConfigurationListener modelConfigurationListener,
                                 BuildCompletionListener buildCompletionListener, BuildOperationExecutor operationExecutor,
                                 BuildConfigurationActionExecuter buildConfigurationActionExecuter, BuildExecuter buildExecuter,
                                 BuildScopeServices buildServices, List<?> servicesToStop) {
        this.gradle = gradle;
        this.initScriptHandler = initScriptHandler;
        this.settingsLoader = settingsLoader;
        this.buildConfigurer = buildConfigurer;
        this.exceptionAnalyser = exceptionAnalyser;
        this.buildListener = buildListener;
        this.modelConfigurationListener = modelConfigurationListener;
        this.buildOperationExecutor = operationExecutor;
        this.buildConfigurationActionExecuter = buildConfigurationActionExecuter;
        this.buildExecuter = buildExecuter;
        this.buildCompletionListener = buildCompletionListener;
        this.buildServices = buildServices;
        this.servicesToStop = servicesToStop;
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
        // TODO:pm Move this to RunAsBuildOperationBuildActionRunner when BuildOperationWorkerRegistry scope is changed
        final AtomicReference<BuildResult> buildResult = new AtomicReference<BuildResult>();
        WorkerLeaseService workerLeaseService = buildServices.get(WorkerLeaseService.class);
        workerLeaseService.withLocks(workerLeaseService.getWorkerLease()).execute(new Runnable() {
            @Override
            public void run() {
                Throwable failure = null;
                try {
                    buildListener.buildStarted(gradle);
                    doBuildStages(upTo);
                } catch (Throwable t) {
                    failure = exceptionAnalyser.transform(t);
                }
                buildResult.set(new BuildResult(upTo.name(), gradle, failure));
                buildListener.buildFinished(buildResult.get());
                if (failure != null) {
                    throw new ReportedException(failure);
                }
            }
        });
        return buildResult.get();
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
            buildOperationExecutor.run(new ConfigureBuildBuildOperation());
            stage = Stage.Configure;
        }

        if (upTo == Stage.Configure) {
            return;
        }

        // After this point, the GradleLauncher cannot be reused
        stage = Stage.Build;

        // marker descriptor class for identifying build operation
        buildOperationExecutor.run(new CalculateTaskGraphBuildOperation());

        // Execute build
        buildOperationExecutor.run(new RunTasksBuildOperation());
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

    public void stop() {
        try {
            CompositeStoppable.stoppable(buildServices).add(servicesToStop).stop();
        } finally {
            buildCompletionListener.completed();
        }
    }

    private class ConfigureBuildBuildOperation implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            buildConfigurer.configure(gradle);

            if (!isConfigureOnDemand()) {
                projectsEvaluated();
            }

            modelConfigurationListener.onConfigure(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Configure build");
        }
    }

    private class CalculateTaskGraphBuildOperation implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext buildOperationContext) {
            buildConfigurationActionExecuter.select(gradle);
            if (isConfigureOnDemand()) {
                projectsEvaluated();
            }
            // make requested tasks available from according build operation.
            buildOperationContext.setResult(new CalculateTaskGraphOperationResult(CollectionUtils.collect(gradle.getTaskGraph().getRequestedTasks(), new Transformer<String, Task>() {
                @Override
                public String transform(Task task) {
                    return task.getPath();
                }
            })));
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            StartParameter startParameter = gradle.getStartParameter();
            CalculateTaskGraphDescriptor calculateTaskGraphDescriptor = new CalculateTaskGraphDescriptor(startParameter.getTaskRequests(), startParameter.getExcludedTaskNames());
            return BuildOperationDescriptor.displayName("Calculate task graph").details(calculateTaskGraphDescriptor);
        }
    }

    private class RunTasksBuildOperation implements RunnableBuildOperation {
        @Override
        public void run(BuildOperationContext context) {
            buildExecuter.execute(gradle);
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Run tasks");
        }
    }

    private boolean isConfigureOnDemand() {
        return gradle.getStartParameter().isConfigureOnDemand();
    }

    private void projectsEvaluated() {
        buildListener.projectsEvaluated(gradle);
    }
}
