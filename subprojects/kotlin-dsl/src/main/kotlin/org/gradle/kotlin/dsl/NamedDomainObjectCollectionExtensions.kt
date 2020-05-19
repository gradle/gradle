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

import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.UnknownDomainObjectException

import org.gradle.kotlin.dsl.support.illegalElementType

import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.safeCast


/**
 * Returns a collection containing the objects in this collection of the given type. The
 * returned collection is live, so that when matching objects are later added to this
 * collection, they are also visible in the filtered collection.
 *
 * @param S The type of objects to find.
 * @return The matching objects. Returns an empty collection if there are no such objects
 * in this collection.
 * @see [NamedDomainObjectCollection.withType]
 */
inline fun <reified S : Any> NamedDomainObjectCollection<in S>.withType(): NamedDomainObjectCollection<S> =
    withType(S::class.java)


/**
 * Idiomatic way of referring to the provider of a well-known element of a collection via a delegate property.
 *
 * `tasks { val jar by existing }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 */
inline val <T : Any, C : NamedDomainObjectCollection<T>> C.existing: ExistingDomainObjectDelegateProvider<out C>
    get() = ExistingDomainObjectDelegateProvider.of(this)


/**
 * Idiomatic way of referring to the provider of a well-known element of a collection via a delegate property.
 *
 * `tasks { val jar by existing { ... } }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 * @param action the configuration action
 */
fun <T : Any, C : NamedDomainObjectCollection<T>> C.existing(action: T.() -> Unit): ExistingDomainObjectDelegateProviderWithAction<out C, T> =
    ExistingDomainObjectDelegateProviderWithAction.of(this, action)


/**
 * Idiomatic way of referring to the provider of a well-known element of a collection via a delegate property.
 *
 * `tasks { val jar by existing(Jar::class) }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 * @param type the domain object type
 */
fun <T : Any, C : NamedDomainObjectCollection<T>, U : T> C.existing(type: KClass<U>): ExistingDomainObjectDelegateProviderWithType<out C, U> =
    ExistingDomainObjectDelegateProviderWithType.of(this, type)


/**
 * Idiomatic way of referring to the provider of a well-known element of a collection via a delegate property.
 *
 * `tasks { val jar by existing(Jar::class) { ... } }`
 *
 * @param T the domain object type
 * @param C the concrete container type
 * @param type the domain object type
 * @param action the configuration action
 */
fun <T : Any, C : NamedDomainObjectCollection<T>, U : T> C.existing(type: KClass<U>, action: U.() -> Unit): ExistingDomainObjectDelegateProviderWithTypeAndAction<out C, U> =
    ExistingDomainObjectDelegateProviderWithTypeAndAction.of(this, type, action)


/**
 * Holds the delegate provider for the `existing` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class ExistingDomainObjectDelegateProvider<T>
private constructor(
    internal val delegateProvider: T
) {
    companion object {
        fun <T> of(delegateProvider: T) =
            ExistingDomainObjectDelegateProvider(delegateProvider)
    }
}


/**
 * Holds the delegate provider for the `existing` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class ExistingDomainObjectDelegateProviderWithAction<C, T>
private constructor(
    internal val delegateProvider: C,
    internal val action: T.() -> Unit
) {
    companion object {
        fun <C, T> of(delegateProvider: C, action: T.() -> Unit) =
            ExistingDomainObjectDelegateProviderWithAction(delegateProvider, action)
    }
}


/**
 * Holds the delegate provider and expected element type for the `existing` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class ExistingDomainObjectDelegateProviderWithType<T, U : Any>
private constructor(
    internal val delegateProvider: T,
    internal val type: KClass<U>
) {
    companion object {
        fun <T, U : Any> of(delegateProvider: T, type: KClass<U>) =
            ExistingDomainObjectDelegateProviderWithType(delegateProvider, type)
    }
}


/**
 * Holds the delegate provider and expected element type for the `existing` property delegate with
 * the purpose of providing specialized implementations for the `provideDelegate` operator
 * based on the static type of the provider.
 */
class ExistingDomainObjectDelegateProviderWithTypeAndAction<T, U : Any>
private constructor(
    internal val delegateProvider: T,
    internal val type: KClass<U>,
    internal val action: U.() -> Unit
) {
    companion object {
        fun <T, U : Any> of(delegateProvider: T, type: KClass<U>, action: U.() -> Unit) =
            ExistingDomainObjectDelegateProviderWithTypeAndAction(delegateProvider, type, action)
    }
}


/**
 * Provides access to the [NamedDomainObjectProvider] for the element of the given
 * property name from the container via a delegated property.
 */
operator fun <T : Any, C : NamedDomainObjectCollection<T>> ExistingDomainObjectDelegateProvider<C>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name)
)


/**
 * Provides access to the [NamedDomainObjectProvider] for the element of the given
 * property name from the container via a delegated property.
 */
operator fun <T : Any, C : NamedDomainObjectCollection<T>> ExistingDomainObjectDelegateProviderWithAction<C, T>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name).apply { configure(action) }
)


/**
 * Provides access to the [NamedDomainObjectProvider] for the element of the given
 * property name from the container via a delegated property.
 */
operator fun <T : Any, C : NamedDomainObjectCollection<T>, U : T> ExistingDomainObjectDelegateProviderWithType<C, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name, type)
)


/**
 * Provides access to the [NamedDomainObjectProvider] for the element of the given
 * property name from the container via a delegated property.
 */
operator fun <T : Any, C : NamedDomainObjectCollection<T>, U : T> ExistingDomainObjectDelegateProviderWithTypeAndAction<C, U>.provideDelegate(
    receiver: Any?,
    property: KProperty<*>
) = ExistingDomainObjectDelegate.of(
    delegateProvider.named(property.name, type, action)
)


/**
 * Holds a property delegate with the purpose of providing specialized implementations for the
 * `getValue` operator based on the static type of the delegate.
 */
class ExistingDomainObjectDelegate<T>
private constructor(
    internal val delegate: T
) {
    companion object {
        fun <T> of(delegate: T) =
            ExistingDomainObjectDelegate(delegate)
    }
}


/**
 * Gets the delegate value.
 */
operator fun <T> ExistingDomainObjectDelegate<out T>.getValue(receiver: Any?, property: KProperty<*>): T =
    delegate


/**
 * Locates an object by name and type, without triggering its creation or configuration, failing if there is no such object.
 *
 * @see [NamedDomainObjectCollection.named]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Any> NamedDomainObjectCollection<out Any>.named(name: String): NamedDomainObjectProvider<T> =
    named(name, T::class)


/**
 * Locates an object by name and type, without triggering its creation or configuration, failing if there is no such object.
 *
 * @see [NamedDomainObjectCollection.named]
 */
@Suppress("unchecked_cast")
fun <T : Any> NamedDomainObjectCollection<out Any>.named(name: String, type: KClass<T>): NamedDomainObjectProvider<T> =
    (this as NamedDomainObjectCollection<T>).named(name, type.java)


/**
 * Configures an object by name and type, without triggering its creation or configuration, failing if there is no such object.
 *
 * @see [NamedDomainObjectCollection.named]
 * @see [NamedDomainObjectProvider.configure]
 */
@Suppress("unchecked_cast")
inline fun <reified T : Any> NamedDomainObjectCollection<out Any>.named(name: String, noinline configuration: T.() -> Unit): NamedDomainObjectProvider<T> =
    (this as NamedDomainObjectCollection<T>).named(name, T::class.java, configuration)


/**
 * Configures an object by name and type, without triggering its creation or configuration, failing if there is no such object.
 *
 * @see [NamedDomainObjectCollection.named]
 * @see [NamedDomainObjectProvider.configure]
 */
@Suppress("unchecked_cast")
fun <T : Any> NamedDomainObjectCollection<out Any>.named(name: String, type: KClass<T>, configuration: T.() -> Unit): NamedDomainObjectProvider<T> =
    (this as NamedDomainObjectCollection<T>).named(name, type.java, configuration)


/**
 * Locates an object by name and casts it to the expected type [T].
 *
 * If an object with the given [name] is not found, [UnknownDomainObjectException] is thrown.
 * If the object is found but cannot be cast to the expected type [T], [IllegalArgumentException] is thrown.
 *
 * @param name object name
 * @return the object, never null
 * @throws [UnknownDomainObjectException] When the given object is not found.
 * @throws [IllegalArgumentException] When the given object cannot be cast to the expected type.
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Any> NamedDomainObjectCollection<out Any>.getByName(name: String) =
    getByName(name).let {
        it as? T
            ?: throw illegalElementType(this, name, T::class, it::class)
    }


/**
 * Locates an object by name and casts it to the expected [type].
 *
 * If an object with the given [name] is not found, [UnknownDomainObjectException] is thrown.
 * If the object is found but cannot be cast to the expected [type], [IllegalArgumentException] is thrown.
 *
 * @param name object name
 * @param type expected type
 * @return the object, never null
 * @throws [UnknownDomainObjectException] When the given object is not found.
 * @throws [IllegalArgumentException] When the given object cannot be cast to the expected type.
 */
fun <T : Any> NamedDomainObjectCollection<out Any>.getByName(name: String, type: KClass<T>): T =
    getByName(name).let {
        type.safeCast(it)
            ?: throw illegalElementType(this, name, type, it::class)
    }


/**
 * Locates an object by name and casts it to the expected [type] then configures it.
 *
 * If an object with the given [name] is not found, [UnknownDomainObjectException] is thrown.
 * If the object is found but cannot be cast to the expected [type], [IllegalArgumentException] is thrown.
 *
 * @param name object name
 * @param configure configuration action to apply to the object before returning it
 * @return the object, never null
 * @throws [UnknownDomainObjectException] When the given object is not found.
 * @throws [IllegalArgumentException] When the given object cannot be cast to the expected type.
 */
fun <T : Any> NamedDomainObjectCollection<out Any>.getByName(name: String, type: KClass<T>, configure: T.() -> Unit): T =
    getByName(name, type).also(configure)


/**
 * Locates an object by name and casts it to the expected type [T] then configures it.
 *
 * If an object with the given [name] is not found, [UnknownDomainObjectException] is thrown.
 * If the object is found but cannot be cast to the expected type [T], [IllegalArgumentException] is thrown.
 *
 * @param name object name
 * @param configure configuration action to apply to the object before returning it
 * @return the object, never null
 * @throws [UnknownDomainObjectException] When the given object is not found.
 * @throws [IllegalArgumentException] When the given object cannot be cast to the expected type.
 */
inline fun <reified T : Any> NamedDomainObjectCollection<out Any>.getByName(name: String, configure: T.() -> Unit) =
    getByName<T>(name).also(configure)


/**
 * Idiomatic way of referring to an existing element in a collection
 * via a delegate property.
 *
 * `tasks { val jar by getting }`
 */
inline val <T : Any, U : NamedDomainObjectCollection<out T>> U.getting
    get() = NamedDomainObjectCollectionDelegateProvider.of(this)


/**
 * Idiomatic way of referring and configuring an existing element in a collection
 * via a delegate property.
 *
 * `tasks { val jar by getting { group = "My" } }`
 */
fun <T : Any, U : NamedDomainObjectCollection<T>> U.getting(configuration: T.() -> Unit) =
    NamedDomainObjectCollectionDelegateProvider.of(this, configuration)


/**
 * Enables typed access to container elements via delegated properties.
 */
class NamedDomainObjectCollectionDelegateProvider<T>
private constructor(
    internal val collection: NamedDomainObjectCollection<T>,
    internal val configuration: (T.() -> Unit)?
) {
    companion object {
        fun <T> of(
            collection: NamedDomainObjectCollection<T>,
            configuration: (T.() -> Unit)? = null
        ) =
            NamedDomainObjectCollectionDelegateProvider(collection, configuration)
    }

    operator fun provideDelegate(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = ExistingDomainObjectDelegate.of(
        when (configuration) {
            null -> collection.getByName(property.name)
            else -> collection.getByName(property.name, configuration)
        }
    )
}


/**
 * Locates an object by name, failing if there is no such object.
 *
 * @param name The object name
 * @return The object with the given name.
 * @throws [UnknownDomainObjectException] when there is no such object in this collection.
 *
 * @see [NamedDomainObjectCollection.getByName]
 */
operator fun <T : Any> NamedDomainObjectCollection<T>.get(name: String): T =
    getByName(name)


/**
 * Allows a [NamedDomainObjectCollection] to be used as a property delegate.
 *
 * @see [NamedDomainObjectCollection.named]
 */
operator fun <T : Any> NamedDomainObjectCollection<T>.provideDelegate(thisRef: Any?, property: KProperty<*>): NamedDomainObjectProvider<T> =
    named(property.name)


/**
 * Allows a [NamedDomainObjectProvider] to be used as a property delegate.
 *
 * @see [NamedDomainObjectProvider.get]
 */
@Suppress("nothing_to_inline", "unchecked_cast")
inline operator fun <T : Any, reified U : T> NamedDomainObjectProvider<out T>.getValue(thisRef: Any?, property: KProperty<*>): U =
    get().let {
        it as? U
            ?: throw illegalElementType(this, property.name, U::class, it::class)
    }


/**
 * Required due to [KT-25810](https://youtrack.jetbrains.com/issue/KT-25810).
 */
operator fun <T : Any> T.provideDelegate(receiver: Any?, property: KProperty<*>): T = this
