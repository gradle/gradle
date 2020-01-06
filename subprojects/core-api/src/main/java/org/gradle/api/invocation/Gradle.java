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
package org.gradle.api.invocation;

import groovy.lang.Closure;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.PluginAware;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.HasInternalProtocol;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;

/**
 * Represents an invocation of Gradle.
 *
 * <p>You can obtain a {@code Gradle} instance by calling {@link Project#getGradle()}.</p>
 */
@HasInternalProtocol
public interface Gradle extends PluginAware {
    /**
     * Returns the current Gradle version.
     *
     * @return The Gradle version. Never returns null.
     */
    String getGradleVersion();

    /**
     * Returns the Gradle user home directory.
     *
     * This directory is used to cache downloaded resources, compiled build scripts and so on.
     *
     * @return The user home directory. Never returns null.
     */
    File getGradleUserHomeDir();

    /**
     * Returns the Gradle home directory, if any.
     *
     * This directory is the directory containing the Gradle distribution executing this build.
     * <p>
     * When using the “Gradle Daemon”, this may not be the same Gradle distribution that the build was started with.
     * If an existing daemon process is running that is deemed compatible (e.g. has the desired JVM characteristics)
     * then this daemon may be used instead of starting a new process and it may have been started from a different “gradle home”.
     * However, it is guaranteed to be the same version of Gradle. For more information on the Gradle Daemon, please consult the
     * <a href="https://docs.gradle.org/current/userguide/gradle_daemon.html" target="_top">User Manual</a>.
     *
     * @return The home directory. May return null.
     */
    @Nullable
    File getGradleHomeDir();

    /**
     * Returns the parent build of this build, if any.
     *
     * @return The parent build. May return null.
     */
    @Nullable
    Gradle getParent();

    /**
     * Returns the root project of this build.
     *
     * @return The root project. Never returns null.
     * @throws IllegalStateException When called before the root project is available.
     */
    Project getRootProject() throws IllegalStateException;

    /**
     * Adds an action to execute against the root project of this build.
     *
     * If the root project is already available, the action
     * is executed immediately. Otherwise, the action is executed when the root project becomes available.
     *
     * @param action The action to execute.
     */
    void rootProject(Action<? super Project> action);

    /**
     * Adds an action to execute against all projects of this build.
     *
     * The action is executed immediately against all projects which are
     * already available. It is also executed as subsequent projects are added to this build.
     *
     * @param action The action to execute.
     */
    void allprojects(Action<? super Project> action);

    /**
     * Returns the {@link TaskExecutionGraph} for this build.
     *
     * @return The task graph. Never returns null.
     */
    TaskExecutionGraph getTaskGraph();

    /**
     * Returns the {@link StartParameter} used to start this build.
     *
     * @return The start parameter. Never returns null.
     */
    StartParameter getStartParameter();

    /**
     * Adds a listener to this build, to receive notifications as projects are evaluated.
     *
     * @param listener The listener to add. Does nothing if this listener has already been added.
     * @return The added listener.
     */
    ProjectEvaluationListener addProjectEvaluationListener(ProjectEvaluationListener listener);

    /**
     * Removes the given listener from this build.
     *
     * @param listener The listener to remove. Does nothing if this listener has not been added.
     */
    void removeProjectEvaluationListener(ProjectEvaluationListener listener);

    /**
     * Adds a closure to be called immediately before a project is evaluated. The project is passed to the closure as a
     * parameter.
     *
     * @param closure The closure to execute.
     */
    void beforeProject(Closure closure);

    /**
     * Adds an action to be called immediately before a project is evaluated.
     *
     * @param action The action to execute.
     * @since 3.4
     */
    void beforeProject(Action<? super Project> action);

    /**
     * Adds a closure to be called immediately after a project is evaluated.
     *
     * The project is passed to the closure as the first parameter. The project evaluation failure, if any,
     * is passed as the second parameter. Both parameters are optional.
     *
     * @param closure The closure to execute.
     */
    void afterProject(Closure closure);

    /**
     * Adds an action to be called immediately after a project is evaluated.
     *
     * @param action The action to execute.
     * @since 3.4
     */
    void afterProject(Action<? super Project> action);

    /**
     * Adds a closure to be called when the build is started.
     *
     * This {@code Gradle} instance is passed to the closure as the first parameter.
     *
     * @param closure The closure to execute.
     */
    void buildStarted(Closure closure);

    /**
     * Adds an action to be called when the build is started.
     *
     * @param action The action to execute.
     * @since 3.4
     */
    void buildStarted(Action<? super Gradle> action);

    /**
     * Adds an action to be called before the build settings have been loaded and evaluated.
     *
     * @param closure The action to execute.
     * @since 6.0
     */
    @Incubating
    void beforeSettings(Closure<?> closure);

    /**
     * Adds an action to be called before the build settings have been loaded and evaluated.
     *
     * @param action The action to execute.
     * @since 6.0
     */
    @Incubating
    void beforeSettings(Action<? super Settings> action);

    /**
     * Adds a closure to be called when the build settings have been loaded and evaluated.
     *
     * The settings object is fully configured and is ready to use to load the build projects. The
     * {@link org.gradle.api.initialization.Settings} object is passed to the closure as a parameter.
     *
     * @param closure The closure to execute.
     */
    void settingsEvaluated(Closure closure);

    /**
     * Adds an action to be called when the build settings have been loaded and evaluated.
     *
     * The settings object is fully configured and is ready to use to load the build projects.
     *
     * @param action The action to execute.
     * @since 3.4
     */
    void settingsEvaluated(Action<? super Settings> action);

    /**
     * Adds a closure to be called when the projects for the build have been created from the settings.
     *
     * None of the projects have been evaluated. This {@code Gradle} instance is passed to the closure as a parameter.
     * <p>
     * An example of hooking into the projectsLoaded to configure buildscript classpath from the init script.
     * <pre class='autoTested'>
     * //init.gradle
     * gradle.projectsLoaded {
     *   rootProject.buildscript {
     *     repositories {
     *       //...
     *     }
     *     dependencies {
     *       //...
     *     }
     *   }
     * }
     * </pre>
     *
     * @param closure The closure to execute.
     */
    void projectsLoaded(Closure closure);

    /**
     * Adds an action to be called when the projects for the build have been created from the settings.
     *
     * None of the projects have been evaluated.
     *
     * @param action The action to execute.
     * @since 3.4
     */
    void projectsLoaded(Action<? super Gradle> action);

    /**
     * Adds a closure to be called when all projects for the build have been evaluated.
     *
     * The project objects are fully configured and are ready to use to populate the task graph.
     * This {@code Gradle} instance is passed to the closure as a parameter.
     *
     * @param closure The closure to execute.
     */
    void projectsEvaluated(Closure closure);

    /**
     * Adds an action to be called when all projects for the build have been evaluated.
     *
     * The project objects are fully configured and are ready to use to populate the task graph.
     *
     * @param action The action to execute.
     * @since 3.4
     */
    void projectsEvaluated(Action<? super Gradle> action);

    /**
     * Adds a closure to be called when the build is completed.
     *
     * All selected tasks have been executed.
     * A {@link BuildResult} instance is passed to the closure as a parameter.
     *
     * @param closure The closure to execute.
     */
    void buildFinished(Closure closure);

    /**
     * Adds an action to be called when the build is completed.
     *
     * All selected tasks have been executed.
     *
     * @param action The action to execute.
     * @since 3.4
     */
    void buildFinished(Action<? super BuildResult> action);

    /**
     * Adds a {@link BuildListener} to this Build instance.
     *
     * The listener is notified of events which occur during the execution of the build.
     *
     * @param buildListener The listener to add.
     */
    void addBuildListener(BuildListener buildListener);

    /**
     * Adds the given listener to this build. The listener may implement any of the given listener interfaces:
     *
     * <ul>
     * <li>{@link org.gradle.BuildListener}
     * <li>{@link org.gradle.api.execution.TaskExecutionGraphListener}
     * <li>{@link org.gradle.api.ProjectEvaluationListener}
     * <li>{@link org.gradle.api.execution.TaskExecutionListener}
     * <li>{@link org.gradle.api.execution.TaskActionListener}
     * <li>{@link org.gradle.api.logging.StandardOutputListener}
     * <li>{@link org.gradle.api.tasks.testing.TestListener}
     * <li>{@link org.gradle.api.tasks.testing.TestOutputListener}
     * <li>{@link org.gradle.api.artifacts.DependencyResolutionListener}
     * </ul>
     *
     * @param listener The listener to add. Does nothing if this listener has already been added.
     */
    void addListener(Object listener);

    /**
     * Removes the given listener from this build.
     *
     * @param listener The listener to remove. Does nothing if this listener has not been added.
     */
    void removeListener(Object listener);

    /**
     * Uses the given object as a logger.
     *
     * The logger object may implement any of the listener interfaces supported by
     * {@link #addListener(Object)}.
     * <p>
     * Each listener interface has exactly one associated logger. When you call this
     * method with a logger of a given listener type, the new logger will replace whichever logger is currently
     * associated with the listener type. This allows you to selectively replace the standard logging which Gradle
     * provides with your own implementation, for certain types of events.
     *
     * @param logger The logger to use.
     */
    void useLogger(Object logger);

    /**
     * Returns this {@code Gradle} instance.
     *
     * This method is useful in init scripts to explicitly access Gradle
     * properties and methods. For example, using <code>gradle.parent</code> can express your intent better than using
     * <code>parent</code>. This property also allows you to access Gradle properties from a scope where the property
     * may be hidden, such as, for example, from a method or closure.
     *
     * @return this. Never returns null.
     */
    Gradle getGradle();

    /**
     * Returns the build services that are shared by all projects of this build.
     *
     * @since 6.1
     */
    @Incubating
    BuildServiceRegistry getSharedServices();

    /**
     * Returns the included builds for this build.
     *
     * @since 3.1
     */
    Collection<IncludedBuild> getIncludedBuilds();

    /**
     * Returns the included build with the specified name for this build.
     *
     * @throws UnknownDomainObjectException when there is no build with the given name
     * @since 3.1
     */
    IncludedBuild includedBuild(String name) throws UnknownDomainObjectException;
}
