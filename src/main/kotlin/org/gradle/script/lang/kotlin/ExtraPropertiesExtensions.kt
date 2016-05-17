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

package org.gradle.script.lang.kotlin

import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtraPropertiesExtension

import org.gradle.internal.Cast.uncheckedCast

import kotlin.reflect.KProperty

val ExtensionAware.extra: ExtraPropertiesExtension
    get() = extensions.extraProperties

//region Support for extra delegated properties (val p: String by extensionAware.extra)
//
// Not really useful in the current setting until Kotlin supports local delegated
// properties (http://kotlin.link/articles/Kotlin-Post-1-0-Roadmap.html)
operator fun <T> ExtraPropertiesExtension.setValue(nothing: Nothing?, property: KProperty<*>, value: T?) =
    this.set(property.name, value)

operator fun <T> ExtraPropertiesExtension.getValue(nothing: Nothing?, property: KProperty<*>): T? =
    uncheckedCast<T>(this.get(property.name))
//endregion

