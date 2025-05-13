/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.identity.extension

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskContainer
import org.gradle.util.GradleVersion
import java.util.Optional

/**
 * A project extension which describes a module of a Gradle distribution.
 */
abstract class GradleModuleExtension(val tasks: TaskContainer, val objects: ObjectFactory) {

    companion object {
        const val NAME: String = "gradleModule"
    }

    /**
     * Declares whether this module is an entrypoint, and therefore:
     * 1. Its classpath represents a well-known classpath that can be loaded
     *    by Gradle from the distribution.
     * 2. Its target runtimes are definite and are considered a source of truth.
     *    The target runtimes of a non-entrypoint module are the collection of all
     *    target runtimes of all entrypoint modules that depend on it.
     *
     * Entrypoint modules generally contain the main methods for a given process,
     * are immediately loaded by a -main module, or are loaded by Gradle at
     * runtime -- like a worker action implementation (Compiler worker, test worker)
     */
    abstract val entryPoint: Property<Boolean>

    @get:Nested
    abstract val identity: ModuleIdentity

    fun identity(action: ModuleIdentity.() -> Unit) {
        action(identity)
    }

    /**
     * Describes the target processes that the code in this module may run on.
     */
    @get:Nested
    abstract val targetRuntimes: ModuleTargetRuntimes

    fun targetRuntimes(action: ModuleTargetRuntimes.() -> Unit) {
        action(targetRuntimes)
    }

}

interface ModuleIdentity {

    val version: Property<GradleVersion>

    val baseName: Property<String>

    val buildTimestamp: Property<String>

    val snapshot: Property<Boolean>

    val promotionBuild: Property<Boolean>

    val releasedVersions: Property<ReleasedVersionsDetails>

}

interface ModuleTargetRuntimes {

    /**
     * Declare that this Gradle module runs as part of worker process.
     */
    val usedInWorkers: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of the wrapper or as part of a client process.
     * Client processes include the Tooling API client or the CLI client.
     */
    val usedInClient: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of the Gradle daemon.
     */
    val usedInDaemon: Property<Boolean>

    /**
     * Reduces a map of boolean flags to a single property by applying the given combiner function
     * to the corresponding values of the properties that are true.
     *
     * @param flags The map of boolean properties to their values.
     * @param combiner The function to combine the values of the true properties.
     *
     * @return A property that contains the reduced value.
     */
    fun <T: Any> Project.reduceBooleanFlagValues(flags: Map<Property<Boolean>, T>, combiner: (T, T) -> T): Provider<T> {
        return flags.entries
            .map { entry ->
                entry.key.map {
                    when (it) {
                        true -> Optional.of(entry.value)
                        false -> Optional.empty()
                    }
                }.orElse(provider {
                    throw GradleException("Expected boolean flag to be configured")
                })
            }
            .reduce { acc, next ->
                acc.zip(next) { left , right ->
                    when {
                        !left.isPresent -> right
                        !right.isPresent -> left
                        else -> Optional.of(combiner(left.get(), right.get()))
                    }
                }
            }
            .map { it.orElse(null) }
    }
}
