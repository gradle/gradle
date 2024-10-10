/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.serialize.codecs.core

import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.serialize.beans.services.unsupportedFieldTypeFor
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.Decoding
import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.serialize.graph.codecs.EncodingProducer
import org.gradle.internal.serialize.graph.logPropertyProblem
import org.gradle.internal.serialize.graph.readPropertyValue
import org.gradle.internal.serialize.graph.reportUnsupportedFieldType
import org.gradle.internal.serialize.graph.withDebugFrame
import org.gradle.internal.serialize.graph.writePropertyValue
import java.lang.reflect.Field
import java.lang.reflect.Modifier.isStatic


object JavaRecordCodec : EncodingProducer, Decoding {

    override fun encodingForType(type: Class<*>): Encoding? =
        // need to check by name because it's only present in Java 14+
        JavaRecordEncoding.takeIf { type.superclass?.canonicalName == "java.lang.Record" }

    @Suppress("SpreadOperator")
    override suspend fun ReadContext.decode(): Any? {
        val recordType = readClass()
        val fields = recordType.relevantFields

        val args = readFields(fields)
        val types = fields.map { it.type }.toTypedArray()
        return try {
            recordType.getConstructor(*types).newInstance(*args.toTypedArray())
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to create instance of ${recordType.name} with args $args", ex)
        }
    }

    private
    suspend fun ReadContext.readFields(fields: List<Field>): List<Any?> {
        val args = mutableListOf<Any?>()
        for (field in fields) {
            val fieldName = field.name
            unsupportedFieldTypeFor(field)?.let {
                reportUnsupportedFieldType(it, "deserialize", fieldName)
            }
            readPropertyValue(PropertyKind.Field, fieldName) { fieldValue ->
                if (fieldValue == null || field.type.isInstance(fieldValue) || field.type.isPrimitive) {
                    args.add(fieldValue)
                } else {
                    logPropertyProblem("deserialize") {
                        text("value ")
                        reference(fieldValue.toString())
                        text(" is not assignable to ")
                        reference(field.type)
                    }
                    args.add(null)
                }
            }
        }
        return args
    }
}


private
object JavaRecordEncoding : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        val recordType = value::class.java
        writeClass(recordType)
        for (field in recordType.relevantFields) {
            field.isAccessible = true
            val fieldName = field.name
            val fieldValue = field.get(value)
            unsupportedFieldTypeFor(field)?.let {
                reportUnsupportedFieldType(it, "serialize", fieldName, fieldValue)
            }
            withDebugFrame({ "${recordType.typeName}.$fieldName" }) {
                writePropertyValue(PropertyKind.Field, fieldName, fieldValue)
            }
        }
    }
}


private
val Class<*>.relevantFields: List<Field>
    get() = declaredFields
        .filterNot { field -> isStatic(field.modifiers) }
