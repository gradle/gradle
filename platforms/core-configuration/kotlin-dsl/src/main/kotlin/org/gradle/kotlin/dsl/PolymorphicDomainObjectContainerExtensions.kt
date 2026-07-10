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

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.InvalidUserDataException
import org.gradle.api.PolymorphicDomainObjectContainer


/**
 * Defines a new object, which will be created when it is required.
 *
 * @see [PolymorphicDomainObjectContainer.register]
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified T : Any> PolymorphicDomainObjectContainer<in T>.register(name: String): NamedDomainObjectProvider<T> =
    register(name, T::class.java)


/**
 * Defines and configure a new object, which will be created when it is required.
 *
 * @see [PolymorphicDomainObjectContainer.register]
 */
inline fun <reified T : Any> PolymorphicDomainObjectContainer<in T>.register(name: String, noinline configuration: T.() -> Unit): NamedDomainObjectProvider<T> =
    register(name, T::class.java, configuration)


/**
 * Creates a domain object with the specified name and type, adds it to the container,
 * and configures it with the specified action.
 *
 * @param name the name of the domain object to be created
 * @param configuration an action for configuring the domain object
 * @param <U> the type of the domain object to be created
 * @return the created domain object
 * @throws [InvalidUserDataException] if a domain object with the specified name already
 * exists or the container does not support creating a domain object with the specified
 * type
 */
inline fun <reified U : Any> PolymorphicDomainObjectContainer<in U>.create(
    name: String,
    noinline configuration: U.() -> Unit
) =

    this.create(name, U::class.java, configuration)


/**
 * Creates a domain object with the specified name and type, and adds it to the container.
 *
 * @param name the name of the domain object to be created
 * @param <U> the type of the domain object to be created
 * @return the created domain object
 * @throws [InvalidUserDataException] if a domain object with the specified name already
 * exists or the container does not support creating a domain object with the specified
 * type
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified U : Any> PolymorphicDomainObjectContainer<in U>.create(name: String) =
    create(name, U::class.java)


/**
 * Creates a domain object with the specified name and type if it does not exists, and adds it to the container.
 *
 * @param name the name of the domain object to be created
 * @param <U> the type of the domain object to be created
 * @return the created domain object
 * @throws [InvalidUserDataException] if a domain object with the specified name already
 * exists or the container does not support creating a domain object with the specified
 * type
 * @throws [ClassCastException] if a domain object with the specified name exists with a different type
 */
@Suppress("extension_shadowed_by_member")
inline fun <reified U : Any> PolymorphicDomainObjectContainer<in U>.maybeCreate(name: String) =
    maybeCreate(name, U::class.java)
