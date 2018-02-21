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

import org.gradle.api.plugins.ExtensionAware
import kotlin.reflect.KClass


/**
 * Returns the extension of the specified type.
 */
inline
fun <reified T : Any> ExtensionAware.the() =
    the(T::class)


fun <T : Any> ExtensionAware.the(extensionType: KClass<T>) =
    extensions.findByType(extensionType.java) ?: extensions.getByType(extensionType.java)


/**
 * Executes the given configuration block against the [extension][ExtensionAware] of the specified type.
 *
 * @param T the extension type.
 * @param configuration the configuration block.
 * @see [ExtensionAware]
 */
inline
fun <reified T : Any> ExtensionAware.configure(noinline configuration: T.() -> Unit) =
    extensions.findByType(T::class.java)?.let(configuration)
        ?: extensions.configure(T::class.java, configuration)
