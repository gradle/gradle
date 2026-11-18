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

package org.gradle.internal.serialize.codecs.stdlib

import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.codecs.SelectiveCodec


object EnumCodec : SelectiveCodec {

    override fun accepts(type: Class<*>): Boolean =
        type.isEnum || type.superclass?.isEnum == true

    override suspend fun WriteContext.encode(value: Any) {
        // Enum constants with a body are instances of an anonymous subclass of the enum type
        val enumClass = value.javaClass.let { if (it.isEnum) it else it.superclass }
        writeClass(enumClass)
        writeSmallInt((value as Enum<*>).ordinal)
    }

    override suspend fun ReadContext.decode(): Any? {
        val enumClass = readClass()
        val enumOrdinal = readSmallInt()
        return enumClass.enumConstants[enumOrdinal]
    }
}
