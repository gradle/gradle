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

package org.gradle.script.lang.kotlin

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.RepositoryHandler

import org.gradle.api.file.FileCollection

import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal

import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.PluginManager

import org.gradle.script.lang.kotlin.support.gradleScriptKotlinApiOf

import java.io.File

import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * Applies the given plugin. Does nothing if the plugin has already been applied.
 *
 * The given class should implement the [Plugin] interface, and be parameterized for a
 * compatible type of `this`.
 *
 * @param T the plugin type.
 * @see PluginManager.apply
 */
inline fun <reified T : Plugin<Project>> Project.apply() =
    pluginManager.apply(T::class.java)

/**
 * Applies a script to the project.
 *
 * @param script the script to apply. Evaluated as per [Project.file]. However, note that
 * a URL can also be used, allowing the script to be fetched using HTTP, for example.
 */
fun Project.applyFrom(script: Any) =
    apply { it.from(script) }

/**
 * Executes the given configuration block against the [plugin convention]
 * [Convention.getPlugin] or extension of the specified type.
 *
 * @param T the plugin convention type.
 * @param configuration the configuration block.
 * @see Convention.getPlugin
 */
inline fun <reified T : Any> Project.configure(configuration: T.() -> Unit) =
    configure(T::class, configuration)

inline fun <T : Any> Project.configure(extensionType: KClass<T>, configuration: T.() -> Unit) =
    the(extensionType).configuration()

/**
 * Returns the plugin convention or extension of the specified type.
 */
inline fun <reified T : Any> Project.the() =
    the(T::class)

@Suppress("nothing_to_inline") // required to avoid a ClassLoader conflict on KClass
inline fun <T : Any> Project.the(extensionType: KClass<T>) =
    convention.findPlugin(extensionType.java) ?: convention.getByType(extensionType.java)!!

inline fun <reified T : Any> Convention.getPlugin() =
    getPlugin(T::class)

@Suppress("nothing_to_inline") // required to avoid a ClassLoader conflict on KClass
inline fun <T : Any> Convention.getPlugin(conventionType: KClass<T>) =
    getPlugin(conventionType.java)!!

inline fun <reified T : Task> Project.task(name: String, noinline configuration: T.() -> Unit) =
    task(name, T::class, configuration)

inline fun <reified T : Task> Project.task(name: String) =
    tasks.create(name, T::class.java)

fun <T : Task> Project.task(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    createTask(name, type, configuration)

fun Project.task(name: String, configuration: Task.() -> Unit) =
    createTask(name, DefaultTask::class, configuration)

fun <T : Task> Project.createTask(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    tasks.create(name, type.java, configuration)!!

/**
 * Configures the repositories for this project.
 *
 * Executes the given configuration block against the [RepositoryHandler] for this
 * project.
 *
 * @param configuration the configuration block.
 */
fun Project.repositories(configuration: KotlinRepositoryHandler.() -> Unit) =
    KotlinRepositoryHandler(repositories).configuration()

/**
 * Configures the dependencies for this project.
 *
 * Executes the given configuration block against the [KotlinDependencyHandler] for this
 * project.
 *
 * @param configuration the configuration block.
 */
fun Project.dependencies(configuration: KotlinDependencyHandler.() -> Unit) =
    KotlinDependencyHandler(dependencies).configuration()

/**
 * Locates a [Project] property using [Project.findProperty].
 */
operator fun Project.getValue(any: Any, property: KProperty<*>): Any? =
    findProperty(property.name)

/**
 * Creates a dependency on the API of the current version of Gradle Script Kotlin.
 *
 * Includes the Kotlin and Gradle APIs.
 *
 * @return The dependency.
 */
fun Project.gradleScriptKotlinApi(): Dependency =
    DefaultSelfResolvingDependency(
        fileCollectionOf(
            gradleScriptKotlinApiOf(project),
            "gradleScriptKotlinApi") as FileCollectionInternal)

private fun fileCollectionOf(files: Collection<File>, name: String): FileCollection =
    DefaultFileCollectionFactory().fixed(name, files)
