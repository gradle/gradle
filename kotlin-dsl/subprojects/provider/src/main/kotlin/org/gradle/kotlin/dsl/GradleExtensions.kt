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

import org.gradle.api.Plugin
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.PluginAware


/**
 * Applies the plugin of the given type [T]. Does nothing if the plugin has already been applied.
 *
 * The given class should implement the [Plugin] interface, and be parameterized for a
 * compatible type of `this`.
 *
 * @param T the plugin type.
 * @see [PluginAware.apply]
 */
inline fun <reified T : Plugin<Gradle>> Gradle.apply() =
    (this as PluginAware).apply<T>()
