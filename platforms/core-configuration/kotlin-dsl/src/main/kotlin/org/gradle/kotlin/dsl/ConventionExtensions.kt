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

@file:Suppress("DEPRECATION")
package org.gradle.kotlin.dsl

import org.gradle.api.plugins.Convention
import org.gradle.kotlin.dsl.accessors.runtime.conventionOf
import org.gradle.kotlin.dsl.accessors.runtime.conventionPluginByName

import kotlin.reflect.KClass


/**
 * Looks for the convention plugin of a given name and casts it to the expected type [T].
 *
 * If no convention is found or if the one found cannot be cast to the expected type it will throw an [IllegalStateException].
 *
 * @param name convention plugin name
 * @return the convention plugin, never null
 * @throws [IllegalStateException] When the convention cannot be found or cast to the expected type.
 */
@Deprecated("The concept of conventions is deprecated. Use extensions instead.")
inline fun <reified T : Any> Convention.getPluginByName(name: String): T =
    conventionPluginByName(this, name).let {
        (it as T?) ?: throw IllegalStateException("Convention '$name' of type '${it::class.java.name}' cannot be cast to '${T::class.java.name}'.")
    }


/**
 * Locates the plugin convention object with the given type.
 *
 * @param T the convention plugin type
 * @return the convention plugin
 * @throws [IllegalStateException] when there is no such object contained in this convention, or when there are multiple such objects
 * @see [Convention.getPlugin]
 */
@Deprecated("The concept of conventions is deprecated. Use extensions instead.")
inline fun <reified T : Any> Convention.getPlugin(): T =
    getPlugin(T::class)


/**
 * Locates the plugin convention object with the given type.
 *
 * @param conventionType the convention plugin type
 * @return the convention plugin
 * @throws [IllegalStateException] when there is no such object contained in this convention, or when there are multiple such objects
 * @see [Convention.getPlugin]
 */
@Deprecated("The concept of conventions is deprecated. Use extensions instead.")
fun <T : Any> Convention.getPlugin(conventionType: KClass<T>): T =
    getPlugin(conventionType.java)


/**
 * Locates the plugin convention object with the given type.
 *
 * @param T the convention plugin type.
 * @return the convention plugin, or null if there is no such convention plugin
 * @throws [IllegalStateException] when there are multiple matching objects
 * @see [Convention.findPlugin]
 */
@Deprecated("The concept of conventions is deprecated. Use extensions instead.")
inline fun <reified T : Any> Convention.findPlugin(): T? =
    findPlugin(T::class)


/**
 * Locates the plugin convention object with the given type.
 *
 * @param conventionType the convention plugin type.
 * @return the convention plugin, or null if there is no such convention plugin
 * @throws [IllegalStateException] when there are multiple matching objects
 * @see [Convention.findPlugin]
 */
@Deprecated("The concept of conventions is deprecated. Use extensions instead.")
fun <T : Any> Convention.findPlugin(conventionType: KClass<T>): T? =
    findPlugin(conventionType.java)


/**
 * Evaluates the given [function] against the convention plugin of the given [conventionType].
 *
 * @param conventionType the type of the convention to be located.
 * @param function function to be evaluated.
 * @return the value returned by the given [function].
 * @throws [IllegalStateException] When the receiver does not support convention plugins, when there is no convention plugin of the given type, or when there are multiple such plugins.
 *
 * @see [Convention.getPlugin]
 */
@Deprecated("The concept of conventions is deprecated. Use extensions instead.")
inline fun <ConventionType : Any, ReturnType> Any.withConvention(
    conventionType: KClass<ConventionType>,
    function: ConventionType.() -> ReturnType
): ReturnType =
    conventionOf(this).getPlugin(conventionType.java).run(function)
