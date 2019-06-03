/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.serialization.beans

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultPropertyState
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.logProperty
import org.gradle.instantexecution.serialization.logPropertyWarning
import org.gradle.internal.reflect.JavaReflectionUtil
import java.io.File
import java.lang.reflect.Field
import java.util.function.Supplier


class BeanPropertyReader(
    private val beanType: Class<*>,
    private val filePropertyFactory: FilePropertyFactory
) {
    fun ReadContext.readFieldsOf(bean: Any) {
        val fieldsByName = relevantStateOf(beanType).associateBy { it.name }
        while (true) {
            val (fieldName, fieldValue) = readNextProperty(PropertyKind.Field) ?: break
            val field = fieldsByName.getValue(fieldName)
            setFieldOf(bean, field, fieldValue)
        }
    }

    /**
     * Returns `null` when there are no more properties to be read.
     */
    fun ReadContext.readNextProperty(kind: PropertyKind): Pair<String, Any?>? {

        val name = readString()
        if (name.isEmpty()) {
            return null
        }

        val value =
            try {
                read().also {
                    logProperty("deserialize", kind, beanType, name, it)
                }
            } catch (e: Throwable) {
                throw GradleException("Could not load the value of $kind '${beanType.name}.$name'.", e)
            }

        return name to value
    }

    private
    fun ReadContext.setFieldOf(bean: Any, field: Field, value: Any?) {

        fun assign(value: Any?) = field.set(bean, value)

        @Suppress("unchecked_cast")
        when (val type = field.type) {
            DirectoryProperty::class.java -> {
                assign(
                    filePropertyFactory.newDirectoryProperty().apply {
                        set(value as File?)
                    }
                )
            }
            RegularFileProperty::class.java -> {
                assign(
                    filePropertyFactory.newFileProperty().apply {
                        set(value as File?)
                    }
                )
            }
            Property::class.java -> {
                assign(
                    DefaultPropertyState(Any::class.java).apply {
                        set(value)
                    }
                )
            }
            Provider::class.java -> {
                assign(when (value) {
                    null -> Providers.notDefined()
                    else -> Providers.of(value)
                })
            }
            Supplier::class.java -> assign(Supplier { value })
            Function0::class.java -> assign({ value })
            Lazy::class.java -> assign(lazyOf(value))
            else -> {
                if (isAssignableTo(type, value)) {
                    assign(value)
                } else if (value != null) {
                    logPropertyWarning("deserialize", PropertyKind.Field, beanType, field.name, "value $value is not assignable to $type")
                } // else null value -> ignore
            }
        }
    }

    private
    fun isAssignableTo(type: Class<*>, value: Any?) =
        type.isInstance(value) ||
            type.isPrimitive && JavaReflectionUtil.getWrapperTypeForPrimitiveType(type).isInstance(value)
}
