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
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.IConventionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.logFieldSerialization
import org.gradle.instantexecution.serialization.logFieldWarning
import java.util.function.Supplier


/**
 * Serializes a bean by serializing the value of each of its fields.
 */
class BeanFieldSerializer(
    private val beanType: Class<*>
) {
    fun WriteContext.serialize(bean: Any) {
        for (field in relevantStateOf(beanType)) {
            field.isAccessible = true
            val fieldValue = field.get(bean)
            val conventionalValue = fieldValue ?: conventionalValueOf(bean, field.name)
            val finalValue = unpack(conventionalValue)
            val writeValue = writeActionFor(finalValue)
            if (writeValue == null) {
                logFieldWarning("serialize", beanType, field.name, "there's no serializer for type '${GeneratedSubclasses.unpackType(finalValue!!).name}'")
                continue
            }
            writeString(field.name)
            try {
                writeValue(finalValue)
            } catch (e: Throwable) {
                throw GradleException("Could not save the value of field '${beanType.name}.${field.name}' with type ${finalValue?.javaClass?.name}.", e)
            }
            logFieldSerialization("serialize", beanType, field.name, finalValue)
        }
        writeString("")
    }

    private
    fun conventionalValueOf(bean: Any, fieldName: String): Any? = (bean as? IConventionAware)?.run {
        conventionMapping.getConventionValue<Any?>(null, fieldName, false)
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
