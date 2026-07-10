/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.runtimes

/**
 * Represents a target runtime for code in the Gradle distribution. Code may be declared
 * to run in one or more target runtimes, and is required to be compiled such that it may
 * run in any of the declared environments. In most cases, a target runtime maps to a
 * target java version for the compiled bytecode.
 */
enum class TargetRuntime {
    CLIENT,
    DAEMON,
    WORKER,
}

/**
 * All details from a single project necessary to validate and automate checks and
 * configuration for the target runtimes of a project in the build.
 */
data class TargetRuntimeDetails(
    /**
     * The set of target runtimes this project must explicitly support. [computedRuntimes]
     * contains the full set of runtimes, which includes these explicit runtimes and the
     * implicit runtimes as determined by analyzing the distribution's dependency graph.
     *
     * @see [gradlebuild.identity.extension.GradleModuleExtension.requiredRuntimes]
     */
    val requiredRuntimes: Set<TargetRuntime>,

    /**
     * The complete set of target runtimes that this project needs to support,
     * computed based on the [requiredRuntimes] of all projects in the distribution.
     *
     * @see [gradlebuild.identity.extension.GradleModuleExtension.computedRuntimes]
     */
    val computedRuntimes: Set<TargetRuntime>,

    /**
     * The set of paths of projects that this project depends on at compile-time and runtime.
     */
    val dependencies: Set<String>
)
