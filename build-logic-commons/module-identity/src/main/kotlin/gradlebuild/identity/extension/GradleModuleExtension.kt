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

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskContainer
import org.gradle.util.GradleVersion

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
     * Declare that this Gradle module runs as part of an entrypoint to user-executed
     * processes, and therefore should compile to a lower version of Java --
     * in order to ensure comprehensible error messages when executing Gradle
     * on an unsupported JVM version.
     * <p>
     * This runtime target should only be used by the various
     * "-main" modules containing main methods.
     */
    val usedForStartup: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of the Gradle Wrapper.
     */
    val usedInWrapper: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of worker process.
     */
    val usedInWorkers: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of a client process, such
     * as the Tooling API client or CLI client.
     */
    val usedInClient: Property<Boolean>

    /**
     * Declare that this Gradle module runs as part of the Gradle daemon.
     */
    val usedInDaemon: Property<Boolean>

}
