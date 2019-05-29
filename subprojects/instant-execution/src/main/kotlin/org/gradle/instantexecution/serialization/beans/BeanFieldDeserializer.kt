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
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.logFieldSerialization
import org.gradle.instantexecution.serialization.logFieldWarning
import org.gradle.internal.reflect.JavaReflectionUtil
import java.io.File
import java.util.function.Supplier


class BeanFieldDeserializer(
    private val beanType: Class<*>,
    private val filePropertyFactory: FilePropertyFactory
) {
    fun ReadContext.deserialize(bean: Any) {
        val fieldsByName = relevantStateOf(beanType).associateBy { it.name }
        while (true) {
            val fieldName = readString()
            if (fieldName.isEmpty()) {
                break
            }
            try {
                val value = read()
                val field = fieldsByName.getValue(fieldName)
                field.isAccessible = true
                logFieldSerialization("deserialize", beanType, fieldName, value)
                @Suppress("unchecked_cast")
                when (val type = field.type) {
                    DirectoryProperty::class.java -> {
                        val dirProperty = filePropertyFactory.newDirectoryProperty()
                        dirProperty.set(value as File?)
                        field.set(bean, dirProperty)
                    }
                    RegularFileProperty::class.java -> {
                        val fileProperty = filePropertyFactory.newFileProperty()
                        fileProperty.set(value as File?)
                        field.set(bean, fileProperty)
                    }
                    Property::class.java -> {
                        val property = DefaultPropertyState(Any::class.java)
                        property.set(value)
                        field.set(bean, property)
                    }
                    Provider::class.java -> {
                        val provider = if (value == null) {
                            Providers.notDefined()
                        } else {
                            Providers.of(value)
                        }
                        field.set(bean, provider)
                    }
                    Supplier::class.java -> field.set(bean, Supplier { value })
                    Function0::class.java -> field.set(bean, { value })
                    Lazy::class.java -> field.set(bean, lazyOf(value))
                    else -> {
                        if (isAssignableTo(type, value)) {
                            field.set(bean, value)
                        } else if (value != null) {
                            logFieldWarning("deserialize", beanType, fieldName, "value $value is not assignable to $type")
                        } // else null value -> ignore
                    }
                }
            } catch (e: Exception) {
                throw GradleException("Could not load the value of field '${beanType.name}.$fieldName'.", e)
            }
        }
    }

    private
    fun isAssignableTo(type: Class<*>, value: Any?) =
        type.isInstance(value) ||
            type.isPrimitive && JavaReflectionUtil.getWrapperTypeForPrimitiveType(type).isInstance(value)
}
