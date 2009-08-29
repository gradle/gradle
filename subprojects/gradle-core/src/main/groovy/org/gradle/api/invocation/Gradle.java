/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.Project;
import org.gradle.api.ProjectEvaluationListener;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.artifacts.repositories.InternalRepository;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.StartParameter;
import org.gradle.BuildListener;

import java.io.File;

import groovy.lang.Closure;

/**
 * <p>A {@code Gradle} represents an invocation of Gradle.</p>
 *
 * <p>You can obtain a {@code Gradle} instance by calling {@link Project#getGradle()}. In your build file you can use
 * {@code gradle} to access it.</p>
 */
public interface Gradle {
    /**
     * <p>Returns the current Gradle version.</p>
     *
     * @return The Gradle version. Never returns null.
     */
    String getGradleVersion();

    /**
     * <p>Returns the Gradle user home directory. This directory is used to cache downloaded resources.</p>
     *
     * @return The user home directory. Never returns null.
     */
    File getGradleUserHomeDir();

    /**
     * <p>Returns the Gradle home directory. This directory is used to locate resources such as the default imports
     * file.</p>
     *
     * @return The home directory. Never returns null.
     */
    File getGradleHomeDir();

    /**
     * <p>Returns the root project of this build.</p>
     *
     * @return The root project. Never returns null.
     */
    Project getRootProject();

    /**
     * <p>Returns the {@link TaskExecutionGraph} for this build.</p>
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
     * Returns the repository used to pass artifacts between projects in this build.
     *
     * @return The internal repository. Never returns null.
     */
    InternalRepository getInternalRepository();

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
     * Adds a closure to be called immediately after a project is evaluated. The project is passed to the closure as the
     * first parameter. The project evaluation failure, if any, is passed as the second parameter. Both parameters are
     * options.
     *
     * @param closure The closure to execute.
     */
    void afterProject(Closure closure);

    /**
     * <p>Adds a {@link BuildListener} to this Build instance. The listener is notified of events which occur during the
     * execution of the build.</p>
     *
     * @param buildListener The listener to add.
     */
    void addBuildListener(BuildListener buildListener);

    /**
     * Returns this {@code Gradle} instance.
     *
     * @return this. Never returns null.
     */
    Gradle getGradle();

    /**
     * Returns the init script handler for this build. You can use this handler to manage the classpath used to compile
     * and execute the build's init scripts.
     *
     * @return the classpath handler. Never returns null.
     */
    ScriptHandler getInitscript();

    /**
     * Configures the init script classpath for this build. The given closure is executed against this build's {@link
     * ScriptHandler}. The {@link ScriptHandler} is passed to the closure as the closure's delegate.
     *
     * @param configureClosure the closure to use to configure the init script classpath.
     */
    void initscript(Closure configureClosure);

    /**
     * Disables redirection of standard output during init script evaluation. By default redirection is enabled.
     *
     * @see #captureStandardOutput(org.gradle.api.logging.LogLevel)
     */
    void disableStandardOutputCapture();

    /**
     * Starts redirection of standard output during to the logging system during init script evaluation. By default
     * redirection is enabled and the output is redirected to the QUIET level. System.err is always redirected to the
     * ERROR level. Redirection of output at execution time can be configured via the tasks.
     *
     * For more fine-grained control on redirecting standard output see {@link org.gradle.api.logging.StandardOutputLogging}.
     *
     * @param level The level standard out should be logged to.
     * @see #disableStandardOutputCapture()
     */
    void captureStandardOutput(LogLevel level);

    /**
     * Returns the logger for this build. You can use this in your init scripts to write log messages.
     *
     * @return The logger. Never returns null.
     */
    Logger getLogger();
}
