/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.process;

import java.io.File;
import java.util.Map;

/**
 * <p>Specifies the options to use to fork a process.</p>
 */
public interface ProcessForkOptions {
    /**
     * Returns the name of the executable to use.
     *
     * @return The executable.
     */
    String getExecutable();

    /**
     * Sets the name of the executable to use.
     *
     * @param executable The executable. Must not be null.
     * @since 4.0
     */
    void setExecutable(String executable);

    /**
     * Sets the name of the executable to use.
     *
     * @param executable The executable. Must not be null.
     */
    void setExecutable(Object executable);

    /**
     * Sets the name of the executable to use.
     *
     * @param executable The executable. Must not be null.
     * @return this
     */
    ProcessForkOptions executable(Object executable);

    /**
     * Returns the working directory for the process. Defaults to the project directory.
     *
     * @return The working directory. Never returns null.
     */
    File getWorkingDir();

    /**
     * Sets the working directory for the process.
     *
     * @param dir The working directory. Must not be null.
     * @since 4.0
     */
    void setWorkingDir(File dir);

    /**
     * Sets the working directory for the process. The supplied argument is evaluated as per {@link
     * org.gradle.api.Project#file(Object)}.
     *
     * @param dir The working directory. Must not be null.
     */
    void setWorkingDir(Object dir);

    /**
     * Sets the working directory for the process. The supplied argument is evaluated as per {@link
     * org.gradle.api.Project#file(Object)}.
     *
     * @param dir The working directory. Must not be null.
     * @return this
     */
    ProcessForkOptions workingDir(Object dir);

    /**
     * The environment variables to use for the process. Defaults to the environment of this process.
     *
     * @return The environment. Returns an empty map when there are no environment variables.
     */
    Map<String, Object> getEnvironment();

    /**
     * Sets the environment variable to use for the process.
     *
     * @param environmentVariables The environment variables. Must not be null.
     */
    void setEnvironment(Map<String, ?> environmentVariables);

    /**
     * Adds some environment variables to the environment for this process.
     *
     * @param environmentVariables The environment variables. Must not be null.
     * @return this
     */
    ProcessForkOptions environment(Map<String, ?> environmentVariables);

    /**
     * Adds an environment variable to the environment for this process.
     *
     * @param name The name of the variable.
     * @param value The value for the variable. Must not be null.
     * @return this
     */
    ProcessForkOptions environment(String name, Object value);

    /**
     * Copies these options to the given target options.
     *
     * @param options The target options
     * @return this
     */
    ProcessForkOptions copyTo(ProcessForkOptions options);
}
