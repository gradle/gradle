package org.gradle.kotlin.dsl

import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer


// This file contains members intended to be pulled upstream into the next Gradle Kotlin DSL release


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


inline fun <reified T : Task> TaskContainer.withType(): TaskCollection<T> =
    withType(T::class.java)
