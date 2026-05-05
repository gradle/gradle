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
 * Kept for binary compatibility with plugins compiled against earlier Gradle versions
 * that picked up this extension (the former [KT-25810](https://youtrack.jetbrains.com/issue/KT-25810) workaround)
 * to use any object as a property delegate, e.g. `by lazy { ... }` after
 * `import org.gradle.kotlin.dsl.provideDelegate`. Not visible at source level.
 *
 * Hidden in 10.0.
 */
// See ProvideDelegateBinaryCompatibilityIntegrationTest
@Deprecated("Binary compatibility only.", level = DeprecationLevel.HIDDEN)
operator fun <T : Any> T.provideDelegate(receiver: Any?, property: KProperty<*>): T = this
