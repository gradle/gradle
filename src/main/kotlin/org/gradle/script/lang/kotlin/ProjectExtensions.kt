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
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.PluginManager

import kotlin.reflect.KClass

/**
 * Applies the given plugin. Does nothing if the plugin has already been applied.
 *
 * The given class should implement the [Plugin] interface, and be parameterized for a compatible type of `this`.
 *
 * @param T the plugin type.
 *
 * @see PluginManager.apply
 */
inline fun <reified T : Plugin<Project>> Project.apply() =
    pluginManager.apply(T::class.java)

/**
 * Executes the given configuration block against the [plugin convention][Convention.getPlugin] of the specified type.
 *
 * @param T the plugin convention type.
 * @param configuration the configuration block.
 *
 * @see Convention.getPlugin
 */
inline fun <reified T : Any> Project.configure(configuration: T.() -> Unit) =
    configure(T::class, configuration)

inline fun <T : Any> Project.configure(extensionType: KClass<T>, configuration: T.() -> Unit) =
    configuration(convention.getPlugin(extensionType.java))

inline fun <reified T : Task> Project.task(name: String, noinline configuration: T.() -> Unit) =
    task(name, T::class, configuration)

inline fun <reified T : Task> Project.task(name: String) =
    tasks.create(name, T::class.java)

fun <T : Task> Project.task(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    createTask(name, type, configuration)

fun Project.task(name: String, configuration: Task.() -> Unit) =
    createTask(name, DefaultTask::class, configuration)

fun <T : Task> Project.createTask(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    tasks.create(name, type.java, configuration)

/**
 * Configures the repositories for this project.
 *
 * Executes the given configuration block against the [RepositoryHandler] for this project.
 *
 * @param configuration the configuration block.
 */
inline fun Project.repositories(configuration: RepositoryHandler.() -> Unit) = configuration(repositories)

/**
 * Configures the dependencies for this project.
 *
 * Executes the given configuration block against the [KotlinDependencyHandler] for this project.
 *
 * @param configuration the configuration block.
 */
inline fun Project.dependencies(configuration: KotlinDependencyHandler.() -> Unit) =
    configuration(KotlinDependencyHandler(dependencies))

/**
 * @see DependencyHandler
 */
class KotlinDependencyHandler(val dependencies: DependencyHandler) : DependencyHandler by dependencies {

    /**
     * Adds a dependency to the given configuration.
     *
     * @param dependencyNotation notation for the dependency to be added.
     * @return The dependency.
     *
     * @see DependencyHandler.add
     */
    operator fun String.invoke(dependencyNotation: String) =
        dependencies.add(this, dependencyNotation)
}

