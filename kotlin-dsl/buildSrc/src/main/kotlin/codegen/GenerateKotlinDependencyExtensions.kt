/*
 * Copyright 2016 the original author or authors.
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

package codegen

import org.gradle.api.DefaultTask

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.io.File


open class GenerateKotlinDependencyExtensions : DefaultTask() {

    @get:OutputFile
    var outputFile: File? = null

    @get:Input
    var embeddedKotlinVersion: String? = null

    @get:Input
    var kotlinDslPluginsVersion: String? = null

    @Suppress("unused")
    @TaskAction
    fun generate() {
        outputFile!!.writeText(
            """$licenseHeader

package org.gradle.kotlin.dsl

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
 * You can also use `` `embedded-kotlin` version "$kotlinDslPluginsVersion" `` if you want to use a different version.
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
 * You can also use `` `kotlin-dsl` version "$kotlinDslPluginsVersion" `` if you want to use a different version.
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
 * You can also use `` `kotlin-dsl-base` version "$kotlinDslPluginsVersion" `` if you want to use a different version.
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
 * You can also use `` `kotlin-dsl-precompiled-script-plugins` version "$kotlinDslPluginsVersion" `` if you want to use a different version.
 *
 * @see org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins
 */
val PluginDependenciesSpec.`kotlin-dsl-precompiled-script-plugins`: PluginDependencySpec
    get() = id("org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins") version "$kotlinDslPluginsVersion"
""")
    }
}


internal
const val licenseHeader = """/*
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
 */"""
