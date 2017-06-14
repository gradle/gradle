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
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.ExtraPropertiesExtension

import org.gradle.internal.Cast.uncheckedCast

import kotlin.reflect.KProperty


/**
 * The extra properties extension in this object's extension container.
 *
 * @see ExtensionContainer.getExtraProperties
 */
val ExtensionAware.extra: ExtraPropertiesExtension
    get() = extensions.extraProperties


operator fun <T> ExtraPropertiesExtension.setValue(receiver: Any?, property: KProperty<*>, value: T) =
    set(property.name, value)


operator fun <T> ExtraPropertiesExtension.getValue(receiver: Any?, property: KProperty<*>): T =
    /* We would like to be able to express optional properties via nullability of the return type
       but Kotlin won't let us reflect on `property.returnType` here and complain with:
           Not supported for local property reference.
    uncheckedCast(
        if (property.returnType.isMarkedNullable && !has(property.name)) null
        else get(property.name))
    */
    uncheckedCast(get(property.name))


/**
 * Returns a property delegate provider that will initialize the extra property to the given [initialValue].
 *
 * Usage: `val answer by extra(42)`
 */
operator fun <T> ExtraPropertiesExtension.invoke(initialValue: T): ExtraPropertyDelegateProvider<T> =
    ExtraPropertyDelegateProvider(this, initialValue)


class ExtraPropertyDelegateProvider<T>(
    val extra: ExtraPropertiesExtension,
    val initialValue: T) {

    operator fun provideDelegate(thisRef: Any?, property: kotlin.reflect.KProperty<*>): ExtraPropertyDelegate<T> {
        extra.set(property.name, initialValue)
        return ExtraPropertyDelegate(extra)
    }
}


/**
 * Enables typed access to extra properties.
 */
class ExtraPropertyDelegate<T>(val extra: ExtraPropertiesExtension) {

    operator fun setValue(receiver: Any?, property: kotlin.reflect.KProperty<*>, value: T) =
        extra.set(property.name, value)

    @Suppress("unchecked_cast")
    operator fun getValue(receiver: Any?, property: kotlin.reflect.KProperty<*>): T =
        uncheckedCast(extra.get(property.name))
}
