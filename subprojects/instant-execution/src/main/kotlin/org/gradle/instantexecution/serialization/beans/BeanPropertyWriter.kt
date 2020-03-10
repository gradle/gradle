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
import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.IConventionAware
import org.gradle.instantexecution.InstantExecutionError
import org.gradle.instantexecution.InstantExecutionProblemsException
import org.gradle.instantexecution.extensions.maybeUnwrapInvocationTargetException
import org.gradle.instantexecution.problems.PropertyKind
import org.gradle.instantexecution.problems.propertyDescriptionFor
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.IsolateContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.logPropertyInfo
import org.gradle.instantexecution.serialization.logUnsupported
import java.io.IOException
import java.util.concurrent.Callable
import java.util.function.Supplier


class BeanPropertyWriter(
    beanType: Class<*>
) : BeanStateWriter {

    private
    val relevantFields = relevantStateOf(beanType)

    /**
     * Serializes a bean by serializing the value of each of its fields.
     */
    override suspend fun WriteContext.writeStateOf(bean: Any) {
        for (field in relevantFields) {
            val fieldName = field.name
            val fieldValue = valueOrConvention(field.get(bean), bean, fieldName)
            reportFieldProblems(fieldName, field.type, fieldValue)
            writeNextProperty(fieldName, fieldValue, PropertyKind.Field)
        }
    }

    private
    fun conventionalValueOf(bean: Any, fieldName: String): Any? = (bean as? IConventionAware)?.run {
        conventionMapping.getConventionValue<Any?>(null, fieldName, false)
    }

    private
    fun valueOrConvention(fieldValue: Any?, bean: Any, fieldName: String): Any? = when (fieldValue) {
        is Closure<*> -> fieldValue
        // TODO - do not eagerly evaluate these types
        is Callable<*> -> fieldValue.call()
        is Supplier<*> -> fieldValue.get()
        is Function0<*> -> fieldValue.invoke()
        is Lazy<*> -> fieldValue.value
        else -> fieldValue ?: conventionalValueOf(bean, fieldName)
    }
}


private
fun WriteContext.reportFieldProblems(fieldName: String, fieldType: Class<*>, fieldValue: Any?) {
    withPropertyTrace(PropertyKind.Field, fieldName) {
        unsupportedFieldDeclaredTypes
            .firstOrNull { it.java.isAssignableFrom(fieldType) }
            ?.let { unsupported ->
                if (fieldValue == null) logUnsupported(unsupported)
                else logUnsupported(unsupported, fieldValue::class.java)
            }
    }
}


/**
 * Returns whether the given property could be written. A property can only be written when there's
 * a suitable [Codec] for its [value].
 */
suspend fun WriteContext.writeNextProperty(name: String, value: Any?, kind: PropertyKind) {
    withPropertyTrace(kind, name) {
        try {
            write(value)
        } catch (passThrough: IOException) {
            throw passThrough
        } catch (passThrough: InstantExecutionProblemsException) {
            throw passThrough
        } catch (error: Exception) {
            throw InstantExecutionError(propertyErrorMessage(value), error.maybeUnwrapInvocationTargetException())
        }
        logPropertyInfo("serialize", value)
    }
}


private
fun IsolateContext.propertyErrorMessage(value: Any?) =
    "${propertyDescriptionFor(trace)}: error writing value of type '${value?.let { unpackedTypeNameOf(it) } ?: "null"}'"


private
fun unpackedTypeNameOf(value: Any) =
    GeneratedSubclasses.unpackType(value).name
