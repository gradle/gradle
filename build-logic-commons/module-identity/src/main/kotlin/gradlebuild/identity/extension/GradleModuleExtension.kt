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

    @get:Nested
    abstract val identity: ModuleIdentity

    fun identity(action: ModuleIdentity.() -> Unit) {
        action(identity)
    }

    /**
     * Describes the target processes that the code in this module must be able
     * to run on, by definition. All modules that this module depends on will
     * then be required to also support these target runtimes.
     *
     * The required runtimes for a project should be set if that project has
     * specific runtime requirements. For example, they must be able to run in
     * a worker (a compiler worker, test worker, worker API worker action),
     * or must be able to run in a client (TAPI model, TAPI client, CLI client).
     */
    @get:Nested
    abstract val requiredRuntimes: ModuleTargetRuntimes

    fun requiredRuntimes(action: ModuleTargetRuntimes.() -> Unit) {
        action(requiredRuntimes)
    }

    /**
     * The set of runtimes for this module, computed based on the required target
     * runtimes of this module and the required target runtimes of all modules
     * that depend on this module. This set contains a runtime if it is required
     * by this module or if another module that requires that runtime depends on this
     * module.
     */
    @get:Nested
    abstract val computedRuntimes: ModuleTargetRuntimes

    fun computedRuntimes(action: ModuleTargetRuntimes.() -> Unit) {
        action(computedRuntimes)
    }

    /**
     * Declares whether this module is published to an external repository.
     */
    abstract val published: Property<Boolean>

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
     * Declare that this Gradle module runs as part of the wrapper or as part of a client process.
     * Client processes include the Tooling API client or the CLI client.
     */
    val client: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of the Gradle daemon.
     */
    val daemon: Property<Boolean>


    /**
     * Declare that this Gradle module runs as part of worker process.
     */
    val worker: Property<Boolean>
    /**
     * Reduces a map of boolean flag properties to a single provider by applying the given
     * combiner function to the corresponding values of the properties that are true.
     *
     * @param flags The map of boolean properties mapped to their values.
     * @param combiner The function to combine the values of the true properties.
     *
     * @return A provider of the reduced value. Non-present if no properties are true.
     */
    fun <T : Any> Project.reduceBooleanFlagValues(flags: Map<Property<Boolean>, T>, combiner: (T, T) -> T): Provider<T> {
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
