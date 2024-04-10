/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.instrumentation.processor.codegen;

import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.TypeName;
import org.objectweb.asm.Type;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TypeUtils {

    private static final Map<Type, String> PRIMITIVE_TYPES_DEFAULT_VALUES_AS_STRING;
    static {
        Map<Type, String> map = new HashMap<>();
        map.put(Type.BYTE_TYPE, "0");
        map.put(Type.SHORT_TYPE, "0");
        map.put(Type.INT_TYPE, "0");
        map.put(Type.LONG_TYPE, "0L");
        map.put(Type.FLOAT_TYPE, "0.0f");
        map.put(Type.DOUBLE_TYPE, "0.0");
        map.put(Type.CHAR_TYPE, "'\\u0000'");
        map.put(Type.BOOLEAN_TYPE, "false");
        PRIMITIVE_TYPES_DEFAULT_VALUES_AS_STRING = Collections.unmodifiableMap(map);
    }

    public static String getDefaultValue(Type type) {
        return PRIMITIVE_TYPES_DEFAULT_VALUES_AS_STRING.getOrDefault(type, "null");
    }

    /**
     * Converts an ASM {@link Type} to a JavaPoet {@link TypeName}.
     */
    public static TypeName typeName(Type type) {
        if (type.equals(Type.VOID_TYPE)) {
            return ClassName.VOID;
        }
        if (type.equals(Type.BOOLEAN_TYPE)) {
            return ClassName.BOOLEAN;
        }
        if (type.equals(Type.CHAR_TYPE)) {
            return ClassName.CHAR;
        }
        if (type.equals(Type.BYTE_TYPE)) {
            return ClassName.BYTE;
        }
        if (type.equals(Type.SHORT_TYPE)) {
            return ClassName.SHORT;
        }
        if (type.equals(Type.INT_TYPE)) {
            return ClassName.INT;
        }
        if (type.equals(Type.FLOAT_TYPE)) {
            return ClassName.FLOAT;
        }
        if (type.equals(Type.LONG_TYPE)) {
            return ClassName.LONG;
        }
        if (type.equals(Type.DOUBLE_TYPE)) {
            return ClassName.DOUBLE;
        }
        if (type.getSort() == Type.ARRAY) {
            return ArrayTypeName.of(typeName(type.getElementType()));
        }
        return className(type);
    }

    public static ClassName className(Type type) {
        return ClassName.bestGuess(type.getClassName().replace("$", "."));
    }
}
