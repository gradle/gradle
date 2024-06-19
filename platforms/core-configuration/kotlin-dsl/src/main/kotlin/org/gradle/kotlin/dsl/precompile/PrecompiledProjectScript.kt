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
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderConvertible
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.*
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependency
import org.gradle.plugin.use.PluginDependencySpec


/**
 * Legacy script template definition for precompiled Kotlin scripts targeting [Project] instances.
 */
@Deprecated("Kept for compatibility with precompiled script plugins published with Gradle versions prior to 6.0")
open class PrecompiledProjectScript(
    override val delegate: Project
) : @Suppress("DEPRECATION") org.gradle.kotlin.dsl.support.delegates.ProjectDelegate() {

    init {
        DeprecationLogger.deprecateBehaviour("Applying a Kotlin DSL precompiled script plugin published with Gradle versions < 6.0.")
            .withAdvice("Use a version of the plugin published with Gradle >= 6.0.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "kotlin_dsl_precompiled_gradle_lt_6")
            .nagUser()
    }

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
            object : PluginDependenciesSpec {
                override fun id(id: String): PluginDependencySpec {
                    project.pluginManager.apply(id)
                    return NullPluginDependencySpec
                }

                override fun alias(notation: Provider<PluginDependency>): PluginDependencySpec {
                    project.pluginManager.apply(notation.get().pluginId)
                    return NullPluginDependencySpec
                }

                override fun alias(notation: ProviderConvertible<PluginDependency>): PluginDependencySpec {
                    return alias(notation.asProvider())
                }
            }
        )
    }

    object NullPluginDependencySpec : PluginDependencySpec {
        override fun apply(apply: Boolean) = this
        override fun version(version: String?) = this
    }
}
