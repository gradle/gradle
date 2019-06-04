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
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.logProperty
import org.gradle.instantexecution.serialization.logPropertyWarning
import java.util.function.Supplier


class BeanPropertyWriter(
    private val beanType: Class<*>
) {

    private
    val relevantFields = relevantStateOf(beanType)

    /**
     * Serializes a bean by serializing the value of each of its fields.
     */
    fun WriteContext.writeFieldsOf(bean: Any) {
        writingProperties {
            for (field in relevantFields) {
                val fieldName = field.name
                val fieldValue = unpack(field.get(bean)) ?: unpack(conventionalValueOf(bean, fieldName))
                writeNextProperty(fieldName, fieldValue, PropertyKind.Field)
            }
        }
    }

    /**
     * Ensures a sequence of [writeNextProperty] calls is properly terminated
     * by the end marker (empty String) so that it can be read by [BeanPropertyReader.readNextProperty].
     */
    inline fun WriteContext.writingProperties(block: BeanPropertyWriter.() -> Unit) {
        block()
        writeString("")
    }

    /**
     * Returns whether the given property could be written. A property can only be written when there's
     * a suitable [Codec] for its [value].
     */
    fun WriteContext.writeNextProperty(name: String, value: Any?, kind: PropertyKind): Boolean {
        val writeValue = writeActionFor(value)
        if (writeValue == null) {
            logPropertyWarning("serialize", kind, beanType, name, "there's no serializer for type '${GeneratedSubclasses.unpackType(value!!).name}'")
            return false
        }
        writeString(name)
        try {
            writeValue(value)
        } catch (e: Throwable) {
            throw GradleException("Could not save the value of $kind '${beanType.name}.$name' with type ${value?.javaClass?.name}.", e)
        }
        logProperty("serialize", kind, beanType, name, value)
        return true
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
