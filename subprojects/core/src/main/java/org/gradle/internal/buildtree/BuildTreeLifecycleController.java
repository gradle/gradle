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

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Controls the lifecycle of the build tree, allowing a single action to be run against the build tree.
 */
public interface BuildTreeLifecycleController {
    /**
     * Performs some setup of the mutable model, prior to any execution. Must be called prior to one of the other methods.
     */
    void beforeBuild(Consumer<? super GradleInternal> action);

    /**
     * Runs the given action against an empty build model. Does not attempt to perform any configuration or run any tasks.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     */
    <T> T withEmptyBuild(Function<? super SettingsInternal, T> action);

    /**
     * Schedules the work graph for the tasks specified in the {@link org.gradle.StartParameter} associated with the build, runs the scheduled work and finishes up the build.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     *
     * <p>This method may or may nor configure the build. When a cached task graph is available, this may be used instead of configuring the build.
     */
    void scheduleAndRunTasks();

    /**
     * Configures the build, optionally schedules and runs any tasks specified in the {@link org.gradle.StartParameter} associated with the build, calls the given action and finally finishes up the build.
     * When this method returns, all user code will have completed, including 'build finished' hooks.
     *
     * <p>This method may or may not run the action. When a cached result is available, this may be used instead of configuring the build and running the action.</p>
     *
     * <p>Does not call the given action when task execution fails.
     */
    <T> T fromBuildModel(boolean runTasks, BuildTreeModelAction<? extends T> action);
}
