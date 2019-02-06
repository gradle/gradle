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

import org.gradle.api.Named
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty


/**
 * Creates a simple immutable [Named] object of the given type and name.
 *
 * @param T The type of object to create
 * @param name The name of the created object
 * @return the created named object
 *
 * @see [ObjectFactory.named]
 */
inline fun <reified T : Named> ObjectFactory.named(name: String): T =
    named(T::class.java, name)


/**
 * Create a new instance of `T`, using [parameters] as the construction parameters.
 *
 * @param T The type of object to create
 * @param parameters The construction parameters
 * @return the created named object
 *
 * @see [ObjectFactory.newInstance]
 */
inline fun <reified T> ObjectFactory.newInstance(vararg parameters: Any): T =
    newInstance(T::class.java, *parameters)


/**
 * Creates a [Property] that holds values of the given type [T].
 *
 * @see [ObjectFactory.property]
 */
inline fun <reified T> ObjectFactory.property(): Property<T> =
    property(T::class.java)


/**
 * Creates a [SetProperty] that holds values of the given type [T].
 *
 * @see [ObjectFactory.setProperty]
 */
inline fun <reified T> ObjectFactory.setProperty(): SetProperty<T> =
    setProperty(T::class.java)


/**
 * Creates a [ListProperty] that holds values of the given type [T].
 *
 * @see [ObjectFactory.listProperty]
 */
inline fun <reified T> ObjectFactory.listProperty(): ListProperty<T> =
    listProperty(T::class.java)


/**
 * Creates a [MapProperty] that holds values of the given key type [K] and value type [V].
 *
 * @see [ObjectFactory.mapProperty]
 */
inline fun <reified K, reified V> ObjectFactory.mapProperty(): MapProperty<K, V> =
    mapProperty(K::class.java, V::class.java)
