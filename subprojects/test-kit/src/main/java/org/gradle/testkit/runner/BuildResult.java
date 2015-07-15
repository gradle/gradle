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

package org.gradle.testkit.runner;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * The result of executing a build, via the {@link GradleRunner}.
 *
 * @since 2.6
 * @see GradleRunner#build()
 * @see GradleRunner#buildAndFail()
 */
@Incubating
public interface BuildResult {

    /**
     * The textual output produced during the build.
     * <p>
     * This is equivalent to the console output produced when running a build from the command line,
     * except for any error output (which is available via {@link #getStandardError()}).
     *
     * @return the build output text, or empty string if there was no build output (e.g. ran with {@code -q})
     */
    String getStandardOutput();

    /**
     * The textual error output produced during the build (i.e. text written to {@link System#err}).
     * <p>
     * During a build, Gradle itself does not write its output to the error output stream.
     * However, tools used by the build, as well as processes forked by the build
     * (who's output is forwarded) may write to the error output stream.
     * <p>
     * If the build fails to start, due to an invalid argument for example, the message will be written to the error output
     * and hence available here.
     *
     * @return the build error output text, or empty string if there was no error output
     */
    String getStandardError();

    /**
     * The tasks that were part of the build.
     * <p>
     * The order of the tasks corresponds to the order in which the tasks were started.
     * If executing a parallel enabled build, the order is not guaranteed to be deterministic.
     * <p>
     * The returned list will be empty if no tasks were executed.
     * This can occur if the build fails early, due to a build script failing to compile for example.
     *
     * @return the build tasks
     */
    List<BuildTask> getTasks();

    /**
     * The subset of {@link #getTasks()} that had the given result.
     * <p>
     * The returned list will be empty if no tasks were executed that completed with the given result.
     *
     * @param result the desired result
     * @return the build tasks that completed with the given result
     */
    List<BuildTask> tasks(TaskResult result);

    /**
     * The paths of the subset of {@link #getTasks()} that had the given result.
     * <p>
     * The returned list will be empty if no tasks were executed that completed with the given result.
     *
     * @param result the desired result
     * @return the paths of the build tasks that completed with the given result
     */
    List<String> taskPaths(TaskResult result);

}
