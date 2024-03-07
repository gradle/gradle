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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.configurationcache.problems.PropertyKind
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.beans.readPropertyValue
import org.gradle.configurationcache.serialization.beans.reportUnsupportedFieldType
import org.gradle.configurationcache.serialization.beans.unsupportedFieldTypeFor
import org.gradle.configurationcache.serialization.beans.writeNextProperty
import org.gradle.configurationcache.serialization.withDebugFrame
import java.lang.reflect.Field
import java.lang.reflect.Modifier.isStatic


object JavaRecordCodec : EncodingProducer, Decoding {

    override fun encodingForType(type: Class<*>): Encoding? =
        // need to check by name because it's only present in Java 14+
        JavaRecordEncoding.takeIf { type.superclass?.canonicalName == "java.lang.Record" }


    override suspend fun ReadContext.decode(): Any? {
        val clazz = readClass()
        val args = mutableListOf<Any?>()
        for (field in clazz.relevantFields) {
            val fieldName = field.name
            unsupportedFieldTypeFor(field)?.let {
                reportUnsupportedFieldType(it, "deserialize", fieldName)
            }
            readPropertyValue(PropertyKind.Field, fieldName) { fieldValue ->
                args.add(fieldValue)
            }
        }
        val constructor = clazz.constructors.find { it.parameterCount == args.size }
            ?: error("No suitable constructor with ${args.size} arguments found for $clazz")
        return constructor.newInstance(*args.toTypedArray())
    }
}


private
object JavaRecordEncoding : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        val clazz = value::class.java
        writeClass(clazz)
        for (field in clazz.relevantFields) {
            field.isAccessible = true
            val fieldName = field.name
            val fieldValue = field.get(value)
            unsupportedFieldTypeFor(field)?.let {
                reportUnsupportedFieldType(it, "serialize", fieldName, fieldValue)
            }
            withDebugFrame({ "${clazz.typeName}.$fieldName" }) {
                writeNextProperty(fieldName, fieldValue, PropertyKind.Field)
            }
        }
    }
}


private
val Class<*>.relevantFields: List<Field>
    get() = declaredFields
        .filterNot { field -> isStatic(field.modifiers) }
