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

import org.gradle.api.plugins.Convention


/**
 * Looks for the convention plugin of a given name and casts it to the expected type [T].
 *
 * If no convention is found or if the one found cannot be cast to the expected type it will throw an [IllegalStateException].
 *
 * @param name convention plugin name
 * @return the convention plugin, never null
 * @throws IllegalStateException When the convention cannot be found or cast to the expected type.
 */
inline
fun <reified T : Any> Convention.getPluginByName(name: String): T =
    plugins[name]?.let {
        (it as T?) ?: throw IllegalStateException("Convention '$name' of type '${it::class.java.name}' cannot be cast to '${T::class.java.name}'.")
    } ?: throw IllegalStateException("A convention named '$name' could not be found.")
