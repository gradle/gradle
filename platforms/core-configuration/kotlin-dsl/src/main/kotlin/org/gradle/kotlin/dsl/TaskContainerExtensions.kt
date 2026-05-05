/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.Task
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider

import org.gradle.kotlin.dsl.support.delegates.TaskContainerDelegate

import kotlin.reflect.KClass


/**
 * Allows a [TaskContainer] to be configured via an augmented DSL that includes
 * a shorthand string notation for configuring existing tasks.
 *
 * ```kotlin
 * tasks {
 *     "build" {
 *         dependsOn("clean")
 *     }
 *     "clean"(Delete::class) {
 *         delete("./build")
 *     }
 * }
 * ```
 *
 * @param configuration The expression to configure this [TaskContainer] with.
 * @return The given [TaskContainer].
 */
inline operator fun TaskContainer.invoke(
    configuration: TaskContainerScope.() -> Unit
): TaskContainer =

    apply {
        configuration(TaskContainerScope.of(this))
    }


/**
 * Receiver for the `tasks` block providing an extended set of operators for the configuration of tasks.
 */
class TaskContainerScope
private constructor(
    val container: TaskContainer
) : TaskContainerDelegate() {

    companion object {
        fun of(container: TaskContainer) =
            TaskContainerScope(container)
    }

    override val delegate: TaskContainer
        get() = container

    /**
     * Configures a task by name, without triggering its creation or configuration, failing if there is no such task.
     *
     * @see [TaskContainer.named]
     * @see [TaskProvider.configure]
     */
    operator fun String.invoke(configuration: Task.() -> Unit): TaskProvider<Task> =
        named(this).apply { configure(configuration) }

    /**
     * Configures a task by name, without triggering its creation or configuration, failing if there is no such task.
     *
     * @see [TaskContainer.named]
     * @see [TaskProvider.configure]
     */
    operator fun <U : Task> String.invoke(type: KClass<U>, configuration: U.() -> Unit): TaskProvider<U> =
        container.named(this, type, configuration)

    /**
     * Locates a task by name and type, without triggering its creation or configuration, failing if there is no such task.
     *
     * @see [TaskContainer.named]
     */
    operator fun <U : Task> String.invoke(type: KClass<U>): TaskProvider<U> =
        container.named(this, type)
}


/**
 * Locates a task by name and type, without triggering its creation or configuration, failing if there is no such task.
 *
 * @see [TaskCollection.named]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Task> TaskCollection<out Task>.named(name: String): TaskProvider<T> =
    named(name, T::class)


/**
 * Locates a task by name and type, without triggering its creation or configuration, failing if there is no such task.
 *
 * @see [TaskCollection.named]
 */
@Suppress("unchecked_cast")
fun <T : Task> TaskCollection<out Task>.named(name: String, type: KClass<T>): TaskProvider<T> =
    (this as TaskCollection<T>).named(name, type.java)


/**
 * Configures a task by name and type, without triggering its creation or configuration, failing if there is no such task.
 *
 * @see [TaskCollection.named]
 * @see [TaskProvider.configure]
 */
@Suppress("unchecked_cast")
fun <T : Task> TaskCollection<out Task>.named(name: String, type: KClass<T>, configuration: T.() -> Unit): TaskProvider<T> =
    (this as TaskCollection<T>).named(name, type.java, configuration)


/**
 * Configures a task by name and type, without triggering its creation or configuration, failing if there is no such task.
 *
 * @see [TaskCollection.named]
 * @see [TaskProvider.configure]
 */
@Suppress("unchecked_cast")
inline fun <reified T : Task> TaskCollection<out Task>.named(name: String, noinline configuration: T.() -> Unit): TaskProvider<T> =
    (this as TaskCollection<T>).named(name, T::class.java, configuration)


/**
 * Defines a new task, which will be created when it is required.
 *
 * @see [TaskContainer.register]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Task> TaskContainer.register(name: String): TaskProvider<T> =
    register(name, T::class.java)


/**
 * Defines and configure a new task, which will be created when it is required.
 *
 * @see [TaskContainer.register]
 */
inline fun <reified T : Task> TaskContainer.register(name: String, noinline configuration: T.() -> Unit): TaskProvider<T> =
    register(name, T::class.java, configuration)


/**
 * Defines a new task, which will be created when it is required passing the given arguments to the [javax.inject.Inject]-annotated constructor.
 *
 * @see [TaskContainer.register]
 */
inline fun <reified T : Task> TaskContainer.register(name: String, vararg arguments: Any): TaskProvider<T> =
    register(name, T::class.java, *arguments)


/**
 * Creates a [Task] with the given [name] and type, passing the given arguments to the [javax.inject.Inject]-annotated constructor,
 * and adds it to this project tasks container.
 */
@Deprecated("Use register instead. See https://docs.gradle.org/current/userguide/task_configuration_avoidance.html for more information.", ReplaceWith("this.register<T>(name, *arguments)"))
@Suppress("DEPRECATION")
inline fun <reified T : Task> TaskContainer.create(name: String, vararg arguments: Any): T =
    create(name, T::class.java, *arguments)

/**
 * Creates a [Task] with the specified [name] and type, adds it to the container,
 * and configures it with the specified action.
 */
@Deprecated("Use register instead. See https://docs.gradle.org/current/userguide/task_configuration_avoidance.html for more information.", ReplaceWith("this.register<T>(name, configureAction)"))
@Suppress("DEPRECATION")
inline fun <reified T : Task> TaskContainer.create(name: String, noinline configureAction: T.() -> Unit): T =
    create(name, T::class.java, configureAction)
