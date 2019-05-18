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
import org.gradle.api.internal.IConventionAware
import org.gradle.api.provider.Property
import org.gradle.internal.serialize.Encoder
import java.lang.reflect.Field
import java.util.function.Supplier


/**
 * Serializes a bean by serializing the value of each of its fields.
 */
class BeanFieldSerializer(private val bean: Any, private val fields: Sequence<Field>, private val stateSerializer: StateSerializer) : ValueSerializer {
    override fun invoke(encoder: Encoder, listener: SerializationListener) {
        encoder.apply {
            for (field in fields) {
                field.isAccessible = true
                val fieldValue = field.get(bean)
                val conventionalValue = fieldValue ?: conventionalValueOf(bean, field.name)
                val finalValue = unpack(conventionalValue) ?: continue
                val valueSerializer = stateSerializer.serializerFor(finalValue)
                if (valueSerializer == null) {
                    listener.logFieldWarning("serialize", bean.javaClass, field.name, "there's no serializer for type '${finalValue.javaClass.name}'")
                    continue
                }
                writeString(field.name)
                try {
                    valueSerializer(this, listener)
                } catch (e: Exception) {
                    throw GradleException("Could not save the value of field '${bean.javaClass.name}.${field.name}'.", e)
                }
                listener.logFieldSerialization("serialize", bean.javaClass, field.name, finalValue)
            }
            writeString("")
        }
    }

    private
    fun conventionalValueOf(task: Any, fieldName: String): Any? {
        if (task is IConventionAware) {
            return task.conventionMapping.getConventionValue(null, fieldName, false)
        } else {
            return null
        }
    }

    private
    fun unpack(fieldValue: Any?) = when (fieldValue) {
        is DirectoryProperty -> fieldValue.asFile.orNull
        is RegularFileProperty -> fieldValue.asFile.orNull
        is Property<*> -> fieldValue.orNull
        is Supplier<*> -> fieldValue.get()
        is Function0<*> -> (fieldValue as (() -> Any?)).invoke()
        else -> fieldValue
    }
}
