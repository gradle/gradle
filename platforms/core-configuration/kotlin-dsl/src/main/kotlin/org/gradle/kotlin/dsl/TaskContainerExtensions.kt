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
import org.gradle.internal.deprecation.DeprecationLogger

import org.gradle.kotlin.dsl.support.delegates.TaskContainerDelegate

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


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
 * Provides a [TaskProvider] delegate for the task named after the property.
 */
operator fun ExistingDomainObjectDelegateProvider<out TaskContainer>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name)
)


/**
 * Provides a [TaskProvider] delegate for the task named after the property after configuring it with the given action.
 */
operator fun ExistingDomainObjectDelegateProviderWithAction<out TaskContainer, Task>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name).apply { configure(action) }
)


/**
 * Provides a [TaskProvider] delegate for the task of the given type named after the property.
 */
operator fun <U : Task> ExistingDomainObjectDelegateProviderWithType<out TaskContainer, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name, type)
)


/**
 * Provides a [TaskProvider] delegate for the task of the given type named after the property after configuring it with the given action.
 */
operator fun <U : Task> ExistingDomainObjectDelegateProviderWithTypeAndAction<out TaskContainer, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name, type).apply { configure(action) }
)


/**
 * Registers a task and provides a delegate with the resulting [TaskProvider].
 */
operator fun RegisteringDomainObjectDelegateProvider<out TaskContainer>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.register(property.name)
)


/**
 * Registers a task that gets configured with the given action and provides a delegate with the resulting [TaskProvider].
 */
operator fun RegisteringDomainObjectDelegateProviderWithAction<out TaskContainer, Task>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.register(property.name, action)
)


/**
 * Registers a task of the given type and provides a delegate with the resulting [TaskProvider].
 */
operator fun <U : Task> RegisteringDomainObjectDelegateProviderWithType<out TaskContainer, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.register(property.name, type.java)
)


/**
 * Registers a task of the given type that gets configured with the given action and provides a delegate with the resulting [TaskProvider].
 */
operator fun <U : Task> RegisteringDomainObjectDelegateProviderWithTypeAndAction<out TaskContainer, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.register(property.name, type.java, action)
)


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
     * Locates a task by name, without triggering its creation or configuration, failing if there is no such task.
     *
     * @see [TaskContainer.named]
     */
    @Deprecated("Use named(String) instead.", ReplaceWith("named(this)"))
    operator fun String.invoke(): TaskProvider<Task> {
        DeprecationLogger.deprecateBehaviour(("Task '$this' found by String.invoke() notation."))
            .withContext("The \"name\"() notation can cause confusion with methods provided by Kotlin or the JDK.")
            .withAdvice("Use named(String) instead.")
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(8, "string_invoke")
            .nagUser()
        return container.named(this)
    }

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

/**
 * Provides a property delegate that creates tasks of the default type.
 */
@Deprecated("Use registering instead. See https://docs.gradle.org/current/userguide/task_configuration_avoidance.html for more information.", ReplaceWith("registering"))
val TaskContainer.creating
    get() = NamedDomainObjectContainerCreatingDelegateProvider.of(this)

/**
 * Provides a property delegate that creates tasks of the default type with the given [configuration].
 *
 * `val someTask by tasks.creating { onlyIf = true }`
 */
@Deprecated("Use registering instead. See https://docs.gradle.org/current/userguide/task_configuration_avoidance.html for more information.", ReplaceWith("registering(configuration)"))
fun TaskContainer.creating(configuration: Task.() -> Unit) =
    NamedDomainObjectContainerCreatingDelegateProvider.of(this, configuration)

/**
 * Provides a property delegate that creates tasks of the given [type].
 */
@Deprecated("Use registering instead. See https://docs.gradle.org/current/userguide/task_configuration_avoidance.html for more information.", ReplaceWith("registering(type)"))
fun <U : Task> TaskContainer.creating(type: KClass<U>) =
    PolymorphicDomainObjectContainerCreatingDelegateProvider.of(this, type.java)


/**
 * Provides a property delegate that creates tasks of the given [type] with the given [configuration].
 */
@Deprecated("Use registering instead. See https://docs.gradle.org/current/userguide/task_configuration_avoidance.html for more information.", ReplaceWith("registering(type, configuration)"))
@Suppress("DEPRECATION")
fun <U : Task> TaskContainer.creating(type: KClass<U>, configuration: U.() -> Unit) =
    creating(type.java, configuration)


/**
 * Provides a property delegate that creates tasks of the given [type] expressed as a [java.lang.Class]
 * with the given [configuration].
 */
@Deprecated("Use registering instead. See https://docs.gradle.org/current/userguide/task_configuration_avoidance.html for more information.", ReplaceWith("registering(type, configuration)"))
fun <U : Task> TaskContainer.creating(type: Class<U>, configuration: U.() -> Unit) =
    PolymorphicDomainObjectContainerCreatingDelegateProvider.of(this, type, configuration)

