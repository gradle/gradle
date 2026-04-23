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

package org.gradle.internal.declarativedsl

import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

object Workarounds {
    /**
     * Used to match an overriding function and its overriden counterpart in the supertype.
     *
     * It is not very nice that we rely on this internal signature, but we don't have much of a choice...
     * We have also tried using the "descriptor" field and the "overriddenDescriptors" and/or "overriddenFunctions" from that,
     * but it's even uglier with a lot of weird lazy types involved and the code for doing so becomes very brittle.
     */
    internal fun kFunctionSignature(function: KFunction<*>): String {
        return kFunctionImplSignature(function)
    }

    private val kFunctionImplSignature: (KFunction<*>) -> String by lazy {
        val property: KProperty<*> = Class.forName("kotlin.reflect.jvm.internal.DescriptorKFunction").kotlin.memberProperties.first { it.name == "signature" }.apply { isAccessible = true }
        return@lazy { kFunction: KFunction<*> -> property.call(kFunction) as String }
    }
}
