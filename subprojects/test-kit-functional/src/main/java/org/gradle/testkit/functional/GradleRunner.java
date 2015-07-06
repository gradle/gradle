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
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.testkit.functional.internal.DefaultGradleRunner;
import org.gradle.testkit.functional.internal.dist.GradleDistribution;
import org.gradle.testkit.functional.internal.dist.InstalledGradleDistribution;
import org.gradle.testkit.functional.internal.dist.URILocatedGradleDistribution;
import org.gradle.testkit.functional.internal.dist.VersionBasedGradleDistribution;

import java.io.File;
import java.util.List;

/**
 * Executes a Gradle build for given tasks and arguments.
 *
 * @since 2.6
 */
@Incubating
public abstract class GradleRunner {
    /**
     * Returns the Gradle user home directory. Defaults to null which indicates the default location.
     *
     * @return Gradle user home directory
     */
    public abstract File getGradleUserHomeDir();

    /**
     * Sets the Gradle user home directory.
     *
     * @param gradleUserHomeDir Gradle user home directory
     */
    public abstract void setGradleUserHomeDir(File gradleUserHomeDir);

    /**
     * Returns the working directory for the current build execution.
     *
     * @return Working directory
     */
    public abstract File getWorkingDir();

    /**
     * Sets the working directory for the current build execution.
     *
     * @param workingDirectory Working directory
     */
    public abstract void setWorkingDir(File workingDirectory);

    /**
     * Returns the provided arguments for the build execution. Defaults to an empty List.
     *
     * @return Build execution arguments
     */
    public abstract List<String> getArguments();

    /**
     * Sets the arguments used for the build execution.
     *
     * @param arguments Build execution arguments
     */
    public abstract void setArguments(List<String> arguments);

    /**
     * Returns the provided task names for build execution. Defaults to an empty List.
     *
     * @return Task names
     */
    public abstract List<String> getTasks();

    /**
     * Defines which tasks should be executed.
     *
     * @param taskNames Task names
     */
    public abstract void setTasks(List<String> taskNames);

    /**
     * Executes a build and expects it to finish successfully. Throws an {@link UnexpectedBuildFailure} exception if build fails unexpectedly.
     *
     * @return Result of the build
     */
    public abstract BuildResult succeeds();

    /**
     * Executes a build and expects it to fail. Throws an {@link UnexpectedBuildSuccess} exception if build succeeds unexpectedly.
     *
     * @return Result of the build
     */
    public abstract BuildResult fails();

    /**
     * Creates and returns an implementation of a {@link GradleRunner}. The implementation is determined based on the following rules:
     *
     * <p>
     * - When running from a {@code Test} task, use the Gradle installation that is running the build.<br>
     * - When importing into the IDE, use the Gradle installation that performed the import.
     * </p>
     *
     * @return Default implementation
     */
    public static GradleRunner create() {
        DefaultModuleRegistry registry = new DefaultModuleRegistry(GradleRunner.class);
        File gradleHome = registry.getGradleHome();
        return create(new InstalledGradleDistribution(gradleHome));
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
