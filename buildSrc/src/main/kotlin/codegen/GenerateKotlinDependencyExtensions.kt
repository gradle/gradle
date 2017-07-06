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

    @get:Input
    var kotlinDslRepository: String? = null

    @Suppress("unused")
    @TaskAction
    fun generate() {
        outputFile!!.writeText(
            """$licenseHeader

package org.gradle.kotlin.dsl

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler

import org.gradle.api.artifacts.repositories.ArtifactRepository

import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec


/**
 * The version of the Kotlin compiler embedded in gradle-kotlin-dsl (currently _${embeddedKotlinVersion}_).
 */
val embeddedKotlinVersion = "$embeddedKotlinVersion"


/**
 * Adds the remote repository containing the Kotlin libraries embedded in gradle-kotlin-dsl.
 */
@Deprecated("Will be removed in 1.0")
fun RepositoryHandler.gradleScriptKotlin(): ArtifactRepository =
    maven { it.setUrl("$kotlinDslRepository") }


/**
 * Builds the dependency notation for the named Kotlin [module] at the given [version].
 *
 * @param module simple name of the Kotlin module, for example "reflect".
 * @param version optional desired version, null implies [embeddedKotlinVersion].
 */
fun DependencyHandler.kotlin(module: String, version: String? = null): Any =
    "org.jetbrains.kotlin:kotlin-${'$'}module:${'$'}{version ?: embeddedKotlinVersion}"


@Deprecated("Will be removed in 1.0", ReplaceWith("kotlin(module, version)"))
fun DependencyHandler.kotlinModule(module: String, version: String? = null): Any =
    kotlin(module, version)


/**
 * Applies the given Kotlin plugin [module] at the (optional) given [version] ([embeddedKotlinVersion] by default).
 *
 * For example: `plugins { kotlin("jvm") }`
 *
 * Visit the [plugin portal](https://plugins.gradle.org/search?term=org.jetbrains.kotlin) to see the list of available plugins.
 *
 * @param module simple name of the Kotlin Gradle plugin module, for example "jvm", "android", "kapt", "plugin.allopen" etc...
 * @param version optional desired version, null implies [embeddedKotlinVersion].
 */
fun PluginDependenciesSpec.kotlin(module: String, version: String? = null): PluginDependencySpec =
    id("org.jetbrains.kotlin.${'$'}module") version (version ?: embeddedKotlinVersion)


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

""")
    }
}


internal
val licenseHeader = """/*
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
 */"""
