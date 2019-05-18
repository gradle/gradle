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
import org.gradle.api.provider.Property
import org.gradle.internal.serialize.Decoder
import java.io.File
import java.util.function.Supplier


class BeanFieldDeserializer(private val bean: Any, private val beanType: Class<*>, private val deserializer: StateDeserializer) {
    fun deserialize(decoder: Decoder, listener: SerializationListener) {
        val fieldsByName = relevantStateOf(beanType).associateBy { it.name }
        while (true) {
            val fieldName = decoder.readString()
            if (fieldName.isEmpty()) {
                break
            }
            try {
                val value = deserializer.read(decoder, listener) ?: continue
                val field = fieldsByName.getValue(fieldName)
                field.isAccessible = true
                listener.logFieldSerialization("deserialize", beanType, fieldName, value)
                @Suppress("unchecked_cast")
                when (field.type) {
                    DirectoryProperty::class.java -> (field.get(bean) as? DirectoryProperty)?.set(value as File)
                    RegularFileProperty::class.java -> (field.get(bean) as? RegularFileProperty)?.set(value as File)
                    Property::class.java -> (field.get(bean) as? Property<Any>)?.set(value)
                    Supplier::class.java -> field.set(bean, Supplier { value })
                    Function0::class.java -> field.set(bean, { value })
                    else -> {
                        if (field.type.isAssignableFrom(value.javaClass)) {
                            field.set(bean, value)
                        } else {
                            listener.logFieldWarning("deserialize", beanType, fieldName, "${field.type} != ${value.javaClass}")
                        }
                    }
                }
            } catch (e: Exception) {
                throw GradleException("Could not load the value of field '${beanType.name}.$fieldName'.", e)
            }
        }
    }
}
