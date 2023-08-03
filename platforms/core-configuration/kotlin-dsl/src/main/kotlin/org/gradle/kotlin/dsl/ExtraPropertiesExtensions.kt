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
operator fun ExtraPropertiesExtension.provideDelegate(receiver: Any?, property: KProperty<*>): MutablePropertyDelegate =
    if (property.returnType.isMarkedNullable) NullableExtraPropertyDelegate(this, property.name)
    else NonNullExtraPropertyDelegate(this, property.name)


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
inline operator fun <T> ExtraPropertiesExtension.invoke(initialValueProvider: () -> T): InitialValueExtraPropertyDelegateProvider<T> =
    invoke(initialValueProvider())


/**
 * Returns a property delegate provider that will initialize the extra property to the given [initialValue].
 *
 * Usage: `val answer by extra(42)`
 */
operator fun <T> ExtraPropertiesExtension.invoke(initialValue: T): InitialValueExtraPropertyDelegateProvider<T> =
    InitialValueExtraPropertyDelegateProvider.of(this, initialValue)


/**
 * Enables typed access to extra properties with initial value.
 */
class InitialValueExtraPropertyDelegateProvider<T>
private constructor(
    private val extra: ExtraPropertiesExtension,
    private val initialValue: T
) {
    companion object {
        fun <T> of(extra: ExtraPropertiesExtension, initialValue: T) =
            InitialValueExtraPropertyDelegateProvider(extra, initialValue)
    }

    operator fun provideDelegate(thisRef: Any?, property: kotlin.reflect.KProperty<*>): InitialValueExtraPropertyDelegate<T> {
        extra.set(property.name, initialValue)
        return InitialValueExtraPropertyDelegate.of(extra)
    }
}


/**
 * Enables typed access to extra properties with initial value.
 */
class InitialValueExtraPropertyDelegate<T>
private constructor(
    private val extra: ExtraPropertiesExtension
) {
    companion object {
        fun <T> of(extra: ExtraPropertiesExtension) =
            InitialValueExtraPropertyDelegate<T>(extra)
    }

    operator fun setValue(receiver: Any?, property: kotlin.reflect.KProperty<*>, value: T) =
        extra.set(property.name, value)

    @Suppress("unchecked_cast")
    operator fun getValue(receiver: Any?, property: kotlin.reflect.KProperty<*>): T =
        uncheckedCast(extra.get(property.name))
}
