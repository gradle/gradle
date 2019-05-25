/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.kotlin.dsl.provider

import org.gradle.api.PathValidation
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.CopySpec
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileTree
import org.gradle.api.logging.Logger
import org.gradle.api.logging.LoggingManager
import org.gradle.api.resources.ResourceHandler
import org.gradle.api.tasks.WorkResult
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import org.gradle.process.JavaExecSpec

import java.io.File
import java.net.URI


/**
 * Base contract for all Gradle Kotlin DSL scripts.
 *
 * This is the Kotlin flavored equivalent of [org.gradle.api.Script].
 *
 * It is not implemented directly by script templates to overcome ambiguous conflicts and Kotlin language
 * limitations. See each script template for actual implementations.
 *
 * `ScriptApiTest` validates that each script template provide compatible methods/properties.
 *
 * Members here must not use default parameters values.
 * Documentation should go to the actual implementations so that it shows up in IDEs properly.
 */
@Suppress("unused")
internal
interface ScriptApi {

    val logger: Logger
    val logging: LoggingManager
    val resources: ResourceHandler

    fun relativePath(path: Any): String
    fun uri(path: Any): URI

    fun file(path: Any): File
    fun file(path: Any, validation: PathValidation): File
    fun files(vararg paths: Any): ConfigurableFileCollection
    fun files(paths: Any, configuration: ConfigurableFileCollection.() -> Unit): ConfigurableFileCollection

    fun fileTree(baseDir: Any): ConfigurableFileTree
    fun fileTree(baseDir: Any, configuration: ConfigurableFileTree.() -> Unit): ConfigurableFileTree
    fun zipTree(zipPath: Any): FileTree
    fun tarTree(tarPath: Any): FileTree

    fun copy(configuration: CopySpec.() -> Unit): WorkResult
    fun copySpec(): CopySpec
    fun copySpec(configuration: CopySpec.() -> Unit): CopySpec

    fun mkdir(path: Any): File

    fun delete(vararg paths: Any): Boolean
    fun delete(configuration: DeleteSpec.() -> Unit): WorkResult

    fun exec(configuration: ExecSpec.() -> Unit): ExecResult
    fun javaexec(configuration: JavaExecSpec.() -> Unit): ExecResult
}
