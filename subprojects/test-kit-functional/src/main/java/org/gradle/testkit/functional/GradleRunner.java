/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.functional;

import org.gradle.api.Incubating;
import org.gradle.testkit.functional.internal.dist.GradleDistribution;
import org.gradle.testkit.functional.internal.dist.InstalledGradleDistribution;
import org.gradle.testkit.functional.internal.dist.URILocatedGradleDistribution;
import org.gradle.testkit.functional.internal.dist.VersionBasedGradleDistribution;
import org.gradle.testkit.functional.internal.DefaultGradleRunner;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Executes a Gradle build for given tasks and arguments.
 *
 * @since 2.6
 */
@Incubating
public abstract class GradleRunner {
    private File workingDirectory;
    private List<String> arguments = Collections.emptyList();
    private List<String> taskNames = Collections.emptyList();

    /**
     * Returns the working directory for the current build execution.
     *
     * @return Working directory
     */
    public File getWorkingDir() {
        return workingDirectory;
    }

    /**
     * Sets the working directory for the current build execution.
     *
     * @param workingDirectory Working directory
     */
    public void setWorkingDir(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    /**
     * Returns the provided arguments for the build execution. Defaults to an empty List.
     *
     * @return Build execution arguments
     */
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * Sets the arguments used for the build execution.
     *
     * @param arguments Build execution arguments
     */
    public void setArguments(List<String> arguments) {
        this.arguments = arguments;
    }

    /**
     * Defines which tasks should be executed.
     *
     * @param taskNames Task names
     */
    public void setTasks(List<String> taskNames) {
        this.taskNames = taskNames;
    }

    /**
     * Returns the provided task names for build execution. Defaults to an empty List.
     *
     * @return Task names
     */
    public List<String> getTasks() {
        return taskNames;
    }

    /**
     * Executes a build and expects it to finish successfully. Throws a {@link UnexpectedBuildFailure} if build fails unexpectedly.
     *
     * @return Result of the build
     */
    public abstract BuildResult succeeds();

    /**
     * Executes a build and expects it to fail. Throws a {@link UnexpectedBuildSuccess} if build succeeds unexpectedly.
     *
     * @return Result of the build
     */
    public abstract BuildResult fails();

    /**
     * Creates and returns a default implementation of a {@link GradleRunner}. The implementation uses the current Gradle version to execute the build.
     *
     * @return Default implementation
     */
    public static GradleRunner create() {
        return create(VersionBasedGradleDistribution.CURRENT);
    }

    static GradleRunner create(GradleDistribution gradleDistribution) {
        if(!(gradleDistribution instanceof VersionBasedGradleDistribution
            || gradleDistribution instanceof InstalledGradleDistribution
            || gradleDistribution instanceof URILocatedGradleDistribution)) {
            throw new IllegalArgumentException(String.format("Invalid Gradle distribution type: %s", gradleDistribution.getClass().getName()));
        }

        return new DefaultGradleRunner(gradleDistribution);
    }
}
