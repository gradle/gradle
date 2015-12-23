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

import com.google.common.collect.ImmutableList;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public abstract class PrimitiveTypes {

    public final static List<ModelType<?>> TYPES = ImmutableList.<ModelType<?>>of(
        ModelType.of(boolean.class),
        ModelType.of(char.class),
        ModelType.of(byte.class),
        ModelType.of(short.class),
        ModelType.of(int.class),
        ModelType.of(float.class),
        ModelType.of(long.class),
        ModelType.of(double.class)
    );

    public static boolean isPrimitiveType(ModelType<?> modelType) {
        return TYPES.contains(modelType);
    }

    public static Object defaultValueOf(ModelType<?> primitiveModelType) {
        if (!isPrimitiveType(primitiveModelType)) {
            throw new IllegalArgumentException(primitiveModelType + " is not a primitive type.");
        }
        Class<?> primitiveClass = primitiveModelType.getRawClass();
        if (primitiveClass.equals(boolean.class)) {
            return false;
        }
        if (primitiveClass.equals(char.class)) {
            return '\u0000';
        }
        if (primitiveClass.equals(byte.class)) {
            return (byte) 0;
        }
        if (primitiveClass.equals(short.class)) {
            return (short) 0;
        }
        if (primitiveClass.equals(int.class)) {
            return 0;
        }
        if (primitiveClass.equals(float.class)) {
            return 0.0F;
        }
        if (primitiveClass.equals(long.class)) {
            return 0L;
        }
        if (primitiveClass.equals(double.class)) {
            return 0.0D;
        }
        throw new IllegalStateException("This should never happen, internal PrimitiveTypes must be broken, please report.");
    }

}
