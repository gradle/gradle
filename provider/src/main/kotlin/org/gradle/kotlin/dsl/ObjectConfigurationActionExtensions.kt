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

import org.gradle.api.Plugin
import org.gradle.api.plugins.ObjectConfigurationAction


/**
 * Adds a Plugin to use to configure the target objects. This method may be called
 * multiple times, to use multiple plugins. Scripts and plugins are applied in the order
 * that they are added.
 *
 * @param T the plugin to apply.
 */
inline fun <reified T : Plugin<*>> ObjectConfigurationAction.plugin() =
    this.plugin(T::class.java)
