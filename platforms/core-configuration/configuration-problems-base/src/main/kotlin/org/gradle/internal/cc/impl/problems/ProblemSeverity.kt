/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.problems

enum class ProblemSeverity {

    /**
     * Problems that are reported to the user sometime after they are discovered,
     * but which will fail the build, unless [warning-mode][org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption.Value.WARN]
     * is active.
     *
     * Collecting deferred problems is useful to provide the user with the overview
     * of potentially many things that make the build not compatible with Configuration Cache,
     * instead of failing the build on the first encounter. Many serialization problems
     * fall into this category.
     */
    Deferred,

    /**
     * Problems that interrupt the current operation immediately after being discovered and recorded.
     *
     * The exception is normally turned into a dedicated build failure.
     * These problems are still present in the report and can appear in the console summary.
     */
    Interrupting,

    /**
     * A problem produced by a task marked as [notCompatibleWithConfigurationCache][org.gradle.api.Task.notCompatibleWithConfigurationCache].
     */
    Suppressed,

    /**
     * A problem produced by a task, requested Configuration Cache[degradation][org.gradle.api.internal.ConfigurationCacheDegradationController.requireConfigurationCacheDegradation].
     */
    SuppressedSilently
}
