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

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.PolymorphicDomainObjectContainer

import org.gradle.internal.Factory
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.support.delegates.NamedDomainObjectContainerDelegate

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/**
 * Allows the container to be configured via an augmented DSL.
 *
 * @param configuration The expression to configure this container with
 * @return The container.
 */
@Suppress("nothing_to_inline")
inline operator fun <T : Any, C : NamedDomainObjectContainer<T>> C.invoke(
    configuration: Action<NamedDomainObjectContainerScope<T>>
): C = apply {
    configuration.execute(NamedDomainObjectContainerScope.of(this))
}


/**
 * Property delegate for registering new elements in the container.
 *
 * `tasks { val rebuild by registering }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register(name)' instead. See the Gradle 9.6 upgrading guide.")
inline val <T : Any, C : NamedDomainObjectContainer<T>> C.registering: RegisteringDomainObjectDelegateProvider<out C>
    get() {
        DeprecationLogger.deprecate("The 'val name by registering' property delegate syntax")
            .withAdvice("Use 'val element = register(name)' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
        return DeprecationLogger.whileDisabled(Factory {
            RegisteringDomainObjectDelegateProvider.of(this)
        })!!
    }


/**
 * Property delegate for registering new elements in the container.
 *
 * ```kotlin
 * tasks {
 *    val rebuild by registering {
 *        dependsOn("clean", "build")
 *    }
 * }
 * ```
 *
 * @param T the domain object type
 * @param C the concrete container type
 * @param action the configuration action
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register(name) { }' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, C : NamedDomainObjectContainer<T>> C.registering(action: T.() -> Unit): RegisteringDomainObjectDelegateProviderWithAction<out C, T> {
    DeprecationLogger.deprecate("The 'val name by registering { }' property delegate syntax")
        .withAdvice("Use 'val element = register(name) { }' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    return DeprecationLogger.whileDisabled(Factory {
        RegisteringDomainObjectDelegateProviderWithAction.of(this, action)
    })!!
}


/**
 * Property delegate for registering new elements in the container.
 *
 * `tasks { val jar by registering(Jar::class) }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 * @param type the domain object type
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> C.registering(type: KClass<U>): RegisteringDomainObjectDelegateProviderWithType<out C, U> {
    DeprecationLogger.deprecate("The 'val name by registering(Type::class)' property delegate syntax")
        .withAdvice("Use 'val element = register<Type>(name)' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    return DeprecationLogger.whileDisabled(Factory {
        RegisteringDomainObjectDelegateProviderWithType.of(this, type)
    })!!
}


/**
 * Property delegate for registering new elements in the container.
 *
 * `tasks { val jar by registering(Jar::class) { } }`
 *
 * @param T the container element type
 * @param C the container type
 * @param U the desired domain object type
 * @param type the domain object type
 * @param action the configuration action
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register<Type>(name) { }' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> C.registering(
    type: KClass<U>,
    action: U.() -> Unit
): RegisteringDomainObjectDelegateProviderWithTypeAndAction<out C, U> {
    DeprecationLogger.deprecate("The 'val name by registering(Type::class) { }' property delegate syntax")
        .withAdvice("Use 'val element = register<Type>(name) { }' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    return DeprecationLogger.whileDisabled(Factory {
        RegisteringDomainObjectDelegateProviderWithTypeAndAction.of(this, type, action)
    })!!
}


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register(name)' instead. See the Gradle 9.6 upgrading guide.")
operator fun <T : Any, C : NamedDomainObjectContainer<T>> RegisteringDomainObjectDelegateProvider<C>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = DeprecationLogger.whileDisabled(Factory {
    ExistingDomainObjectDelegate.of(
        delegateProvider.register(property.name)
    )
})!!


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register(name) { }' instead. See the Gradle 9.6 upgrading guide.")
operator fun <T : Any, C : NamedDomainObjectContainer<T>> RegisteringDomainObjectDelegateProviderWithAction<C, T>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = DeprecationLogger.whileDisabled(Factory {
    ExistingDomainObjectDelegate.of(
        delegateProvider.register(property.name, action)
    )
})!!


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
operator fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> RegisteringDomainObjectDelegateProviderWithType<C, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = DeprecationLogger.whileDisabled(Factory {
    ExistingDomainObjectDelegate.of(
        delegateProvider.register(property.name, type.java)
    )
})!!


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = register<Type>(name) { }' instead. See the Gradle 9.6 upgrading guide.")
operator fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> RegisteringDomainObjectDelegateProviderWithTypeAndAction<C, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = DeprecationLogger.whileDisabled(Factory {
    ExistingDomainObjectDelegate.of(
        delegateProvider.register(property.name, type.java, action)
    )
})!!


/**
 * Holds the delegate provider for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
@Deprecated("Use 'val element = register(name)' instead. See the Gradle 9.6 upgrading guide.")
class RegisteringDomainObjectDelegateProvider<T>
private constructor(
    internal val delegateProvider: T
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T> of(delegateProvider: T) =
            RegisteringDomainObjectDelegateProvider(delegateProvider).also {
                DeprecationLogger.deprecateType(RegisteringDomainObjectDelegateProvider::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }
}


/**
 * Holds the delegate provider for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
@Deprecated("Use 'val element = register(name) { }' instead. See the Gradle 9.6 upgrading guide.")
class RegisteringDomainObjectDelegateProviderWithAction<C, T>
private constructor(
    internal val delegateProvider: C,
    internal val action: T.() -> Unit
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <C, T> of(delegateProvider: C, action: T.() -> Unit) =
            RegisteringDomainObjectDelegateProviderWithAction(delegateProvider, action).also {
                DeprecationLogger.deprecateType(RegisteringDomainObjectDelegateProviderWithAction::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }
}


/**
 * Holds the delegate provider and expected element type for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
@Deprecated("Use 'val element = register<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
class RegisteringDomainObjectDelegateProviderWithType<T, U : Any>
private constructor(
    internal val delegateProvider: T,
    internal val type: KClass<U>
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T, U : Any> of(delegateProvider: T, type: KClass<U>) =
            RegisteringDomainObjectDelegateProviderWithType(delegateProvider, type).also {
                DeprecationLogger.deprecateType(RegisteringDomainObjectDelegateProviderWithType::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }
}


/**
 * Holds the delegate provider and expected element type for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
@Deprecated("Use 'val element = register<Type>(name) { }' instead. See the Gradle 9.6 upgrading guide.")
class RegisteringDomainObjectDelegateProviderWithTypeAndAction<T, U : Any>
private constructor(
    internal val delegateProvider: T,
    internal val type: KClass<U>,
    internal val action: U.() -> Unit
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T, U : Any> of(delegateProvider: T, type: KClass<U>, action: U.() -> Unit) =
            RegisteringDomainObjectDelegateProviderWithTypeAndAction(delegateProvider, type, action).also {
                DeprecationLogger.deprecateType(RegisteringDomainObjectDelegateProviderWithTypeAndAction::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }
}


/**
 * Receiver for [NamedDomainObjectContainer] configuration blocks.
 */
open
class NamedDomainObjectContainerScope<T : Any>
internal constructor(
    override val delegate: NamedDomainObjectContainer<T>
) : NamedDomainObjectContainerDelegate<T>(), PolymorphicDomainObjectContainer<T> {

    companion object {
        fun <T : Any> of(container: NamedDomainObjectContainer<T>) =
            NamedDomainObjectContainerScope(container)
    }

    override fun <U : T> register(name: String, type: Class<U>, configurationAction: Action<in U>): NamedDomainObjectProvider<U> =
        polymorphicDomainObjectContainer().register(name, type, configurationAction)

    override fun <U : T> register(name: String, type: Class<U>): NamedDomainObjectProvider<U> =
        polymorphicDomainObjectContainer().register(name, type)

    override fun <U : T> create(name: String, type: Class<U>): U =
        polymorphicDomainObjectContainer().create(name, type)

    override fun <U : T> create(name: String, type: Class<U>, configuration: Action<in U>): U =
        polymorphicDomainObjectContainer().create(name, type, configuration)

    override fun <U : T> maybeCreate(name: String, type: Class<U>): U =
        polymorphicDomainObjectContainer().maybeCreate(name, type)

    override fun <U : T> containerWithType(type: Class<U>): NamedDomainObjectContainer<U> =
        polymorphicDomainObjectContainer().containerWithType(type)

    /**
     * Configures an object by name, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [NamedDomainObjectContainer.named]
     * @see [NamedDomainObjectProvider.configure]
     */
    operator fun String.invoke(configuration: T.() -> Unit): NamedDomainObjectProvider<T> =
        named(this).apply { configure(configuration) }

    /**
     * Configures an object by name, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [PolymorphicDomainObjectContainer.named]
     * @see [NamedDomainObjectProvider.configure]
     */
    operator fun <U : T> String.invoke(type: KClass<U>, configuration: U.() -> Unit): NamedDomainObjectProvider<U> =
        delegate.named(this, type, configuration)

    /**
     * Locates an object by name and type, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [PolymorphicDomainObjectContainer.named]
     */
    operator fun <U : T> String.invoke(type: KClass<U>): NamedDomainObjectProvider<U> =
        delegate.named(this, type)

    /**
     * Cast this to [PolymorphicDomainObjectContainer] or throw [IllegalArgumentException].
     *
     * We must rely on the dynamic cast and possible runtime failure here due to a Kotlin extension member limitation.
     * Kotlin currently can't disambiguate between invoke operators with more specific receivers in a type hierarchy.
     *
     * See https://youtrack.jetbrains.com/issue/KT-15711
     */
    private
    fun polymorphicDomainObjectContainer() =
        delegate as? PolymorphicDomainObjectContainer<T>
            ?: throw IllegalArgumentException("Container '$delegate' is not polymorphic.")
}


/**
 * Provides a property delegate that creates elements of the default collection type.
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = create(name)' instead. See the Gradle 9.6 upgrading guide.")
val <T : Any> NamedDomainObjectContainer<T>.creating
    get() = DeprecationLogger.whileDisabled(Factory { NamedDomainObjectContainerCreatingDelegateProvider.of(this) })!!.also {
        DeprecationLogger.deprecate("The 'val name by creating' property delegate syntax")
            .withAdvice("Use 'val element = create(name)' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    }


/**
 * Provides a property delegate that creates elements of the default collection type with the given [configuration].
 *
 * `val myElement by myContainer.creating { myProperty = 42 }`
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = create(name) { }' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any> NamedDomainObjectContainer<T>.creating(configuration: T.() -> Unit) =
    DeprecationLogger.whileDisabled(Factory { NamedDomainObjectContainerCreatingDelegateProvider.of(this, configuration) })!!.also {
        DeprecationLogger.deprecate("The 'val name by creating { }' property delegate syntax")
            .withAdvice("Use 'val element = create(name) { }' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    }


/**
 * A property delegate that creates elements in the given [NamedDomainObjectContainer].
 *
 * See [creating]
 */
@Deprecated("Use 'val element = create(name)' instead. See the Gradle 9.6 upgrading guide.")
class NamedDomainObjectContainerCreatingDelegateProvider<T : Any>
private constructor(
    internal val container: NamedDomainObjectContainer<T>,
    internal val configuration: (T.() -> Unit)? = null
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T : Any> of(container: NamedDomainObjectContainer<T>, configuration: (T.() -> Unit)? = null) =
            NamedDomainObjectContainerCreatingDelegateProvider(container, configuration).also {
                DeprecationLogger.deprecateType(NamedDomainObjectContainerCreatingDelegateProvider::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use 'val element = create(name)' instead. See the Gradle 9.6 upgrading guide.")
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        DeprecationLogger.whileDisabled(Factory {
            ExistingDomainObjectDelegate.of(
                when (configuration) {
                    null -> container.create(property.name)
                    else -> container.create(property.name, configuration)
                }
            )
        })!!
}


/**
 * Provides a property delegate that creates elements of the given [type].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = create<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: KClass<U>) =
    DeprecationLogger.whileDisabled(Factory {
        PolymorphicDomainObjectContainerCreatingDelegateProvider.of(this, type.java)
    })!!.also {
        DeprecationLogger.deprecate("The 'val name by creating(Type::class)' property delegate syntax")
            .withAdvice("Use 'val element = create<Type>(name)' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    }


/**
 * Provides a property delegate that creates elements of the given [type] with the given [configuration].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = create<Type>(name) { }' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: KClass<U>, configuration: U.() -> Unit) =
    creating(type.java, configuration)


/**
 * Provides a property delegate that creates elements of the given [type] expressed as a [java.lang.Class]
 * with the given [configuration].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = create<Type>(name) { }' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: Class<U>, configuration: U.() -> Unit) =
    DeprecationLogger.whileDisabled(Factory {
        PolymorphicDomainObjectContainerCreatingDelegateProvider.of(this, type, configuration)
    })!!.also {
        DeprecationLogger.deprecate("The 'val name by creating(Type::class) { }' property delegate syntax")
            .withAdvice("Use 'val element = create<Type>(name) { }' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    }


/**
 * A property delegate that creates elements of the given [type] with the given [configuration] in the given [container].
 */
@Deprecated("Use 'val element = create<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
class PolymorphicDomainObjectContainerCreatingDelegateProvider<T : Any, U : T>
private constructor(
    internal val container: PolymorphicDomainObjectContainer<T>,
    internal val type: Class<U>,
    internal val configuration: (U.() -> Unit)? = null
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T : Any, U : T> of(container: PolymorphicDomainObjectContainer<T>, type: Class<U>, configuration: (U.() -> Unit)? = null) =
            PolymorphicDomainObjectContainerCreatingDelegateProvider(container, type, configuration).also {
                DeprecationLogger.deprecateType(PolymorphicDomainObjectContainerCreatingDelegateProvider::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use 'val element = create<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        DeprecationLogger.whileDisabled(Factory {
            ExistingDomainObjectDelegate.of(
                when (configuration) {
                    null -> container.create(property.name, type)
                    else -> container.create(property.name, type, configuration)
                }
            )
        })!!
}


/**
 * Provides a property delegate that gets elements of the given [type] and applies the given [configuration].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = getByName<Type>(name) { }' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, U : T> NamedDomainObjectContainer<T>.getting(type: KClass<U>, configuration: U.() -> Unit) =
    DeprecationLogger.whileDisabled(Factory {
        PolymorphicDomainObjectContainerGettingDelegateProvider.of(this, type, configuration)
    })!!.also {
        DeprecationLogger.deprecate("The 'val name by getting(Type::class) { }' property delegate syntax")
            .withAdvice("Use 'val element = getByName<Type>(name) { }' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    }


/**
 * Provides a property delegate that gets elements of the given [type].
 */
@Suppress("DEPRECATION")
@Deprecated("Use 'val element = getByName<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
fun <T : Any, U : T> NamedDomainObjectContainer<T>.getting(type: KClass<U>) =
    DeprecationLogger.whileDisabled(Factory {
        PolymorphicDomainObjectContainerGettingDelegateProvider.of(this, type)
    })!!.also {
        DeprecationLogger.deprecate("The 'val name by getting(Type::class)' property delegate syntax")
            .withAdvice("Use 'val element = getByName<Type>(name)' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    }


/**
 * A property delegate that gets elements of the given [type] from the given [container]
 * and applies the given [configuration].
 */
@Deprecated("Use 'val element = getByName<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
class PolymorphicDomainObjectContainerGettingDelegateProvider<T : Any, U : T>
private constructor(
    internal val container: NamedDomainObjectContainer<T>,
    internal val type: KClass<U>,
    internal val configuration: (U.() -> Unit)? = null
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T : Any, U : T> of(container: NamedDomainObjectContainer<T>, type: KClass<U>, configuration: (U.() -> Unit)? = null) =
            PolymorphicDomainObjectContainerGettingDelegateProvider(container, type, configuration).also {
                DeprecationLogger.deprecateType(PolymorphicDomainObjectContainerGettingDelegateProvider::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use 'val element = getByName<Type>(name)' instead. See the Gradle 9.6 upgrading guide.")
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        DeprecationLogger.whileDisabled(Factory {
            ExistingDomainObjectDelegate.of(
                when (configuration) {
                    null -> container.getByName(property.name, type)
                    else -> container.getByName(property.name, type, configuration)
                }
            )
        })!!
}
