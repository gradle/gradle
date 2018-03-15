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
import org.gradle.api.plugins.PluginAware


fun PluginAware.apply(from: Any? = null, plugin: String? = null, to: Any? = null) {
    apply {
        if (plugin != null) it.plugin(plugin)
        if (from != null) it.from(from)
        if (to != null) it.to(to)
    }
}


inline
fun <reified T : Plugin<*>> PluginAware.apply(to: Any? = null) {
    apply {
        it.plugin(T::class.java)
        if (to != null) it.to(to)
    }
}
