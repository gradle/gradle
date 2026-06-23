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

import org.gradle.api.InvalidUserCodeException
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.internal.Factory
import org.gradle.internal.deprecation.DeprecationLogger

import org.gradle.kotlin.dsl.support.uncheckedCast

import kotlin.reflect.KProperty


/**
 * The extra properties extension in this object's extension container.
 *
 * @see [ExtensionContainer.getExtraProperties]
 */
val ExtensionAware.extra: ExtraPropertiesExtension
    get() = extensions.extraProperties


/**
 * Provides property delegate for typed access to extra properties.
 */
@Deprecated("Use 'val property = extra[name] as Type' instead. See the Gradle 9.6 upgrading guide.")
@Suppress("DEPRECATION")
operator fun ExtraPropertiesExtension.provideDelegate(receiver: Any?, property: KProperty<*>): MutablePropertyDelegate {
    if (property.returnType.isMarkedNullable) {
        DeprecationLogger.deprecate("The 'val name: Type? by extra' property delegate syntax")
            .withAdvice("Use 'val property = extra[name] as Type?' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
        return DeprecationLogger.whileDisabled(Factory { NullableExtraPropertyDelegate(this, property.name) })
    } else {
        DeprecationLogger.deprecate("The 'val name: Type by extra' property delegate syntax")
            .withAdvice("Use 'val property = extra[name] as Type' instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
            .nagUser()
        return DeprecationLogger.whileDisabled(Factory { NonNullExtraPropertyDelegate(this, property.name) })
    }
}


@Suppress("DEPRECATION")
private
class NonNullExtraPropertyDelegate(
    private val extra: ExtraPropertiesExtension,
    private val name: String
) : MutablePropertyDelegate {

    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        if (!extra.has(name)) cannotGetExtraProperty("does not exist")
        else uncheckedCast(extra.get(name) ?: cannotGetExtraProperty("is null"))

    override fun <T> setValue(receiver: Any?, property: KProperty<*>, value: T) =
        extra.set(property.name, value)

    private
    fun cannotGetExtraProperty(reason: String): Nothing =
        throw InvalidUserCodeException("Cannot get non-null extra property '$name' as it $reason")
}


@Suppress("DEPRECATION")
private
class NullableExtraPropertyDelegate(
    private val extra: ExtraPropertiesExtension,
    private val name: String
) : MutablePropertyDelegate {

    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        uncheckedCast(if (extra.has(name)) extra.get(name) else null)

    override fun <T> setValue(receiver: Any?, property: KProperty<*>, value: T) =
        extra.set(property.name, value)
}


/**
 * Returns a property delegate provider that will initialize the extra property to the value provided
 * by [initialValueProvider].
 *
 * Usage: `val answer by extra { 42 }`
 */
@Deprecated("Use 'extra.set(name, value)' instead. See the Gradle 9.6 upgrading guide.")
@Suppress("DEPRECATION")
inline operator fun <T> ExtraPropertiesExtension.invoke(initialValueProvider: () -> T): InitialValueExtraPropertyDelegateProvider<T> =
    invoke(initialValueProvider())


/**
 * Returns a property delegate provider that will initialize the extra property to the given [initialValue].
 *
 * Usage: `val answer by extra(42)`
 */
@Deprecated("Use 'extra.set(name, value)' instead. See the Gradle 9.6 upgrading guide.")
@Suppress("DEPRECATION")
operator fun <T> ExtraPropertiesExtension.invoke(initialValue: T): InitialValueExtraPropertyDelegateProvider<T> {
    DeprecationLogger.deprecate("The 'val name by extra(...)' or 'val name by extra { ... }' property delegate syntax")
        .withAdvice("Use 'extra.set(name, value)' instead.")
        .willBeRemovedInGradle10()
        .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
        .nagUser()
    return DeprecationLogger.whileDisabled(Factory { InitialValueExtraPropertyDelegateProvider.of(this, initialValue) })
}


/**
 * Enables typed access to extra properties with initial value.
 */
@Deprecated("Use 'extra.set(name, value)' instead. See the Gradle 9.6 upgrading guide.")
class InitialValueExtraPropertyDelegateProvider<T>
private constructor(
    private val extra: ExtraPropertiesExtension,
    private val initialValue: T
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T> of(extra: ExtraPropertiesExtension, initialValue: T) =
            InitialValueExtraPropertyDelegateProvider(extra, initialValue).also {
                DeprecationLogger.deprecateType(InitialValueExtraPropertyDelegateProvider::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }

    @Suppress("DEPRECATION")
    operator fun provideDelegate(thisRef: Any?, property: kotlin.reflect.KProperty<*>): InitialValueExtraPropertyDelegate<T> {
        extra.set(property.name, initialValue)
        return DeprecationLogger.whileDisabled(Factory { InitialValueExtraPropertyDelegate.of(extra) })
    }
}


/**
 * Enables typed access to extra properties with initial value.
 */
@Deprecated("Use 'extra.set(name, value)' instead. See the Gradle 9.6 upgrading guide.")
class InitialValueExtraPropertyDelegate<T>
private constructor(
    private val extra: ExtraPropertiesExtension
) {
    companion object {
        @Suppress("DEPRECATION")
        fun <T> of(extra: ExtraPropertiesExtension) =
            InitialValueExtraPropertyDelegate<T>(extra).also {
                DeprecationLogger.deprecateType(InitialValueExtraPropertyDelegate::class.java)
                    .willBeRemovedInGradle10()
                    .withUpgradeGuideSection(9, "kotlin_dsl_delegated_properties")
                    .nagUser()
            }
    }

    operator fun setValue(receiver: Any?, property: kotlin.reflect.KProperty<*>, value: T) =
        extra.set(property.name, value)

    @Suppress("unchecked_cast")
    operator fun getValue(receiver: Any?, property: kotlin.reflect.KProperty<*>): T =
        uncheckedCast(extra.get(property.name))
}
