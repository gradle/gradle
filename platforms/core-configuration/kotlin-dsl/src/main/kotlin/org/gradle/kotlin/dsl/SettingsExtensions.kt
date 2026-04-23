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
import org.gradle.api.initialization.Settings
import org.gradle.api.plugins.PluginAware
import org.gradle.internal.Factory
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.kotlin.dsl.support.serviceOf

import kotlin.reflect.KProperty


/**
 * Applies the plugin of the given type [T]. Does nothing if the plugin has already been applied.
 *
 * The given class should implement the [Plugin] interface, and be parameterized for a
 * compatible type of `this`.
 *
 * @param T the plugin type.
 * @see [PluginAware.apply]
 */
inline fun <reified T : Plugin<Settings>> Settings.apply() =
    (this as PluginAware).apply<T>()


/**
 * Locates a property on [Settings].
 */
@Deprecated("Use 'providers.gradleProperty(name)' for Gradle properties or 'extra[name]' for extra properties instead. See the Gradle 9.6 upgrading guide.")
@Suppress("DEPRECATION")
operator fun Settings.provideDelegate(any: Any?, property: KProperty<*>): PropertyDelegate {
    if (property.returnType.isMarkedNullable) {
        DeprecationLogger.deprecate("The 'val name: Type? by settings' property delegate syntax")
            .withAdvice(
                "Use 'val property = providers.gradleProperty(name).orNull' for Gradle properties " +
                    "or 'extra[name] as Type?' for extra properties instead."
            )
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    } else {
        DeprecationLogger.deprecate("The 'val name: Type by settings' property delegate syntax")
            .withAdvice(
                "Use 'val property = providers.gradleProperty(name).get()' for Gradle properties " +
                    "or 'val property = extra[name] as Type' for extra properties instead."
            )
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
    }
    return DeprecationLogger.whileDisabled(Factory { propertyDelegateFor(serviceOf(), this, property) })
}
