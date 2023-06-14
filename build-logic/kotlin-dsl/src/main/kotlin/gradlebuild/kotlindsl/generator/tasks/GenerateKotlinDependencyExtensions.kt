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

package gradlebuild.kotlindsl.generator.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault

import java.io.File


@Suppress("unused")
@DisableCachingByDefault(because = "Not worth caching")
abstract class GenerateKotlinDependencyExtensions : CodeGenerationTask() {

    @get:Input
    abstract val embeddedKotlinVersion: Property<String>

    @get:Input
    abstract val kotlinDslPluginsVersion: Property<String>

    override fun File.writeFiles() {

        val kotlinDslPluginsVersion = kotlinDslPluginsVersion.get()
        val embeddedKotlinVersion = embeddedKotlinVersion.get()

        // IMPORTANT: kotlinDslPluginsVersion should NOT be made a `const` to avoid inlining
        writeFile(
            "org/gradle/kotlin/dsl/support/KotlinDslPlugins.kt",
            """$licenseHeader

package org.gradle.kotlin.dsl.support


/**
 * Expected version of the `kotlin-dsl` plugin for the running Gradle version.
 */
@Suppress("unused")
val expectedKotlinDslPluginsVersion: String
    get() = "$kotlinDslPluginsVersion"
"""
        )

        writeFile(
            "org/gradle/kotlin/dsl/KotlinDependencyExtensions.kt",
            """$licenseHeader

package org.gradle.kotlin.dsl

import org.gradle.api.Incubating
import org.gradle.api.artifacts.dsl.DependencyHandler

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec


/**
 * The version of the Kotlin compiler embedded in gradle-kotlin-dsl (currently _${embeddedKotlinVersion}_).
 */
val embeddedKotlinVersion = "$embeddedKotlinVersion"


/**
 * Builds the dependency notation for the named Kotlin [module] at the embedded version (currently _${embeddedKotlinVersion}_).
 *
 * @param module simple name of the Kotlin module, for example "reflect".
 */
fun DependencyHandler.embeddedKotlin(module: String): Any =
    kotlin(module, embeddedKotlinVersion)


/**
 * Builds the dependency notation for the named Kotlin [module] at the given [version].
 *
 * @param module simple name of the Kotlin module, for example "reflect".
 * @param version optional desired version, unspecified if null.
 */
fun DependencyHandler.kotlin(module: String, version: String? = null): Any =
    "org.jetbrains.kotlin:kotlin-${'$'}module${'$'}{version?.let { ":${'$'}version" } ?: ""}"


/**
 * Applies the given Kotlin plugin [module] at the embedded version (currently _${embeddedKotlinVersion}_).
 *
 * For example: `plugins { embeddedKotlin("plugin.serialization") }`
 *
 * Visit the [plugin portal](https://plugins.gradle.org/search?term=org.jetbrains.kotlin) to see the list of available plugins.
 *
 * @param module simple name of the Kotlin Gradle plugin module, for example "jvm", "android", "kapt", "plugin.allopen" etc...
 * @since 8.3
 */
@Incubating
fun PluginDependenciesSpec.embeddedKotlin(module: String): PluginDependencySpec =
    id("org.jetbrains.kotlin.${'$'}module") version embeddedKotlinVersion


/**
 * Applies the given Kotlin plugin [module].
 *
 * For example: `plugins { kotlin("jvm") version "$embeddedKotlinVersion" }`
 *
 * Visit the [plugin portal](https://plugins.gradle.org/search?term=org.jetbrains.kotlin) to see the list of available plugins.
 *
 * @param module simple name of the Kotlin Gradle plugin module, for example "jvm", "android", "kapt", "plugin.allopen" etc...
 */
fun PluginDependenciesSpec.kotlin(module: String): PluginDependencySpec =
    id("org.jetbrains.kotlin.${'$'}module")


/**
 * The `embedded-kotlin` plugin.
 *
 * Equivalent to `id("org.gradle.kotlin.embedded-kotlin") version "$kotlinDslPluginsVersion"`
 *
 * @see org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin
 */
val PluginDependenciesSpec.`embedded-kotlin`: PluginDependencySpec
    get() = id("org.gradle.kotlin.embedded-kotlin") version "$kotlinDslPluginsVersion"


/**
 * The `kotlin-dsl` plugin.
 *
 * Equivalent to `id("org.gradle.kotlin.kotlin-dsl") version "$kotlinDslPluginsVersion"`
 *
 * @see org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin
 */
val PluginDependenciesSpec.`kotlin-dsl`: PluginDependencySpec
    get() = id("org.gradle.kotlin.kotlin-dsl") version "$kotlinDslPluginsVersion"


/**
 * The `kotlin-dsl.base` plugin.
 *
 * Equivalent to `id("org.gradle.kotlin.kotlin-dsl.base") version "$kotlinDslPluginsVersion"`
 *
 * @see org.gradle.kotlin.dsl.plugins.base.KotlinDslBasePlugin
 */
val PluginDependenciesSpec.`kotlin-dsl-base`: PluginDependencySpec
    get() = id("org.gradle.kotlin.kotlin-dsl.base") version "$kotlinDslPluginsVersion"


/**
 * The `kotlin-dsl.precompiled-script-plugins` plugin.
 *
 * Equivalent to `id("org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins") version "$kotlinDslPluginsVersion"`
 *
 * @see org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins
 */
val PluginDependenciesSpec.`kotlin-dsl-precompiled-script-plugins`: PluginDependencySpec
    get() = id("org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins") version "$kotlinDslPluginsVersion"
"""
        )
    }
}
