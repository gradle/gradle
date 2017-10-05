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

import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.Convention
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
inline
fun <reified T : Any> Convention.getPluginByName(name: String): T =
    plugins[name]?.let {
        (it as T?) ?: throw IllegalStateException("Convention '$name' of type '${it::class.java.name}' cannot be cast to '${T::class.java.name}'.")
    } ?: throw IllegalStateException("A convention named '$name' could not be found.")


inline
fun <reified T : Any> Convention.getPlugin() =
    getPlugin(T::class)


fun <T : Any> Convention.getPlugin(conventionType: KClass<T>) =
    getPlugin(conventionType.java)


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
inline
fun <ConventionType : Any, ReturnType> Any.withConvention(conventionType: KClass<ConventionType>, function: ConventionType.() -> ReturnType): ReturnType =
    when (this) {
        is HasConvention -> convention.getPlugin(conventionType).run(function)
        else -> throw IllegalStateException("Object `$this` doesn't support conventions!")
    }

