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

import kotlin.reflect.KClass
import kotlin.reflect.KProperty


/**
 * Allows the container to be configured via an augmented DSL.
 *
 * @param configuration The expression to configure this container with
 * @return The container.
 */
inline operator fun <T : Any, C : NamedDomainObjectContainer<T>> C.invoke(
    configuration: NamedDomainObjectContainerScope<T>.() -> Unit
): C =

    apply {
        configuration(NamedDomainObjectContainerScope.of(this))
    }


/**
 * Property delegate for registering new elements in the container.
 *
 * `tasks { val rebuild by registering }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 */
inline val <T : Any, C : NamedDomainObjectContainer<T>> C.registering: RegisteringDomainObjectDelegateProvider<out C>
    get() = RegisteringDomainObjectDelegateProvider(this)


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
fun <T : Any, C : NamedDomainObjectContainer<T>> C.registering(action: T.() -> Unit): RegisteringDomainObjectDelegateProviderWithAction<out C, T> =
    RegisteringDomainObjectDelegateProviderWithAction(this, action)


/**
 * Property delegate for registering new elements in the container.
 *
 * `tasks { val jar by registering(Jar::class) }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 * @param type the domain object type
 */
fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> C.registering(type: KClass<U>): RegisteringDomainObjectDelegateProviderWithType<out C, U> =
    RegisteringDomainObjectDelegateProviderWithType(this, type)


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
fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> C.registering(
    type: KClass<U>,
    action: U.() -> Unit
): RegisteringDomainObjectDelegateProviderWithTypeAndAction<out C, U> =
    RegisteringDomainObjectDelegateProviderWithTypeAndAction(this, type, action)


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
operator fun <T : Any, C : NamedDomainObjectContainer<T>> RegisteringDomainObjectDelegateProvider<C>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate(
    delegateProvider.register(property.name)
)


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
operator fun <T : Any, C : NamedDomainObjectContainer<T>> RegisteringDomainObjectDelegateProviderWithAction<C, T>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate(
    delegateProvider.register(property.name, action)
)


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
operator fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> RegisteringDomainObjectDelegateProviderWithType<C, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate(
    delegateProvider.register(property.name, type.java)
)


/**
 * Registers an element and provides a delegate with the resulting [NamedDomainObjectProvider].
 */
operator fun <T : Any, C : PolymorphicDomainObjectContainer<T>, U : T> RegisteringDomainObjectDelegateProviderWithTypeAndAction<C, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate(
    delegateProvider.register(property.name, type.java, action)
)


/**
 * Holds the delegate provider for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class RegisteringDomainObjectDelegateProvider<T>(
    val delegateProvider: T
)


/**
 * Holds the delegate provider for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class RegisteringDomainObjectDelegateProviderWithAction<C, T>(
    val delegateProvider: C,
    val action: T.() -> Unit
)


/**
 * Holds the delegate provider and expected element type for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class RegisteringDomainObjectDelegateProviderWithType<T, U : Any>(
    val delegateProvider: T,
    val type: KClass<U>
)


/**
 * Holds the delegate provider and expected element type for the `registering` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class RegisteringDomainObjectDelegateProviderWithTypeAndAction<T, U : Any>(
    val delegateProvider: T,
    val type: KClass<U>,
    val action: U.() -> Unit
)


/**
 * Receiver for [NamedDomainObjectContainer] configuration blocks.
 */
class NamedDomainObjectContainerScope<T : Any>
private constructor(
    private val container: NamedDomainObjectContainer<T>
) : NamedDomainObjectContainer<T> by container, PolymorphicDomainObjectContainer<T> {

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
        this().apply { configure(configuration) }

    /**
     * Locates an object by name, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [NamedDomainObjectContainer.named]
     */
    operator fun String.invoke(): NamedDomainObjectProvider<T> =
        container.named(this)

    /**
     * Configures an object by name, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [PolymorphicDomainObjectContainer.named]
     * @see [NamedDomainObjectProvider.configure]
     */
    operator fun <U : T> String.invoke(type: KClass<U>, configuration: U.() -> Unit): NamedDomainObjectProvider<U> =
        container.named(this, type, configuration)

    /**
     * Locates an object by name and type, without triggering its creation or configuration, failing if there is no such object.
     *
     * @see [PolymorphicDomainObjectContainer.named]
     */
    operator fun <U : T> String.invoke(type: KClass<U>): NamedDomainObjectProvider<U> =
        container.named(this, type)

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
        container as? PolymorphicDomainObjectContainer<T>
            ?: throw IllegalArgumentException("Container '$container' is not polymorphic.")
}


/**
 * Provides a property delegate that creates elements of the default collection type.
 */
val <T : Any> NamedDomainObjectContainer<T>.creating
    get() = NamedDomainObjectContainerCreatingDelegateProvider(this)


/**
 * Provides a property delegate that creates elements of the default collection type with the given [configuration].
 *
 * `val myElement by myContainer.creating { myProperty = 42 }`
 */
fun <T : Any> NamedDomainObjectContainer<T>.creating(configuration: T.() -> Unit) =
    NamedDomainObjectContainerCreatingDelegateProvider(this, configuration)


/**
 * A property delegate that creates elements in the given [NamedDomainObjectContainer].
 *
 * See [creating]
 */
class NamedDomainObjectContainerCreatingDelegateProvider<T : Any>(
    val container: NamedDomainObjectContainer<T>,
    val configuration: (T.() -> Unit)? = null
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        when (configuration) {
            null -> container.register(property.name)
            else -> container.register(property.name, configuration)
        }
}


/**
 * Provides a property delegate that creates elements of the given [type].
 */
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: KClass<U>) =
    PolymorphicDomainObjectContainerCreatingDelegateProvider(this, type.java)


/**
 * Provides a property delegate that creates elements of the given [type] with the given [configuration].
 */
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: KClass<U>, configuration: U.() -> Unit) =
    creating(type.java, configuration)


/**
 * Provides a property delegate that creates elements of the given [type] expressed as a [java.lang.Class]
 * with the given [configuration].
 */
fun <T : Any, U : T> PolymorphicDomainObjectContainer<T>.creating(type: Class<U>, configuration: U.() -> Unit) =
    PolymorphicDomainObjectContainerCreatingDelegateProvider(this, type, configuration)


/**
 * A property delegate that creates elements of the given [type] with the given [configuration] in the given [container].
 */
class PolymorphicDomainObjectContainerCreatingDelegateProvider<T : Any, U : T>(
    val container: PolymorphicDomainObjectContainer<T>,
    val type: Class<U>,
    val configuration: (U.() -> Unit)? = null
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        when (configuration) {
            null -> container.register(property.name, type)
            else -> container.register(property.name, type, configuration)
        }
}


/**
 * Provides a property delegate that gets elements of the given [type] and applies the given [configuration].
 */
fun <T : Any, U : T> NamedDomainObjectContainer<T>.getting(type: KClass<U>, configuration: U.() -> Unit) =
    PolymorphicDomainObjectContainerGettingDelegateProvider(this, type, configuration)


/**
 * Provides a property delegate that gets elements of the given [type].
 */
fun <T : Any, U : T> NamedDomainObjectContainer<T>.getting(type: KClass<U>) =
    PolymorphicDomainObjectContainerGettingDelegateProvider(this, type)


/**
 * A property delegate that gets elements of the given [type] from the given [container]
 * and applies the given [configuration].
 */
class PolymorphicDomainObjectContainerGettingDelegateProvider<T : Any, U : T>(
    val container: NamedDomainObjectContainer<T>,
    val type: KClass<U>,
    val configuration: (U.() -> Unit)? = null
) {
    operator fun provideDelegate(thisRef: Any?, property: KProperty<*>) =
        when (configuration) {
            null -> container.named(property.name, type)
            else -> container.named(property.name, type, configuration)
        }
}
