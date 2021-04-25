/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;

import java.util.function.Function;

/**
 * Controls the lifecycle of the build tree, allowing a single action to be run against the root build.
 */
public interface BuildTreeLifecycleController {

    /**
     * @return The {@link org.gradle.api.internal.GradleInternal} object that represents the build invocation.
     */
    GradleInternal getGradle();

    /**
     * Runs the given action against an empty build model. Does not attempt to perform any configuration or run any tasks.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     */
    <T> T withEmptyBuild(Function<SettingsInternal, T> action);

    /**
     * Creates the task graph for the tasks specified in the {@link org.gradle.StartParameter} associated with the build, runs the tasks and finishes up the build.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     */
    void run();

    /**
     * Configures the build, but does not schedule or run any tasks, and finishes up the build.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     */
    void configure();
}
