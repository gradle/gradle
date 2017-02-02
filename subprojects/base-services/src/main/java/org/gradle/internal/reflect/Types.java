/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.gradle.internal.Cast;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Queue;
import java.util.Set;

public class Types {
    private static final Collection<Class<?>> OBJECT_TYPE = ImmutableList.<Class<?>>of(Object.class);

    /**
     * Visits all types in a type hierarchy in breadth-first order, super-classes first and then implemented interfaces.
     *
     * @param clazz the type of whose type hierarchy to visit.
     * @param visitor the visitor to call for each type in the hierarchy.
     */
    public static <T> void walkTypeHierarchy(Class<T> clazz, TypeVisitor<? extends T> visitor) {
        walkTypeHierarchy(clazz, OBJECT_TYPE, visitor);
    }

    /**
     * Visits all types in a type hierarchy in breadth-first order, super-classes first and then implemented interfaces.
     *
     * @param clazz the type of whose type hierarchy to visit.
     * @param excludedTypes the types not to walk when encountered in the hierarchy.
     * @param visitor the visitor to call for each type in the hierarchy.
     */
    public static <T> void walkTypeHierarchy(Class<T> clazz, Collection<Class<?>> excludedTypes, TypeVisitor<? extends T> visitor) {
        Set<Class<?>> seenInterfaces = Sets.newHashSet();
        Queue<Class<? super T>> queue = new ArrayDeque<Class<? super T>>();
        queue.add(clazz);
        Class<? super T> type;
        while ((type = queue.poll()) != null) {
            if (excludedTypes.contains(type)) {
                continue;
            }

            visitor.visitType(type);

            Class<? super T> superclass = type.getSuperclass();
            if (superclass != null) {
                queue.add(superclass);
            }
            for (Class<?> iface : type.getInterfaces()) {
                if (seenInterfaces.add(iface)) {
                    queue.add(Cast.<Class<? super T>>uncheckedCast(iface));
                }
            }
        }
    }

    public interface TypeVisitor<T> {
        void visitType(Class<? super T> type);
    }

    /**
     * Get the generic simple name of a type.
     *
     * The generic simple name of a {@link Type} that represents a {@literal List} of {@literal String}s
     * is {@literal List&lt;String&gt;}.
     *
     * @param type The type
     * @return The generic simple name
     */
    public static String getGenericSimpleName(Type type) {
        StringBuilder builder = new StringBuilder();
        simpleGenericNameOf(type, builder);
        return builder.toString();
    }

    private static void simpleGenericNameOf(Type type, StringBuilder builder) {
        if (type instanceof Class) {
            builder.append(((Class) type).getSimpleName());
        } else if (type instanceof ParameterizedType) {
            simpleGenericNameOf((ParameterizedType) type, builder);
        } else if (type instanceof GenericArrayType) {
            simpleGenericNameOf((GenericArrayType) type, builder);
        } else if (type instanceof TypeVariable) {
            builder.append(((TypeVariable) type).getName());
        } else if (type instanceof WildcardType) {
            simpleGenericNameOf((WildcardType) type, builder);
        } else {
            throw new IllegalArgumentException("Don't know how to deal with type:" + type);
        }
    }

    private static void simpleGenericNameOf(ParameterizedType parameterizedType, StringBuilder builder) {
        simpleGenericNameOf(parameterizedType.getRawType(), builder);
        builder.append("<");
        boolean multi = false;
        for (Type typeArgument : parameterizedType.getActualTypeArguments()) {
            if (multi) {
                builder.append(", ");
            }
            simpleGenericNameOf(typeArgument, builder);
            multi = true;
        }
        builder.append(">");
    }

    private static void simpleGenericNameOf(GenericArrayType arrayType, StringBuilder builder) {
        simpleGenericNameOf(arrayType.getGenericComponentType(), builder);
        builder.append("[]");
    }

    private static void simpleGenericNameOf(WildcardType wildcardType, StringBuilder builder) {
        Type[] upperBounds = wildcardType.getUpperBounds();
        if (upperBounds.length == 1 && upperBounds[0] == Object.class) {
            builder.append('?');
        } else {
            builder.append("? extends ");
            boolean multi = false;
            for (Type typeArgument : upperBounds) {
                if (multi) {
                    builder.append(", ");
                }
                simpleGenericNameOf(typeArgument, builder);
                multi = true;
            }
        }
    }
}
