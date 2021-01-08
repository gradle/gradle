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

import org.gradle.api.InvalidUserCodeException

import org.gradle.api.internal.DynamicObjectAware
import org.gradle.api.internal.plugins.DslObject
import org.gradle.internal.metaobject.DynamicObject
import org.gradle.kotlin.dsl.support.uncheckedCast

import kotlin.reflect.KProperty


/**
 * Provides efficient access to a property.
 */
interface PropertyDelegate {
    operator fun <T> getValue(receiver: Any?, property: KProperty<*>): T
}


/**
 * Provides efficient access to a mutable dynamic property.
 */
interface MutablePropertyDelegate : PropertyDelegate {
    operator fun <T> setValue(receiver: Any?, property: KProperty<*>, value: T)
}


internal
fun propertyDelegateFor(target: Any, property: KProperty<*>): PropertyDelegate =
    dynamicObjectFor(target).let { owner ->
        if (property.returnType.isMarkedNullable) NullableDynamicPropertyDelegate(owner, property.name)
        else NonNullDynamicPropertyDelegate(owner, property.name, { target.toString() })
    }


private
fun dynamicObjectFor(target: Any): DynamicObject =
    (target as? DynamicObjectAware ?: DslObject(target)).asDynamicObject


private
class NullableDynamicPropertyDelegate(
    private val owner: DynamicObject,
    private val name: String
) : PropertyDelegate {

    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        owner.tryGetProperty(name).run {
            uncheckedCast(if (isFound) value else null)
        }
}


private
class NonNullDynamicPropertyDelegate(
    private val owner: DynamicObject,
    private val name: String,
    private val describeOwner: () -> String
) : PropertyDelegate {

    override fun <T> getValue(receiver: Any?, property: KProperty<*>): T =
        owner.tryGetProperty(name).run {
            if (isFound && value != null) uncheckedCast<T>(value)
            else throw InvalidUserCodeException("Cannot get non-null property '$name' on ${describeOwner()} as it ${if (isFound) "is null" else "does not exist"}")
        }
}
