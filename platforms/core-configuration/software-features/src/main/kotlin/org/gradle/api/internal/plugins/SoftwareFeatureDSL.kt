/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.plugins

import org.gradle.plugin.software.internal.DefaultSoftwareFeatureBindingBuilder
import org.gradle.plugin.software.internal.DefaultSoftwareTypeBindingBuilder


fun softwareFeature(block: SoftwareFeatureBindingBuilder.() -> Unit): SoftwareFeatureBinding {
    return DefaultSoftwareFeatureBindingBuilder().apply(block).build()
}

fun softwareType(block: SoftwareTypeBindingBuilder.() -> Unit): SoftwareFeatureBinding {
    return DefaultSoftwareTypeBindingBuilder().apply(block).build()
}

inline fun <reified T: Any, reified U: Any, reified V: Any> SoftwareFeatureBindingBuilder.bind(name: String, noinline block: SoftwareFeatureApplicationContext.(T, U, V) -> Unit): SoftwareFeatureBindingBuilder {
    return this.bind(name, T::class.java, U::class.java, V::class.java, block)
}

inline fun <reified T: Any, reified U: Any> SoftwareTypeBindingBuilder.bind(name: String, noinline block: SoftwareFeatureApplicationContext.(T, U) -> Unit): SoftwareTypeBindingBuilder {
    return this.bind(name, T::class.java, U::class.java, block)
}
