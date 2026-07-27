/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.provider.Property
import org.gradle.internal.deprecation.DeprecationLogger

import kotlin.reflect.KProperty


/**
 * Property delegate for [Property] instances.
 *
 * Example: `val someProperty by somePropertyState`
 */
@Deprecated("Use 'val value = property.get()' instead. See the Gradle 9.6 upgrading guide.")
operator fun <T : Any> Property<T>.getValue(receiver: Any?, property: KProperty<*>): T {
    DeprecationLogger.deprecate("The 'val value by property' property delegate syntax")
        .withAdvice("Use 'val value = property.get()' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    return get()
}


/**
 * Property delegate for [Property] instances.
 *
 * Example: `var someProperty by somePropertyState`
 */
@Deprecated("Use 'property.set(value)' instead. See the Gradle 9.6 upgrading guide.")
operator fun <T : Any> Property<T>.setValue(receiver: Any?, property: KProperty<*>, value: T) {
    DeprecationLogger.deprecate("The 'var name by property; name = value' property delegate syntax")
        .withAdvice("Use 'property.set(value)' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    set(value)
}
