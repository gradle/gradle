/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.beans

import com.google.common.primitives.Primitives.wrap
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.IConventionAware
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.withDebugFrame
import java.lang.reflect.Field


class BeanPropertyWriter(
    beanType: Class<*>
) : BeanStateWriter {

    private
    val relevantFields = relevantStateOf(beanType)

    /**
     * Serializes a bean by serializing the value of each of its fields.
     */
    override suspend fun WriteContext.writeStateOf(bean: Any) {
        for (relevantField in relevantFields) {
            val field = relevantField.field
            val fieldName = field.name
            val fieldValue =
                when (val isExplicitValue = relevantField.isExplicitValueField) {
                    null -> field.get(bean)
                    else -> conventionValueOf(bean, field, isExplicitValue)
                }
            relevantField.unsupportedFieldType?.let {
                reportUnsupportedFieldType(it, "serialize", fieldName, fieldValue)
            }
            withDebugFrame({ field.debugFrameName() }) {
                writeNextProperty(fieldName, fieldValue, PropertyKind.Field)
            }
        }
    }

    private
    fun conventionValueOf(bean: Any, field: Field, isExplicitValue: Field) =
        field.get(bean).let { fieldValue ->
            if (isExplicitValue.get(bean).uncheckedCast()) {
                fieldValue
            } else {
                getConventionValue(bean, field, fieldValue)
                    ?.takeIf { conventionValue ->
                        // Prevent convention value to be assigned to a field of incompatible type
                        // A common cause is a regular field type being promoted to a Property/Provider type.
                        conventionValue.isAssignableTo(field.type)
                    } ?: fieldValue
            }
        }

    private
    fun getConventionValue(bean: Any, field: Field, fieldValue: Any?) =
        bean.uncheckedCast<IConventionAware>()
            .conventionMapping
            .getConventionValue<Any?>(fieldValue, field.name, false)

    private
    fun Field.debugFrameName() =
        "${declaringClass.typeName}.$name"

    private
    fun Any?.isAssignableTo(type: Class<*>) =
        (if (type.isPrimitive) wrap(type) else type)
            .isInstance(this)
}


/**
 * Writes a bean property.
 *
 * A property can only be written when there's a suitable [Codec] for its [value].
 */
suspend fun WriteContext.writeNextProperty(name: String, value: Any?, kind: PropertyKind) {
    withPropertyTrace(kind, name) {
        try {
            write(value)
        } catch (error: Exception) {
            onError(error) {
                when {
                    value != null -> {
                        text("error writing value of type ")
                        reference(GeneratedSubclasses.unpackType(value))
                    }

                    else -> {
                        text("error writing null value")
                    }
                }
            }
        }
    }
}
