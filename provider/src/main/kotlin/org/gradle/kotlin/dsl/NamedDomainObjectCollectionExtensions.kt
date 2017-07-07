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
import org.gradle.api.UnknownDomainObjectException

import org.gradle.kotlin.dsl.support.illegalElementType

import kotlin.reflect.KProperty


/**
 * Idiomatic way of referring to an existing element in a collection
 * via a delegate property.
 *
 * `tasks { val jar by getting }`
 */
inline
val <T : Any, U : NamedDomainObjectCollection<in T>> U.getting: U get() = this


/**
 * Idiomatic way of referring and configuring an existing element in a collection
 * via a delegate property.
 *
 * `tasks { val jar by getting { group = "My" } }`
 */
fun <T : Any, U : NamedDomainObjectCollection<T>> U.getting(configuration: T.() -> Unit) =
    NamedDomainObjectCollectionDelegateProvider(this, configuration)


class NamedDomainObjectCollectionDelegateProvider<T>(
    val collection: NamedDomainObjectCollection<T>,
    val configuration: T.() -> Unit) {

    operator fun provideDelegate(thisRef: Any?, property: kotlin.reflect.KProperty<*>): NamedDomainObjectCollection<T> =
        collection.apply {
            getByName(property.name).apply(configuration)
        }
}


/**
 * Locates an object by name, failing if there is no such object.
 *
 * @param name The object name
 * @return The object with the given name.
 * @throws UnknownDomainObjectException when there is no such object in this collection.
 *
 * @see NamedDomainObjectCollection.getByName
 */
operator fun <T : Any> NamedDomainObjectCollection<T>.get(name: String): T =
    getByName(name)


/**
 * Allows a [NamedDomainObjectCollection] to be used as a property delegate.
 *
 * @throws UnknownDomainObjectException upon property access when there is no such object in the given collection.
 *
 * @see NamedDomainObjectCollection.getByName
 */
inline
operator fun <T : Any, reified U : T> NamedDomainObjectCollection<T>.getValue(thisRef: Any?, property: KProperty<*>): U =
    getByName(property.name).let {
        it as? U
            ?: throw illegalElementType(this, property.name, U::class, it::class)
    }
