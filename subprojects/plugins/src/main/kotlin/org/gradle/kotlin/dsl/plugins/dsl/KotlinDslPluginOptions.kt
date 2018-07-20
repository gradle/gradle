/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory

import org.gradle.kotlin.dsl.*


/**
 * Options for the `kotlin-dsl` plugin.
 *
 * @see KotlinDslPlugin
 */
class KotlinDslPluginOptions internal constructor(objects: ObjectFactory) {

    /**
     * Switch for the `kotlin-dsl` plugin `progressive` mode.
     *
     * The `kotlin-dsl` plugin relies on Kotlin compiler's progressive mode and experimental features
     * to enable among other things SAM conversion for Kotlin functions.
     *
     * Once built and published, artifacts produced by this project will continue to work on future Gradle versions.
     * However, you may have to fix the sources of this project after upgrading the Gradle wrapper of this build.
     *
     * Defaults to [ProgressiveModeState.WARN] which enables SAM conversion for Kotlin functions and issue a warning.
     * Set to [ProgressiveModeState.ENABLED] to silence the warning.
     * Set to [ProgressiveModeState.DISABLED] to disable and give up SAM conversion for Kotlin functions.
     *
     * @see ProgressiveModeState
     * @see KotlinDslPlugin
     */
    val progressive = objects.property<ProgressiveModeState>().apply {
        set(ProgressiveModeState.WARN)
    }
}


/**
 * State of the `kotlin-dsl` plugin `progressive` mode.
 *
 * @see KotlinDslPluginOptions.progressive
 * @see KotlinDslPlugin
 */
enum class ProgressiveModeState {

    /**
     * SAM conversion for Kotlin functions is enabled and a warning is issued.
     * This is the default.
     *
     * @see KotlinDslPluginOptions.progressive
     */
    WARN,

    /**
     * SAM conversion for Kotlin functions is enabled.
     *
     * @see KotlinDslPluginOptions.progressive
     */
    ENABLED,

    /**
     * SAM conversion for Kotlin functions is disabled.
     *
     * @see KotlinDslPluginOptions.progressive
     */
    DISABLED
}


internal
fun Project.kotlinDslPluginOptions(action: KotlinDslPluginOptions.() -> Unit) =
    configure(action)
