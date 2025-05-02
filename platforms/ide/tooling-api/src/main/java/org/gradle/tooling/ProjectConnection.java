/*
 * Copyright 2011 the original author or authors.
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

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;

/**
 * <p>Represents a long-lived connection to a Gradle project. You obtain an instance of a {@code ProjectConnection} by using {@link org.gradle.tooling.GradleConnector#connect()}.</p>
 *
 * <pre class='autoTested'>
 *
 * try (ProjectConnection connection = GradleConnector.newConnector()
 *        .forProjectDirectory(new File("someFolder"))
 *        .connect()) {
 *
 *    //obtain some information from the build
 *    BuildEnvironment environment = connection.model(BuildEnvironment.class).get();
 *
 *    //run some tasks
 *    connection.newBuild()
 *      .forTasks("tasks")
 *      .setStandardOutput(System.out)
 *      .run();
 *
 * }
 * </pre>
 *
 * <h2>Thread safety information</h2>
 *
 * <p>All implementations of {@code ProjectConnection} are thread-safe, and may be shared by any number of threads.</p>
 *
 * <p>All notifications from a given {@code ProjectConnection} instance are delivered by a single thread at a time. Note, however, that the delivery thread may change over time.</p>
 *
 * @since 1.0-milestone-3
 */
public interface ProjectConnection extends Closeable {
    /**
     * Fetches a snapshot of the model of the given type for this project. This method blocks until the model is available.
     *
     * <p>This method is simply a convenience for calling {@code model(modelType).get()}</p>
     *
     * @param modelType The model type.
     * @param <T> The model type.
     * @return The model.
     * @throws UnsupportedVersionException When the target Gradle version does not support the given model.
     * @throws UnknownModelException When the target Gradle version or build does not support the requested model.
     * @throws BuildException On some failure executing the Gradle build, in order to build the model.
     * @throws GradleConnectionException On some other failure using the connection.
     * @throws IllegalStateException When this connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    <T> T getModel(Class<T> modelType) throws GradleConnectionException, IllegalStateException;

    /**
     * Starts fetching a snapshot of the given model, passing the result to the given handler when complete. This method returns immediately, and the result is later
     * passed to the given handler's {@link ResultHandler#onComplete(Object)} method.
     *
     * <p>If the operation fails, the handler's {@link ResultHandler#onFailure(GradleConnectionException)} method is called with the appropriate exception.
     * See {@link #getModel(Class)} for a description of the various exceptions that the operation may fail with.
     *
     * <p>This method is simply a convenience for calling {@code model(modelType).get(handler)}</p>
     *
     * @param modelType The model type.
     * @param handler The handler to pass the result to.
     * @param <T> The model type.
     * @throws IllegalStateException When this connection has been closed or is closing.
     * @since 1.0-milestone-3
     */
    <T> void getModel(Class<T> modelType, ResultHandler<? super T> handler) throws IllegalStateException;

    /**
     * Creates a launcher which can be used to execute a build.
     *
     * <p>Requires Gradle 1.0-milestone-8 or later.</p>
     *
     * @return The launcher.
     * @since 1.0-milestone-3
     */
    BuildLauncher newBuild();

    /**
     * Creates a test launcher which can be used to execute tests.
     *
     * <p>Requires Gradle 3.0 or later.</p>
     *
     * @return The launcher.
     * @since 2.6
     */
    TestLauncher newTestLauncher();

    /**
     * Creates a builder which can be used to query the model of the given type.
     *
     * <p>Any of following models types may be available, depending on the version of Gradle being used by the target
     * build:
     *
     * <ul>
     *     <li>{@link org.gradle.tooling.model.gradle.GradleBuild}</li>
     *     <li>{@link org.gradle.tooling.model.build.BuildEnvironment}</li>
     *     <li>{@link org.gradle.tooling.model.GradleProject}</li>
     *     <li>{@link org.gradle.tooling.model.gradle.BuildInvocations}</li>
     *     <li>{@link org.gradle.tooling.model.gradle.ProjectPublications}</li>
     *     <li>{@link org.gradle.tooling.model.idea.IdeaProject}</li>
     *     <li>{@link org.gradle.tooling.model.idea.BasicIdeaProject}</li>
     *     <li>{@link org.gradle.tooling.model.eclipse.EclipseProject}</li>
     *     <li>{@link org.gradle.tooling.model.eclipse.HierarchicalEclipseProject}</li>
     * </ul>
     *
     * <p>A build may also expose additional custom tooling models. You can use this method to query these models.
     *
     * <p>Requires Gradle 1.0-milestone-8 or later.</p>
     *
     * @param modelType The model type
     * @param <T> The model type.
     * @return The builder.
     * @since 1.0-milestone-3
     */
    <T> ModelBuilder<T> model(Class<T> modelType);

    /**
     * Creates an executer which can be used to run the given action when the build has finished. The action is serialized into the build
     * process and executed, then its result is serialized back to the caller.
     *
     * <p>Requires Gradle 1.8 or later.</p>
     *
     * @param buildAction The action to run.
     * @param <T> The result type.
     * @return The builder.
     * @since 1.8
     * @see #action() if you want to hook into different points of the build lifecycle.
     */
    <T> BuildActionExecuter<T> action(BuildAction<T> buildAction);

    /**
     * Creates a builder for an executer which can be used to run actions in different phases of the build.
     * The actions are serialized into the build process and executed, then its result is serialized back to the caller.
     *
     * <p>Requires Gradle 4.8 or later.
     *
     * @return The builder.
     * @since 4.8
     */
    BuildActionExecuter.Builder action();

    /**
     * Notifies all daemons about file changes made by an external process, like an IDE.
     *
     * <p>The daemons will use this information to update the retained file system state.
     *
     * <p>The method should be invoked on every change done by the external process.
     * For example, an IDE should notify Gradle when the user saves a changed file, or
     * after some refactoring finished.
     * This will guarantee that Gradle picks up changes when trigerring a build, even
     * if the file system is too slow to notify file watchers.
     *
     * The caller shouldn't notify Gradle about changes detected by using other file
     * watchers, since Gradle already will be using its own file watcher.
     *
     * <p>The paths which are passed in need to be absolute, canonicalized paths.
     * For a delete, the deleted path should be passed.
     * For a rename, the old and the new path should be passed.
     * When creating a new file, the path to the file should be passed.
     *
     * <p>The call is synchronous, i.e. the method ensures that the changed paths are taken into account
     * by the daemon after it returned. This ensures that for every build started
     * after this method has been called knows about the changed paths.
     *
     * <p>If the version of Gradle does not support virtual file system retention (i.e. &lt; 6.1),
     * then the operation is a no-op.
     *
     * @param changedPaths Absolute paths which have been changed by the external process.
     * @throws IllegalArgumentException When the paths are not absolute.
     * @throws UnsupportedVersionException When the target Gradle version is &lt;= 2.5.
     * @throws GradleConnectionException On some other failure using the connection.
     * @since 6.1
     */
    void notifyDaemonsAboutChangedPaths(List<Path> changedPaths);

    /**
     * Closes this connection. Blocks until any pending operations are complete. Once this method has returned, no more notifications will be delivered by any threads.
     * @since 1.0-milestone-3
     */
    @Override
    void close();
}
