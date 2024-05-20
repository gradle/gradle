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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.BuildIdentifierSerializer
import org.gradle.configurationcache.serialization.BindingsBuilder
import org.gradle.internal.serialize.BaseSerializerFactory.BIG_DECIMAL_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BIG_INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BOOLEAN_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BYTE_ARRAY_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.BYTE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.CHAR_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.DOUBLE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FILE_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.FLOAT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.INTEGER_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.LONG_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.PATH_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.SHORT_SERIALIZER
import org.gradle.internal.serialize.BaseSerializerFactory.STRING_SERIALIZER


internal
fun BindingsBuilder.baseTypes() {
    bind(STRING_SERIALIZER)
    bind(BOOLEAN_SERIALIZER)
    bind(INTEGER_SERIALIZER)
    bind(CHAR_SERIALIZER)
    bind(SHORT_SERIALIZER)
    bind(LONG_SERIALIZER)
    bind(BYTE_SERIALIZER)
    bind(FLOAT_SERIALIZER)
    bind(DOUBLE_SERIALIZER)
    bind(FILE_SERIALIZER)
    bind(PATH_SERIALIZER)
    bind(BIG_INTEGER_SERIALIZER)
    bind(BIG_DECIMAL_SERIALIZER)
    bind(ClassCodec)
    bind(MethodCodec)

    // Only serialize certain List implementations
    bind(arrayListCodec)
    bind(linkedListCodec)
    bind(copyOnWriteArrayListCodec)
    bind(ImmutableListCodec)

    // Only serialize certain Set implementations for now, as some custom types extend Set (e.g. DomainObjectContainer)
    bind(HashSetCodec)
    bind(treeSetCodec)
    bind(copyOnWriteArraySetCodec)
    bind(ImmutableSetCodec)

    // Only serialize certain Map implementations for now, as some custom types extend Map (e.g. DefaultManifest)
    bind(linkedHashMapCodec)
    bind(hashMapCodec)
    bind(treeMapCodec)
    bind(concurrentHashMapCodec)
    bind(ImmutableMapCodec)
    bind(propertiesCodec)
    bind(hashtableCodec)

    // Arrays
    bind(BYTE_ARRAY_SERIALIZER)
    bind(ShortArrayCodec)
    bind(IntArrayCodec)
    bind(LongArrayCodec)
    bind(FloatArrayCodec)
    bind(DoubleArrayCodec)
    bind(BooleanArrayCodec)
    bind(CharArrayCodec)
    bind(NonPrimitiveArrayCodec)

    // Only serialize certain Queue implementations
    bind(arrayDequeCodec)

    bind(EnumCodec)
    bind(JavaRecordCodec)
    bind(RegexpPatternCodec)
    bind(UrlCodec)
    bind(LevelCodec)
    bind(UnitCodec)
    bind(CharsetCodec)

    javaTimeTypes()

    bind(BuildIdentifierSerializer())

    bind(InputStreamCodec)
    bind(OutputStreamCodec)
}
