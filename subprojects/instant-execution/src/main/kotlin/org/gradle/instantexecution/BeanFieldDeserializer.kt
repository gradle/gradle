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

package org.gradle.instantexecution

import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.file.FilePropertyFactory
import org.gradle.api.internal.provider.DefaultPropertyState
import org.gradle.api.internal.provider.Providers
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.reflect.JavaReflectionUtil
import org.gradle.internal.serialize.Decoder
import java.io.File
import java.util.function.Supplier


class BeanFieldDeserializer(
    private val bean: Any,
    private val beanType: Class<*>,
    private val deserializer: StateDeserializer,
    private val filePropertyFactory: FilePropertyFactory
) {
    fun deserialize(decoder: Decoder, listener: SerializationListener) {
        val fieldsByName = relevantStateOf(beanType).associateBy { it.name }
        while (true) {
            val fieldName = decoder.readString()
            if (fieldName.isEmpty()) {
                break
            }
            try {
                val value = deserializer.read(decoder, listener)
                val field = fieldsByName.getValue(fieldName)
                field.isAccessible = true
                listener.logFieldSerialization("deserialize", beanType, fieldName, value)
                @Suppress("unchecked_cast")
                when (field.type) {
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
                        val property = DefaultPropertyState<Any>(Any::class.java)
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
                        if (field.type.isInstance(value) || field.type.isPrimitive && JavaReflectionUtil.getWrapperTypeForPrimitiveType(field.type).isInstance(value)) {
                            field.set(bean, value)
                        } else if (value != null) {
                            listener.logFieldWarning("deserialize", beanType, fieldName, "value $value is not assignable to ${field.type}")
                        } // else null value -> ignore
                    }
                }
            } catch (e: Exception) {
                throw GradleException("Could not load the value of field '${beanType.name}.$fieldName'.", e)
            }
        }
    }
}
