/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.serialize.codecs.stdlib

import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.Decoding
import org.gradle.internal.serialize.graph.codecs.Encoding
import org.gradle.internal.serialize.graph.codecs.EncodingProducer
import java.lang.reflect.Modifier


/**
 * Preserves the identity of Kotlin `object` singletons across the configuration cache.
 */
object KotlinObjectCodec : EncodingProducer, Decoding {

    private
    const val INSTANCE_FIELD_NAME = "INSTANCE"

    override fun encodingForType(type: Class<*>): Encoding? =
        KotlinObjectEncoding.takeIf { type.isKotlinObject() }

    override suspend fun ReadContext.decode(): Any? {
        val objectClass = readClass()
        val instanceField = objectClass.getDeclaredField(INSTANCE_FIELD_NAME).apply { isAccessible = true }
        return instanceField.get(null)
    }

    private
    fun Class<*>.isKotlinObject(): Boolean {
        if (!isAnnotationPresent(Metadata::class.java)) {
            return false
        }
        val field = declaredFields.firstOrNull { it.name == INSTANCE_FIELD_NAME } ?: return false
        return field.type == this &&
            Modifier.isStatic(field.modifiers) &&
            Modifier.isFinal(field.modifiers) &&
            Modifier.isPublic(field.modifiers)
    }
}


private
object KotlinObjectEncoding : Encoding {
    override suspend fun WriteContext.encode(value: Any) {
        writeClass(value.javaClass)
    }
}
