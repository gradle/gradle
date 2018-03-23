package org.gradle.kotlin.dsl

import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.file.ContentFilterable
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskContainer

import java.io.FilterReader


// This file contains members intended to be pulled upstream into the next Gradle Kotlin DSL release


/**
 * Creates and adds a new extension to this container.
 *
 * @see [ExtensionContainer.create]
 */
inline
fun <reified T : Any> ExtensionContainer.create(name: String, vararg constructionArguments: Any?): T =
    create(name, T::class.java, *constructionArguments)


/**
 * Looks for the task of a given name and casts it to the expected type [T].
 *
 * If none found it will throw an [UnknownDomainObjectException].
 * If the task is found but cannot be cast to the expected type it will throw an [IllegalStateException].
 *
 * @param name task name
 * @return task, never null
 * @throws [UnknownDomainObjectException] When the given task is not found.
 * @throws [IllegalStateException] When the given task cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline
fun <reified T : Any> TaskContainer.getByName(name: String) =
    getByName(name).let {
        it as? T
            ?: throw IllegalStateException(
            "Element '$name' of type '${it::class.java.name}' from container '$this' cannot be cast to '${T::class.qualifiedName}'.")
    }


/**
 * Looks for the task of a given name, casts it to the expected type [T]
 * then applies the given [configure] action.
 *
 * If none found it will throw an [UnknownDomainObjectException].
 * If the task is found but cannot be cast to the expected type it will throw an [IllegalStateException].
 *
 * @param name task name
 * @return task, never null
 * @throws [UnknownDomainObjectException] When the given task is not found.
 * @throws [IllegalStateException] When the given task cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline
fun <reified T : Any> TaskContainer.getByName(name: String, configure: T.() -> Unit) =
    getByName<T>(name).also(configure)


/**
 * Enables function invocation syntax on [Spec] instances.
 *
 * @see Spec.isSatisfiedBy
 */
operator fun <T> Spec<T>.invoke(arg: T): Boolean =
    isSatisfiedBy(arg)


inline
fun <reified T : FilterReader> ContentFilterable.filter(vararg properties: Pair<String, Any?>) =
    filter(mapOf(*properties), T::class.java)


inline
fun <reified T : FilterReader> ContentFilterable.filter(properties: Map<String, Any?>) =
    filter(properties, T::class.java)
