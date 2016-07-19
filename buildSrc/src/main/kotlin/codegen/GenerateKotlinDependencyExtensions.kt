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
    var gradleScriptKotlinRepository: String? = null

    @TaskAction
    fun generate() {
        outputFile!!.writeText(
            """$licenseHeader

package org.gradle.script.lang.kotlin

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler

import org.gradle.api.artifacts.repositories.ArtifactRepository

/**
 * The version of the Kotlin compiler embedded in gradle-script-kotlin (currently _${embeddedKotlinVersion}_).
 */
val embeddedKotlinVersion = "$embeddedKotlinVersion"

/**
 * Adds the remote repository containing the Kotlin libraries embedded in gradle-script-kotlin.
 */
fun RepositoryHandler.gradleScriptKotlin(): ArtifactRepository =
    maven { it.setUrl("$gradleScriptKotlinRepository") }

/**
 * Builds the dependency notation for the named Kotlin [module] at the given [version].
 *
 * @param module simple name of the Kotlin module, for example "reflect".
 * @param version optional desired version, null implies [embeddedKotlinVersion].
 */
fun DependencyHandler.kotlinModule(module: String, version: String? = null): Any =
    "org.jetbrains.kotlin:kotlin-${'$'}module:${'$'}{version ?: embeddedKotlinVersion}"
""")
    }
}

internal val licenseHeader = """/*
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
