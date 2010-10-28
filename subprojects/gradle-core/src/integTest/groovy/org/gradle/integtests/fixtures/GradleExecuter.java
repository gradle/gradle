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
package org.gradle.integtests.fixtures;

import org.gradle.util.Jvm;

import java.io.File;
import java.util.List;
import java.util.Map;

public interface GradleExecuter {
    GradleExecuter inDirectory(File directory);

    /**
     * Enables search upwards. Defaults to false.
     */
    GradleExecuter withSearchUpwards();

    /**
     * Sets the task names to execute. Defaults to an empty list.
     */
    GradleExecuter withTasks(String... names);

    /**
     * Sets the task names to execute. Defaults to an empty list.
     */
    GradleExecuter withTasks(List<String> names);

    GradleExecuter withTaskList();

    GradleExecuter withDependencyList();

    GradleExecuter withQuietLogging();

    /**
     * Sets the additional command-line arguments to use when executing the build. Defaults to an empty list.
     */
    GradleExecuter withArguments(String... args);

    /**
     * Sets the additional command-line arguments to use when executing the build. Defaults to an empty list.
     */
    GradleExecuter withArguments(List<String> args);

    GradleExecuter withEnvironmentVars(Map<String, ?> environment);

    GradleExecuter usingSettingsFile(File settingsFile);

    GradleExecuter usingInitScript(File initScript);

    GradleExecuter usingBuildScript(String script);

    /**
     * Sets the user home dir. Set to null to use the default user home dir.
     */
    GradleExecuter withUserHomeDir(File userHomeDir);

    /**
     * Sets the executable to use. Set to null to use the default executable (if any)
     */
    GradleExecuter usingExecutable(String script);

    /**
     * Executes the requested build, asserting that the build succeeds. Resets the configuration of this executer.
     *
     * @return The result.
     */
    ExecutionResult run();

    /**
     * Executes the requested build, asserting that the build fails. Resets the configuration of this executer.
     *
     * @return The result.
     */
    ExecutionFailure runWithFailure();

    boolean worksWith(Jvm jvm);
}
