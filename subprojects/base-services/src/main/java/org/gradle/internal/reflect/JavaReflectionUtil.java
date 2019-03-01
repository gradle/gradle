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

package org.gradle.internal.reflect;

import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Queue;

public class JavaReflectionUtil {

    public static Class<?> getWrapperTypeForPrimitiveType(Class<?> type) {
        if (type == Character.TYPE) {
            return Character.class;
        } else if (type == Boolean.TYPE) {
            return Boolean.class;
        } else if (type == Long.TYPE) {
            return Long.class;
        } else if (type == Integer.TYPE) {
            return Integer.class;
        } else if (type == Short.TYPE) {
            return Short.class;
        } else if (type == Byte.TYPE) {
            return Byte.class;
        } else if (type == Float.TYPE) {
            return Float.class;
        } else if (type == Double.TYPE) {
            return Double.class;
        }
        throw new IllegalArgumentException(String.format("Don't know the wrapper type for primitive type %s.", type));
    }

    /**
     * This is intended to be a equivalent of deprecated {@link Class#newInstance()}.
     */
    public static <T> T newInstance(Class<T> c) {
        try {
            Constructor<T> constructor = c.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Checks if a type has a type variable which may require resolving.
     */
    public static boolean hasTypeVariable(Type type) {
        // do some checks up-front, so we avoid creating the queue in most cases
        // Cases we want to handle:
        // - List<String>
        // - Class<?>
        // - List<Class<?>>
        // - Integer[]
        // - ? extends BaseType
        // - Class<?>[]
        if (doesNotHaveTypeVariable(type)) {
            return false;
        }
        if (type instanceof TypeVariable) {
            return true;
        }
        if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            boolean noTypeVariables = true;
            for (Type actualTypeArgument : parameterizedType.getActualTypeArguments()) {
                if (actualTypeArgument instanceof TypeVariable) {
                    return true;
                }
                noTypeVariables &= doesNotHaveTypeVariable(actualTypeArgument);
            }
            if (noTypeVariables) {
                return false;
            }
        }
        if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = (GenericArrayType) type;
            if (genericArrayType.getGenericComponentType() instanceof TypeVariable) {
                return true;
            }
        }

        // Type is more complicated, need to check everything.
        Queue<Type> typesToInspect = new ArrayDeque<Type>();
        typesToInspect.add(type);
        while (!typesToInspect.isEmpty()) {
            Type typeToInspect = typesToInspect.remove();
            if (typeToInspect instanceof Class) {
                continue;
            }
            if (typeToInspect instanceof TypeVariable) {
                return true;
            }
            if (typeToInspect instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) typeToInspect;
                CollectionUtils.addAll(typesToInspect, parameterizedType.getActualTypeArguments());
            } else if (typeToInspect instanceof GenericArrayType) {
                GenericArrayType arrayType = (GenericArrayType) typeToInspect;
                typesToInspect.add(arrayType.getGenericComponentType());
            } else if (typeToInspect instanceof WildcardType) {
                WildcardType wildcardType = (WildcardType) typeToInspect;
                CollectionUtils.addAll(typesToInspect, wildcardType.getLowerBounds());
                CollectionUtils.addAll(typesToInspect, wildcardType.getUpperBounds());
            } else {
                // We don't know what the type is - let Guava take care of it.
                return true;
            }
        }
        return false;
    }

    /**
     * Quick check if a type does not have any type variables.
     *
     * Handled cases:
     * - raw Class
     * - Wildcard type with Class bounds, e.g. ? extends BaseType
     */
    private static boolean doesNotHaveTypeVariable(Type type) {
        if (type instanceof Class) {
            return true;
        }
        if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            for (Type lowerBound : wildcardType.getLowerBounds()) {
                if (!(lowerBound instanceof Class)) {
                    return false;
                }
            }
            for (Type upperBound : wildcardType.getUpperBounds()) {
                if (!(upperBound instanceof Class)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
