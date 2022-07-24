/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import com.google.common.collect.ImmutableMap;
import org.gradle.model.internal.type.ModelType;

import java.util.Map;

public abstract class PrimitiveTypes {

    private static final Map<ModelType<?>, Object> TYPES_DEFAULT_VALUES = ImmutableMap.<ModelType<?>, Object>builder()
        .put(ModelType.of(boolean.class), false)
        .put(ModelType.of(char.class), '\u0000')
        .put(ModelType.of(byte.class), (byte) 0)
        .put(ModelType.of(short.class), (short) 0)
        .put(ModelType.of(int.class), 0)
        .put(ModelType.of(float.class), 0.0F)
        .put(ModelType.of(long.class), 0L)
        .put(ModelType.of(double.class), 0.0D)
        .build();

    public static boolean isPrimitiveType(ModelType<?> modelType) {
        return TYPES_DEFAULT_VALUES.containsKey(modelType);
    }

    public static Object defaultValueOf(ModelType<?> primitiveModelType) {
        Object defaultValue = TYPES_DEFAULT_VALUES.get(primitiveModelType);
        if (defaultValue == null) {
            throw new IllegalArgumentException(primitiveModelType + " is not a primitive type.");
        }
        return defaultValue;
    }

}
