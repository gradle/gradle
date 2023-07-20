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

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.RepositoryHandler

import org.gradle.api.initialization.dsl.ScriptHandler

import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.file.FileCollectionInternal

import org.gradle.api.plugins.PluginAware

import org.gradle.api.tasks.TaskContainer
import org.gradle.internal.Factory
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier
import org.gradle.internal.deprecation.DeprecationLogger

import org.gradle.kotlin.dsl.provider.fileCollectionOf
import org.gradle.kotlin.dsl.provider.gradleKotlinDslOf
import org.gradle.kotlin.dsl.support.ScriptHandlerScopeInternal
import org.gradle.kotlin.dsl.support.invalidPluginsCall

import org.gradle.plugin.use.PluginDependenciesSpec

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/**
 * Configures the build script classpath for this project.
 */
fun Project.buildscript(action: ScriptHandlerScope.() -> Unit): Unit =
    ScriptHandlerScopeInternal(project, buildscript).action()


/**
 * Sets the default tasks of this project. These are used when no tasks names are provided when
 * starting the build.
 */
@Suppress("nothing_to_inline")
inline fun Project.defaultTasks(vararg tasks: Task) {
    defaultTasks(*tasks.map { it.name }.toTypedArray())
}


/**
 * Applies the plugin of the given type [T]. Does nothing if the plugin has already been applied.
 *
 * The given class should implement the [Plugin] interface, and be parameterized for a
 * compatible type of `this`.
 *
 * @param T the plugin type.
 * @see [PluginAware.apply]
 */
inline fun <reified T : Plugin<Project>> Project.apply() =
    (this as PluginAware).apply<T>()


/**
 * Executes the given configuration block against the [project extension]
 * [org.gradle.api.plugins.ExtensionAware] of the specified type.
 *
 * If no extension is found, configures a project convention if available.
 * Note, that the concept of conventions is deprecated and scheduled for
 * removal in Gradle 9.
 *
 * @param T the project extension type.
 * @param configuration the configuration block.
 * @see [org.gradle.api.plugins.ExtensionAware]
 */
inline fun <reified T : Any> Project.configure(noinline configuration: T.() -> Unit): Unit =
    typeOf<T>().let { type ->
        // Find and configure extension
        extensions.findByType(type)?.let(configuration)
            ?: Factory {
                // Find and configure convention
                // Reflective look up to still support plugins that inlined this function once conventions will be removed
                this::class.java.methods
                    .firstOrNull { it.name == "getConvention" }
                    ?.invoke(this)
                    ?.let { convention ->
                        convention::class.java
                            .getMethod("findPlugin", Class::class.java)
                            .invoke(convention, T::class.java)
                    }
            }.let { findPlugin ->
                DeprecationLogger.whileDisabled(findPlugin)?.let { plugin ->
                    // Second lookup to trigger deprecation warning if convention is found
                    findPlugin.create()
                    @Suppress("UNCHECKED_CAST")
                    configuration(plugin as T)
                }
            }
            // Configure the non-existent extension for error handling
            ?: extensions.configure(type, configuration)
    }


/**
 * Returns the [project extension][org.gradle.api.plugins.ExtensionAware] of the specified type.
 *
 * If no extension is found, returns a project convention if available.
 * Note, that the concept of conventions is deprecated and scheduled for
 * removal in Gradle 9.
 *
 * @param T the project extension type.
 * @see [org.gradle.api.plugins.ExtensionAware]
 */
inline fun <reified T : Any> Project.the(): T =
    typeOf<T>().let { type ->
        // Find extension
        extensions.findByType(type)
            ?: Factory {
                // Find convention
                // Reflective look up to still support plugins that inlined this function once conventions will be removed
                this::class.java.methods.firstOrNull { it.name == "getConvention" }?.invoke(this)?.let { convention ->
                    convention::class.java.getMethod("findPlugin", Class::class.java).invoke(convention, T::class.java)
                }
            }.let { findPlugin ->
                DeprecationLogger.whileDisabled(findPlugin)?.let { plugin ->
                    // Second lookup to trigger deprecation warning if convention is found
                    findPlugin.create()
                    @Suppress("UNCHECKED_CAST")
                    plugin as T
                }
            }
            // Get the non-existent extension for error handling
            ?: extensions.getByType(type)
    }


/**
 * Returns the [project extension][org.gradle.api.plugins.ExtensionAware] of the specified type.
 *
 * If no extension is found, returns a project convention if available.
 * Note, that the concept of conventions is deprecated and scheduled for
 * removal in Gradle 9.
 *
 * @param T the project extension type.
 * @see [org.gradle.api.plugins.ExtensionAware]
 */
fun <T : Any> Project.the(extensionType: KClass<T>): T =
    // Find extension
    extensions.findByType(extensionType.java)
        ?: Factory {
            // Find convention
            // Reflective look up to still support plugins that inlined this function once conventions will be removed
            this::class.java.methods.firstOrNull { it.name == "getConvention" }?.invoke(this)?.let { convention ->
                convention::class.java.getMethod("findPlugin", Class::class.java).invoke(convention, extensionType.java)
            }
        }.let { findPlugin ->
            DeprecationLogger.whileDisabled(findPlugin)?.let { plugin ->
                // Second lookup to trigger deprecation warning if convention is found
                findPlugin.create()
                @Suppress("UNCHECKED_CAST")
                plugin as T
            }
        }
        // Get the non-existent extension for error handling
        ?: extensions.getByType(extensionType.java)


/**
 * Creates a [Task] with the given [name] and [type], configures it with the given [configuration] action,
 * and adds it to this project tasks container.
 */
inline fun <reified type : Task> Project.task(name: String, noinline configuration: type.() -> Unit) =
    task(name, type::class, configuration)


/**
 * Creates a [Task] with the given [name] and [type], and adds it to this project tasks container.
 *
 * @see [Project.getTasks]
 * @see [TaskContainer.create]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified type : Task> Project.task(name: String) =
    tasks.create(name, type::class.java)


fun <T : Task> Project.task(name: String, type: KClass<T>, configuration: T.() -> Unit) =
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


/**
 * Configures the repositories for the script dependencies.
 */
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
    DependencyHandlerScope.of(dependencies).configuration()


/**
 * Configures the artifacts for this project.
 *
 * Executes the given configuration block against the [ArtifactHandlerScope] for this
 * project.
 *
 * @param configuration the configuration block.
 */
fun Project.artifacts(configuration: ArtifactHandlerScope.() -> Unit) =
    ArtifactHandlerScope.of(artifacts).configuration()


/**
 * Locates a property on [Project].
 */
operator fun Project.provideDelegate(any: Any?, property: KProperty<*>): PropertyDelegate =
    propertyDelegateFor(this, property)


/**
 * Creates a container for managing named objects of the specified type.
 *
 * The specified type must have a public constructor which takes the name as a [String] parameter.
 *
 * All objects **MUST** expose their name as a bean property named `name`.
 * The name must be constant for the life of the object.
 *
 * @param T The type of objects for the container to contain.
 * @return The container.
 *
 * @see [Project.container]
 */
inline fun <reified T> Project.container(): NamedDomainObjectContainer<T> =
    container(T::class.java)


/**
 * Creates a container for managing named objects of the specified type.
 *
 * The given factory is used to create object instances.
 *
 * All objects **MUST** expose their name as a bean property named `name`.
 * The name must be constant for the life of the object.
 *
 * @param T The type of objects for the container to contain.
 * @param factory The factory to use to create object instances.
 * @return The container.
 *
 * @see [Project.container]
 */
inline fun <reified T : Any> Project.container(noinline factory: (String) -> T): NamedDomainObjectContainer<T> =
    container(T::class.java, factory)


/**
 * Creates a dependency on the API of the current version of the Gradle Kotlin DSL.
 *
 * Includes the Kotlin and Gradle APIs.
 *
 * @return The dependency.
 */
fun Project.gradleKotlinDsl(): Dependency =
    DefaultSelfResolvingDependency(
        OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation.GRADLE_KOTLIN_DSL),
        project.fileCollectionOf(
            gradleKotlinDslOf(project),
            "gradleKotlinDsl"
        ) as FileCollectionInternal
    )


/**
 * Nested `plugins` blocks are **NOT** allowed, for example:
 * ```
 * project(":core") {
 *   plugins { java }
 * }
 * ```
 * If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = "id") instead.
 * ```
 * project(":core") {
 *   apply(plugin = "java")
 * }
 * ```
 * @since 6.0
 */
@Suppress("unused", "DeprecatedCallableAddReplaceWith")
@Deprecated(
    "The plugins {} block must not be used here. " + "If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = \"id\") instead.",
    level = DeprecationLevel.ERROR
)
fun Project.plugins(@Suppress("unused_parameter") block: PluginDependenciesSpec.() -> Unit): Nothing =
    invalidPluginsCall()
