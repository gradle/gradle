/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.build;

import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.execution.plan.BuildWorkPlan;
import org.gradle.execution.plan.Node;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Controls the lifecycle of an individual build in the build tree.
 */
public interface BuildLifecycleController {
    /**
     * Returns the current state of the mutable model for this build.
     *
     * Note: You should avoid using this method, as no thread safety or lifecycling is applied to the return value.
     */
    GradleInternal getGradle();

    /**
     * Configures the settings for this build, if not already available.
     * Can be called multiple times.
     */
    void loadSettings();

    /**
     * Runs the given action against the loaded settings for this build.
     * This may fail with an error, if this build is loaded from cache rather than configured.
     *
     * @return The loaded settings instance.
     */
    <T> T withSettings(Function<? super SettingsInternal, T> action);

    /**
     * Configures the projects of the build, if not already done.
     * Can be called multiple times.
     */
    void configureProjects();

    /**
     * Runs the given action against the mutable state of this build after configuring the projects of the build.
     * This may fail with an error, if this build is loaded from cache rather than configured.
     *
     * @return The configured Gradle build instance.
     */
    <T> T withProjectsConfigured(Function<? super GradleInternal, T> action);

    /**
     * Configures the build, if not already done.
     * This may fail with an error, if this build is loaded from cache rather than configured.
     *
     * Note: You should not use this method as no thread safety is applied to the return value.
     *
     * @return The configured Gradle build instance.
     */
    GradleInternal getConfiguredBuild();

    /**
     * Prepares this build to schedule tasks. May configure the build, if required to later schedule the requested tasks. Can be called multiple times.
     */
    void prepareToScheduleTasks();

    /**
     * Creates a new work plan for this build.
     * Must call {@link #prepareToScheduleTasks()} prior to calling this method. This method can be called multiple times to create multiple plans.
     */
    BuildWorkPlan newWorkGraph();

    /**
     * Populates the given work plan with tasks and work from this build.
     */
    void populateWorkGraph(BuildWorkPlan plan, Consumer<? super WorkGraphBuilder> action);

    /**
     * Finalizes the work graph after it has not been populated.
     */
    void finalizeWorkGraph(BuildWorkPlan plan);

    /**
     * Executes the given work for this build. Does not automatically configure the build or schedule any tasks.
     * Must call {@link #finalizeWorkGraph(BuildWorkPlan)} prior to calling this method.
     */
    ExecutionResult<Void> executeTasks(BuildWorkPlan plan);

    /**
     * Runs an action against the tooling model creators of this build. May configure the build as required.
     */
    <T> T withToolingModels(Function<? super BuildToolingModelController, T> action);

    /**
     * Calls the `buildFinished` hooks and other user code clean up.
     * Failures to finish the build are passed to the given consumer rather than thrown.
     *
     * @param failure The build failure that should be reported to the buildFinished hooks. When null, this launcher may use whatever failure it has already collected.
     * @return a result containing any failures that happen while finishing the build.
     */
    ExecutionResult<Void> finishBuild(@Nullable Throwable failure);

    /**
     * <p>Adds a listener to this build instance. Receives events for this build only.
     */
    void addListener(Object listener);

    interface WorkGraphBuilder {
        /**
         * Adds requested tasks, as defined in the {@link org.gradle.StartParameter}, and their dependencies to the work graph for this build.
         */
        void addRequestedTasks();

        /**
         * Adds the given tasks and their dependencies to the work graph for this build.
         */
        void addEntryTasks(List<? extends Task> tasks);

        /**
         * Adds the given nodes to the work graph for this build.
         */
        void addNodes(List<? extends Node> nodes);
    }
}
