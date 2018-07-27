package org.gradle.kotlin.dsl

import org.gradle.api.Task
import org.gradle.api.InvalidUserDataException
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider


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


/**
 * Defines a new task, which will be created and configured when it is required.
 * A task is 'required' when the task is located using query methods such as [TaskContainer.getByName],
 * when the task is added to the task graph for execution or when [Provider.get] is called on the return value of this method.
 *
 * It is generally more efficient to use this method instead of one of the [TaskContainer.create] overloads,
 * as those methods will eagerly create and configure the task, regardless of whether that task is required for
 * the current build or not. This method, on the other hand, will defer creation and configuration until required.
 *
 * @param name The name of the task.
 * @param T The task type.
 * @param configurationAction The action to run to configure the task. This action runs when the task is required.
 * @param <T> The task type
 * @return A [Provider] that whose value will be the task, when queried.
 * @throws InvalidUserDataException If a task with the given name already exists in this project.
 * @since 4.9
 */
@Suppress("extension_shadowed_by_member")
inline
fun <reified T : Task> TaskContainer.register(name: String, noinline configurationAction: T.() -> Unit) =
    register(name, T::class.java) { configurationAction() }


inline fun <reified T : Task> TaskContainer.withType(): TaskCollection<T> =
    withType(T::class.java)


inline fun <reified T : Any> TaskProvider<out Task>.configureAs(noinline configurationAction: T.() -> Unit) =
    configure {
        configurationAction(
            this as? T
                ?: throw IllegalArgumentException(
                    "Task of type '${javaClass.name}' cannot be cast to '${T::class.qualifiedName}'."))
    }
