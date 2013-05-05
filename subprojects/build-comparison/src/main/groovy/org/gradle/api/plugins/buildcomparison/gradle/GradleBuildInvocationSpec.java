/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.gradle;

import org.gradle.api.Incubating;

import java.io.File;
import java.util.List;

/**
 * A specification for launching a Gradle build with a specified Gradle version.
  */
@Incubating
public interface GradleBuildInvocationSpec {

    /**
     * The “root” directory of the build.
     *
     * Defaults to the current build's root directory.
     *
     * @return The “root” project directory of the build. Never null.
     */
    File getProjectDir();

    /**
     * Sets the “root” directory of the build.
     *
     * This should not be the project directory of child project in a multi project build.
     * It should always be the root of the multiproject build.
     *
     * The value is interpreted as a file as per {@link org.gradle.api.Project#file(Object)}.
     *
     * @param projectDir The “root” directory of the build.
     */
    void setProjectDir(Object projectDir);

    /**
     * The Gradle version to run the build with.
     *
     * Defaults to the current Gradle version of the running build.
     *
     * @return The Gradle version to run the build with. Never null.
     */
    String getGradleVersion();

    /**
     * Sets the Gradle version to run the build with.
     *
     * The value must be a valid, published, Gradle version number.
     *
     * Examples are:
     * <ul>
     * <li>{@code "1.1"}</li>
     * <li>{@code "1.0-rc-1"}</li>
     * </ul>
     *
     * @param gradleVersion The Gradle version to run the build with.
     */
    void setGradleVersion(String gradleVersion);

    /**
     * The tasks to execute.
     *
     * Defaults to an empty list.
     *
     * @return The tasks to execute.
     */
    List<String> getTasks();

    /**
     * Sets the tasks to execute.
     *
     * @param tasks The tasks to execute.
     */
    void setTasks(Iterable<String> tasks);

    /**
     * The command line arguments (excluding tasks) to invoke the build with.
     *
     * @return The command line arguments (excluding tasks) to invoke the build with.
     */
    List<String> getArguments();

    /**
     * Sets the command line arguments (excluding tasks) to invoke the build with.
     * @param arguments The command line arguments (excluding tasks) to invoke the build with.
     */
    void setArguments(Iterable<String> arguments);

}
