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

package org.gradle.instantexecution.serialization.codecs;

import org.gradle.instantexecution.serialization.Codec;
import org.gradle.instantexecution.serialization.CombinatorsKt;
import org.gradle.instantexecution.serialization.ReadContext;
import org.gradle.instantexecution.serialization.WriteContext;
import org.gradle.internal.UncheckedException;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumMap;

public class EnumMapCodec implements Codec<EnumMap<?, ?>> {
    @Override
    public void encode(WriteContext writeContext, EnumMap<?, ?> value) {
        Class<?> keyType;
        try {
            Field keyTypeField = EnumMap.class.getDeclaredField("keyType");
            keyTypeField.setAccessible(true);
            keyType = (Class<?>) keyTypeField.get(value);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        writeContext.write(keyType);
        CombinatorsKt.writeMap(writeContext, value);
    }

    @Override
    public EnumMap<?, ?> decode(ReadContext readContext) {
        try {
            Class<? extends Enum> keyType = (Class<? extends Enum>) readContext.read();
            EnumMap map = new EnumMap(keyType);
            int size = readContext.readSmallInt();
            for (int i = 0; i < size; i++) {
                Enum key = (Enum) readContext.read();
                Object value = readContext.read();
                map.put(key, value);
            }
            return map;
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
