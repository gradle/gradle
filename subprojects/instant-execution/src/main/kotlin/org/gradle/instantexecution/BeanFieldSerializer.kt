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
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.IConventionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.internal.serialize.Encoder
import java.util.function.Supplier


/**
 * Serializes a bean by serializing the value of each of its fields.
 */
class BeanFieldSerializer(private val bean: Any, private val beanType: Class<*>, private val stateSerializer: StateSerializer) : ValueSerializer {
    override fun invoke(encoder: Encoder, listener: SerializationListener) {
        encoder.apply {
            for (field in relevantStateOf(beanType)) {
                field.isAccessible = true
                val fieldValue = field.get(bean)
                val conventionalValue = fieldValue ?: conventionalValueOf(bean, field.name)
                val finalValue = unpack(conventionalValue)
                val valueSerializer = stateSerializer.serializerFor(finalValue)
                if (valueSerializer == null) {
                    if (finalValue == null) {
                        listener.logFieldWarning("serialize", beanType, field.name, "there's no serializer for null values")
                    } else {
                        listener.logFieldWarning("serialize", beanType, field.name, "there's no serializer for type '${GeneratedSubclasses.unpackType(finalValue).name}'")
                    }
                    continue
                }
                writeString(field.name)
                try {
                    valueSerializer(this, listener)
                } catch (e: Exception) {
                    throw GradleException("Could not save the value of field '${beanType.name}.${field.name}'.", e)
                }
                listener.logFieldSerialization("serialize", beanType, field.name, finalValue)
            }
            writeString("")
        }
    }

    private
    fun conventionalValueOf(bean: Any, fieldName: String): Any? {
        if (bean is IConventionAware) {
            return bean.conventionMapping.getConventionValue(null, fieldName, false)
        } else {
            return null
        }
    }

    private
    fun unpack(fieldValue: Any?) = when (fieldValue) {
        is DirectoryProperty -> fieldValue.asFile.orNull
        is RegularFileProperty -> fieldValue.asFile.orNull
        is Property<*> -> fieldValue.orNull
        is Provider<*> -> fieldValue.orNull
        is Supplier<*> -> fieldValue.get()
        is Function0<*> -> (fieldValue as (() -> Any?)).invoke()
        is Lazy<*> -> fieldValue.value
        else -> fieldValue
    }
}
