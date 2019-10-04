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

package org.gradle.kotlin.dsl.precompile

import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.delegates.ProjectDelegate
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec


/**
 * Legacy script template definition for precompiled Kotlin scripts targeting [Project] instances.
 */
@Deprecated("Kept for compatibility with precompiled script plugins published with Gradle versions prior to 6.0")
open class PrecompiledProjectScript(
    override val delegate: Project
) : ProjectDelegate() {

    /**
     * Configures the build script classpath for this project.
     *
     * @see [Project.buildscript]
     */
    @Suppress("unused")
    open fun buildscript(@Suppress("unused_parameter") block: ScriptHandlerScope.() -> Unit) {
        throw IllegalStateException("The `buildscript` block is not supported on Kotlin script plugins, please use the `plugins` block or project level dependencies.")
    }

    /**
     * Configures the plugin dependencies for this project.
     *
     * @see [PluginDependenciesSpec]
     */
    @Suppress("unused")
    fun plugins(@Suppress("unused_parameter") block: PluginDependenciesSpec.() -> Unit) {
        block(
            PluginDependenciesSpec { pluginId ->
                project.pluginManager.apply(pluginId)
                NullPluginDependencySpec
            })
    }

    object NullPluginDependencySpec : PluginDependencySpec {
        override fun apply(apply: Boolean) = this
        override fun version(version: String?) = this
    }
}
