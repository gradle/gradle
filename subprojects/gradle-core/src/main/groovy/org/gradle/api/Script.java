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
package org.gradle.api;

import groovy.lang.Closure;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.initialization.dsl.ScriptHandler;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;

import java.io.File;

/**
 * <p>The base class for all scripts executed by Gradle. This is a extension to the Groovy {@code Script} class, which
 * adds in some Gradle-specific methods. As your compiled script class will implement this interface, you can use the
 * methods and properties declared here directly in your script.</p>
 *
 * <p>Generally, a {@code Script} object will have a delegate object attached to it. For example, a build script will
 * have a {@link Project} object attached to it, and an initialization script will have a {@link
 * org.gradle.api.invocation.Gradle} instance attached to it. Any property reference or method call which is not found
 * on this {@code Script} object is forwarded to the delegate object.</p>
 */
public interface Script {
    /**
     * <p>Configures the delegate object for this script using plugins or scripts. The given closure is used to
     * configure an {@link org.gradle.api.plugins.ObjectConfigurationAction} which is then used to configure the
     * delegate object.</p>
     *
     * @param closure The closure to configure the {@code ObjectConfigurationAction}.
     */
    void apply(Closure closure);

    /**
     * Returns the script handler for this script. You can use this handler to manage the classpath used to compile and
     * execute this script.
     *
     * @return the classpath handler. Never returns null.
     */
    ScriptHandler getBuildscript();

    /**
     * Configures the classpath for this script. The given closure is executed against this script's {@link
     * ScriptHandler}. The {@link ScriptHandler} is passed to the closure as the closure's delegate.
     *
     * @param configureClosure the closure to use to configure the script classpath.
     */
    void buildscript(Closure configureClosure);

    /**
     * <p>Resolves a file path relative to this script. This works as described for {@link Project#file(Object)}</p>
     *
     * @param path The object to resolve as a File.
     * @return The resolved file. Never returns null.
     */
    File file(Object path);

    /**
     * <p>Returns a {@link ConfigurableFileCollection} containing the given files. This works as described for {@link
     * Project#files(Object...)}.</p>
     *
     * @param paths The paths to the files. May be empty.
     * @return The file collection. Never returns null.
     */
    ConfigurableFileCollection files(Object... paths);

    /**
     * <p>Creates a new {@code ConfigurableFileCollection} using the given paths. The file collection is configured
     * using the given closure. This method works as described for {@link Project#files(Object,
     * groovy.lang.Closure)}.</p>
     *
     * @param paths The contents of the file collection. Evaluated as for {@link #files(Object...)}.
     * @param configureClosure The closure to use to configure the file collection.
     * @return the configured file tree. Never returns null.
     */
    ConfigurableFileCollection files(Object paths, Closure configureClosure);

    /**
     * Disables redirection of standard output during script execution. By default redirection is enabled.
     *
     * @see #captureStandardOutput(org.gradle.api.logging.LogLevel)
     */
    void disableStandardOutputCapture();

    /**
     * Starts redirection of standard output during to the logging system during script execution. By default
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
     * Returns the logger for this script. You can use this in your script to write log messages.
     *
     * @return The logger. Never returns null.
     */
    Logger getLogger();
}
