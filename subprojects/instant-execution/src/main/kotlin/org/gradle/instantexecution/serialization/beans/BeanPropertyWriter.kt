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

import groovy.lang.Closure
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.IConventionAware
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.instantexecution.InstantExecutionException
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.logPropertyError
import org.gradle.instantexecution.serialization.logPropertyInfo
import org.gradle.instantexecution.serialization.logPropertyWarning
import java.io.IOException
import java.util.concurrent.Callable
import java.util.function.Supplier


class BeanPropertyWriter(
    beanType: Class<*>
) {

    private
    val relevantFields = relevantStateOf(beanType).toList()

    /**
     * Serializes a bean by serializing the value of each of its fields.
     */
    suspend fun WriteContext.writeFieldsOf(bean: Any) {
        writingProperties {
            for (field in relevantFields) {
                val fieldName = field.name
                val fieldValue = unpack(field.get(bean)) ?: unpack(conventionalValueOf(bean, fieldName))
                writeNextProperty(fieldName, fieldValue, PropertyKind.Field)
            }
        }
    }

    private
    fun conventionalValueOf(bean: Any, fieldName: String): Any? = (bean as? IConventionAware)?.run {
        conventionMapping.getConventionValue<Any?>(null, fieldName, false)
    }

    fun unpack(fieldValue: Any?): Any? = when (fieldValue) {
        is DirectoryProperty -> fieldValue.asFile.orNull
        is RegularFileProperty -> fieldValue.asFile.orNull
        is Property<*> -> fieldValue.orNull
        is Provider<*> -> fieldValue.orNull
        is Closure<*> -> fieldValue.dehydrate()
        is Callable<*> -> fieldValue.call()
        is Supplier<*> -> fieldValue.get()
        is Function0<*> -> (fieldValue as (() -> Any?)).invoke()
        is Lazy<*> -> unpack(fieldValue.value)
        else -> fieldValue
    }
}


/**
 * Returns whether the given property could be written. A property can only be written when there's
 * a suitable [Codec] for its [value].
 */
suspend fun WriteContext.writeNextProperty(name: String, value: Any?, kind: PropertyKind): Boolean {
    withPropertyTrace(kind, name) {
        val writeValue = writeActionFor(value)
        if (writeValue == null) {
            logPropertyWarning("serialize") {
                text("there's no serializer for type")
                reference(unpackedTypeNameOf(value!!))
            }
            return false
        }
        writeString(name)
        try {
            writeValue(value)
        } catch (passThrough: IOException) {
            throw passThrough
        } catch (passThrough: InstantExecutionException) {
            throw passThrough
        } catch (e: Exception) {
            logPropertyError("write", e) {
                text("error writing value of type ")
                reference(value?.let { unpackedTypeNameOf(it) } ?: "null")
            }
            return false
        }
        logPropertyInfo("serialize", value)
        return true
    }
}


private
fun unpackedTypeNameOf(value: Any) = GeneratedSubclasses.unpackType(value).name


/**
 * Ensures a sequence of [writeNextProperty] calls is properly terminated
 * by the end marker (empty String) so that it can be read by [readEachProperty].
 */
inline fun WriteContext.writingProperties(block: () -> Unit) {
    block()
    writeString("")
}
