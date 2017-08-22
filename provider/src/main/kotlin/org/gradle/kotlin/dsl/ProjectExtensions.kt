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

package org.gradle.kotlin.dsl

import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.RepositoryHandler

import org.gradle.api.file.FileCollection

import org.gradle.api.initialization.dsl.ScriptHandler

import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.file.DefaultFileCollectionFactory
import org.gradle.api.internal.file.FileCollectionInternal

import org.gradle.api.plugins.Convention
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.plugins.PluginManager

import org.gradle.api.provider.PropertyState

import org.gradle.api.tasks.TaskContainer

import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf

import java.io.File

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/**
 * Sets the the default tasks of this project. These are used when no tasks names are provided when
 * starting the build.
 */
inline
fun Project.defaultTasks(vararg tasks: Task) {
    defaultTasks(*tasks.map { it.name }.toTypedArray())
}


/**
 * Applies zero or more plugins or scripts.
 *
 * @param block code to configure an [ObjectConfigurationAction] before executing it
 *
 * @see Project.apply
 */
inline
fun Project.apply(crossinline block: ObjectConfigurationAction.() -> Unit) =
    apply({ it.block() })


/**
 * Applies the given plugin. Does nothing if the plugin has already been applied.
 *
 * The given class should implement the [Plugin] interface, and be parameterized for a
 * compatible type of `this`.
 *
 * @param T the plugin type.
 * @see PluginManager.apply
 */
inline
fun <reified T : Plugin<Project>> Project.apply(): Unit =
    pluginManager.apply(T::class.java)


/**
 * Executes the given configuration block against the [plugin convention]
 * [Convention.getPlugin] or extension of the specified type.
 *
 * @param T the plugin convention type.
 * @param configuration the configuration block.
 * @see Convention.getPlugin
 */
inline
fun <reified T : Any> Project.configure(noinline configuration: T.() -> Unit) =
    convention.findPlugin(T::class.java)?.let(configuration)
        ?: convention.configure(T::class.java, configuration)


/**
 * Returns the plugin convention or extension of the specified type.
 */
inline
fun <reified T : Any> Project.the() =
    the(T::class)


fun <T : Any> Project.the(extensionType: KClass<T>) =
    convention.findPlugin(extensionType.java) ?: convention.getByType(extensionType.java)!!


/**
 * Creates a [Task] with the given [name] and [type], configures it with the given [configuration] action,
 * and adds it to this project tasks container.
 */
inline
fun <reified type : Task> Project.task(name: String, noinline configuration: type.() -> Unit) =
    task(name, type::class, configuration)


/**
 * Creates a [Task] with the given [name] and [type], and adds it to this project tasks container.
 *
 * @see Project.getTasks
 * @see TaskContainer.create
 */
inline
fun <reified type : Task> Project.task(name: String) =
    tasks.create(name, type::class.java)


fun <T : Task> Project.task(name: String, type: KClass<T>, configuration: T.() -> Unit) =
    createTask(name, type, configuration)


/**
 * Creates a [Task] with the given [name ] and [DefaultTask] type, configures it with the given [configuration] action,
 * and adds it to this project tasks container.
 */
fun Project.task(name: String, configuration: Task.() -> Unit): DefaultTask =
    createTask(name, DefaultTask::class, configuration)


fun <T : Task> Project.createTask(name: String, type: KClass<T>, configuration: T.() -> Unit): T =
    tasks.create(name, type.java, configuration)


/**
 * Configures the repositories for this project.
 *
 * Executes the given configuration block against the [RepositoryHandler] for this
 * project.
 *
 * @param configuration the configuration block.
 */
fun Project.repositories(configuration: RepositoryHandler.() -> Unit) =
    repositories.configuration()


fun ScriptHandler.repositories(configuration: RepositoryHandler.() -> Unit) =
    repositories.configuration()


/**
 * Configures the dependencies for this project.
 *
 * Executes the given configuration block against the [DependencyHandlerScope] for this
 * project.
 *
 * @param configuration the configuration block.
 */
fun Project.dependencies(configuration: DependencyHandlerScope.() -> Unit) =
    DependencyHandlerScope(dependencies).configuration()


/**
 * Locates a [Project] property using [Project.findProperty].
 */
operator fun Project.getValue(any: Any, property: KProperty<*>): Any? =
    findProperty(property.name)


/**
 * Creates a [PropertyState] that holds values of the given type [T].
 *
 * @see Project.property
 */
@Incubating
inline
fun <reified T> Project.property(): PropertyState<T> =
    property(T::class.java)


/**
 * Creates a dependency on the API of the current version of the Gradle Kotlin DSL.
 *
 * Includes the Kotlin and Gradle APIs.
 *
 * @return The dependency.
 */
fun Project.gradleKotlinDsl(): Dependency =
    DefaultSelfResolvingDependency(
        fileCollectionOf(
            gradleKotlinDslOf(project),
            "gradleKotlinDsl") as FileCollectionInternal)


@Deprecated("Will be removed in 1.0", ReplaceWith("gradleKotlinDsl()"))
fun Project.gradleScriptKotlinApi(): Dependency =
    gradleKotlinDsl()


private
fun fileCollectionOf(files: Collection<File>, name: String): FileCollection =
    DefaultFileCollectionFactory().fixed(name, files)
