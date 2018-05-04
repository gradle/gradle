/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling;

import org.gradle.api.Incubating;

/**
 * Used to execute multiple {@link BuildAction}s in different phases of the build process.
 *
 * @since 4.8
 */
@Incubating
public interface PhasedBuildActionExecuter extends ConfigurableLauncher<PhasedBuildActionExecuter> {

    /**
     * Builder for a {@link PhasedBuildActionExecuter}.
     *
     * <p>A single {@link BuildAction} is allowed per build phase. Use composite actions if needed.
     *
     * @since 4.8
     */
    @Incubating
    interface Builder {

        /**
         * Executes the given action after projects are loaded and sends its result to the given result handler.
         *
         * <p>Action will be executed after projects are loaded and Gradle will configure projects as necessary for the models requested.
         *
         * <p>If the operation fails, build will fail with the appropriate exception. Handler won't be notified in case of failure.
         *
         * @param buildAction The action to run in the specified build phase.
         * @param handler The handler to supply the result of the given action to.
         * @param <T> The returning type of the action.
         * @return The builder.
         * @throws IllegalArgumentException If an action has already been added to this build phase. Multiple actions per phase are not supported yet.
         */
        <T> Builder projectsLoaded(BuildAction<T> buildAction, PhasedResultHandler<? super T> handler) throws IllegalArgumentException;

        /**
         * Executes the given action after tasks are run and sends its result to the given result handler.
         *
         * <p>If the operation fails, build will fail with the appropriate exception. Handler won't be notified in case of failure.
         *
         * @param buildAction The action to run in the specified build phase.
         * @param handler The handler to supply the result of the given action to.
         * @param <T> The returning type of the action.
         * @return The builder.
         * @throws IllegalArgumentException If an action has already been added to this build phase. Multiple actions per phase are not supported yet.
         */
        <T> Builder buildFinished(BuildAction<T> buildAction, PhasedResultHandler<? super T> handler) throws IllegalArgumentException;

        /**
         * Builds the executer from the added actions.
         *
         * @return The executer.
         */
        PhasedBuildActionExecuter build();
    }

    /**
     * Specifies the tasks to execute before executing the BuildFinishedAction and after the ProjectsLoadedAction.
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created.
     * Passing an empty collection will run the default tasks.
     * @return this
     */
    @Incubating
    PhasedBuildActionExecuter forTasks(String... tasks);

    /**
     * Specifies the tasks to execute before executing the BuildFinishedAction and after the ProjectsLoadedAction.
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created.
     * Passing an empty collection will run the default tasks.
     * @return this
     */
    @Incubating
    PhasedBuildActionExecuter forTasks(Iterable<String> tasks);

    /**
     * Runs all the actions in their respective build phases, blocking until build is finished.
     *
     * <p>If no tasks are defined, Gradle will just configure the build. Otherwise, Gradle will run tasks.
     *
     * <p>Results of each action are sent to their respective result handlers. If one of the actions fails, the build is interrupted.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support phased build action execution.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     *          When the target Gradle version does not support some requested configuration option.
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by {@link #withArguments(String...)}.
     * @throws BuildActionFailureException When one of the build actions fails with an exception.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void run() throws GradleConnectionException, IllegalStateException;

    /**
     * Starts executing the build, passing the build result to the given handler when complete and individual action results to the respective handler when complete.
     * This method returns immediately, and the result is later passed to the given handler's {@link ResultHandler#onComplete(Object)} method.
     *
     * <p>If no tasks are defined, Gradle will just configure the build. Otherwise, Gradle will run tasks.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)} method is called with the appropriate exception. See
     * {@link #run()} for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the build result to. Individual action results are supplied to its respective handler.
     * @throws IllegalStateException When the connection has been closed or is closing.
     */
    void run(ResultHandler<? super Void> handler) throws IllegalStateException;
}
