/*
 * Copyright 2013 the original author or authors.
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

/**
 * Used to execute a {@link BuildAction} in the build process.
 *
 * @param <T> The type of result produced by this executer.
 * @since 1.8
 */
public interface BuildActionExecuter<T> extends ConfigurableLauncher<BuildActionExecuter<T>> {

    /**
     * Builder for a build action that hooks into different phases of the build.
     *
     * <p>A single {@link BuildAction} is allowed per build phase. Use composite actions if needed.
     *
     * @since 4.8
     */
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
        <T> Builder projectsLoaded(BuildAction<T> buildAction, IntermediateResultHandler<? super T> handler) throws IllegalArgumentException;

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
        <T> Builder buildFinished(BuildAction<T> buildAction, IntermediateResultHandler<? super T> handler) throws IllegalArgumentException;

        /**
         * Builds the executer from the added actions.
         *
         * @return The executer.
         */
        BuildActionExecuter<Void> build();
    }

    /**
     * Specifies the tasks to execute before executing the BuildAction.
     *
     * If not configured, null, or an empty array is passed, then no tasks will be executed.
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created. An empty list will run the project's default tasks.
     * @return this
     * @since 3.5
     */
    BuildActionExecuter<T> forTasks(String... tasks);

    /**
     * Specifies the tasks to execute before executing the BuildAction.
     *
     * If not configured, null, or an empty array is passed, then no tasks will be executed.
     *
     * @param tasks The paths of the tasks to be executed. Relative paths are evaluated relative to the project for which this launcher was created. An empty list will run the project's default tasks.
     * @return this
     * @since 3.5
     */
    BuildActionExecuter<T> forTasks(Iterable<String> tasks);

    /**
     * Runs the action, blocking until its result is available.
     *
     * @throws UnsupportedVersionException When the target Gradle version does not support build action execution.
     * @throws org.gradle.tooling.exceptions.UnsupportedOperationConfigurationException
     *          When the target Gradle version does not support some requested configuration option.
     * @throws org.gradle.tooling.exceptions.UnsupportedBuildArgumentException When there is a problem with build arguments provided by {@link #withArguments(String...)}.
     * @throws BuildActionFailureException When the build action fails with an exception.
     * @throws BuildCancelledException When the operation was cancelled before it completed successfully.
     * @throws BuildException On some failure executing the Gradle build.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.8
     */
    T run() throws GradleConnectionException, IllegalStateException;

    /**
     * Starts executing the action, passing the result to the given handler when complete. This method returns immediately, and the result is later passed to the given handler's {@link
     * ResultHandler#onComplete(Object)} method.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)} method is called with the appropriate exception. See
     * {@link #run()} for a description of the various exceptions that the operation may fail with.
     *
     * @param handler The handler to supply the result to.
     * @throws IllegalStateException When the connection has been closed or is closing.
     * @since 1.8
     */
    void run(ResultHandler<? super T> handler) throws IllegalStateException;
}
