/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.beans

import org.gradle.api.internal.GeneratedSubclasses
import org.gradle.api.internal.IConventionAware
import org.gradle.configurationcache.ConfigurationCacheError
import org.gradle.configurationcache.ConfigurationCacheProblemsException
import org.gradle.configurationcache.extensions.maybeUnwrapInvocationTargetException
import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.problems.propertyDescriptionFor
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.IsolateContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.withDebugFrame
import java.io.IOException
import java.lang.reflect.Field


class BeanPropertyWriter(
    beanType: Class<*>
) : BeanStateWriter {

    private
    val relevantFields = relevantStateOf(beanType)

    /**
     * Serializes a bean by serializing the value of each of its fields.
     */
    override suspend fun WriteContext.writeStateOf(bean: Any) {
        for (relevantField in relevantFields) {
            val field = relevantField.field
            val fieldName = field.name
            val originalFieldValue = field.get(bean)
            val fieldValue = originalFieldValue ?: conventionalValueOf(bean, fieldName)
            relevantField.unsupportedFieldType?.let {
                reportUnsupportedFieldType(it, "serialize", field.name, fieldValue)
            }
            withDebugFrame({ field.debugFrameName() }) {
                writeNextProperty(fieldName, fieldValue, PropertyKind.Field)
            }
        }
    }

    private
    fun conventionalValueOf(bean: Any, fieldName: String): Any? = (bean as? IConventionAware)?.run {
        conventionMapping.getConventionValue<Any?>(null, fieldName, false)
    }

    private
    fun Field.debugFrameName() =
        "${declaringClass.typeName}.$name"
}


/**
 * Writes a bean property.
 *
 * A property can only be written when there's a suitable [Codec] for its [value].
 */
suspend fun WriteContext.writeNextProperty(name: String, value: Any?, kind: PropertyKind) {
    withPropertyTrace(kind, name) {
        try {
            write(value)
        } catch (passThrough: IOException) {
            throw passThrough
        } catch (passThrough: ConfigurationCacheProblemsException) {
            throw passThrough
        } catch (error: Exception) {
            throw ConfigurationCacheError(
                propertyErrorMessage(value),
                error.maybeUnwrapInvocationTargetException()
            )
        }
    }
}


private
fun IsolateContext.propertyErrorMessage(value: Any?) =
    "${propertyDescriptionFor(trace)}: error writing value of type '${
    value?.let { unpackedTypeNameOf(it) } ?: "null"
    }'"


private
fun unpackedTypeNameOf(value: Any) =
    GeneratedSubclasses.unpackType(value).name
