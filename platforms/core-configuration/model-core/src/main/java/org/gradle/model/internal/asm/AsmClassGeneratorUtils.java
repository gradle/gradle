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

package org.gradle.model.internal.asm;

import javax.annotation.Nonnull;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import static org.objectweb.asm.Type.getType;

public class AsmClassGeneratorUtils {

    /**
     * Generates the signature for the given constructor, optionally adding a `name` parameter before all other parameters.
     */
    public static String signature(Constructor<?> constructor, boolean addNameParameter) {
        StringBuilder builder = new StringBuilder();
        visitFormalTypeParameters(constructor.getTypeParameters(), builder);
        visitConstructorParameters(constructor.getGenericParameterTypes(), addNameParameter, builder);
        builder.append('V');
        visitExceptions(constructor.getGenericExceptionTypes(), builder);
        return builder.toString();
    }

    private static void visitConstructorParameters(Type[] parameterTypes, boolean addNameParameter, StringBuilder builder) {
        builder.append('(');
        if (addNameParameter) {
            visitClass(String.class, builder);
        }
        visitTypes(parameterTypes, builder);
        builder.append(')');
    }

    public static String getterSignature(java.lang.reflect.Type returnType) {
        StringBuilder builder = new StringBuilder();
        builder.append("()");
        visitType(returnType, builder);
        return builder.toString();
    }

    /**
     * Generates the signature for the given method
     */
    public static String signature(Method method) {
        StringBuilder builder = new StringBuilder();
        visitFormalTypeParameters(method.getTypeParameters(), builder);
        visitParameters(method.getGenericParameterTypes(), builder);
        visitType(method.getGenericReturnType(), builder);
        visitExceptions(method.getGenericExceptionTypes(), builder);
        return builder.toString();
    }

    private static void visitExceptions(Type[] exceptionTypes, StringBuilder builder) {
        for (java.lang.reflect.Type exceptionType : exceptionTypes) {
            builder.append('^');
            visitType(exceptionType, builder);
        }
    }

    private static void visitParameters(Type[] parameterTypes, StringBuilder builder) {
        builder.append('(');
        visitTypes(parameterTypes, builder);
        builder.append(')');
    }

    private static void visitTypes(Type[] types, StringBuilder builder) {
        for (Type type : types) {
            visitType(type, builder);
        }
    }

    private static void visitFormalTypeParameters(TypeVariable<?>[] typeParameters, StringBuilder builder) {
        if (typeParameters.length > 0) {
            builder.append('<');
            for (TypeVariable<?> typeVariable : typeParameters) {
                builder.append(typeVariable.getName());
                for (java.lang.reflect.Type bound : typeVariable.getBounds()) {
                    builder.append(':');
                    visitType(bound, builder);
                }
            }
            builder.append('>');
        }
    }

    private static void visitType(java.lang.reflect.Type type, StringBuilder builder) {
        if (type instanceof Class) {
            visitClass((Class<?>) type, builder);
        } else if (type instanceof ParameterizedType) {
            visitParameterizedType((ParameterizedType) type, builder);
        } else if (type instanceof WildcardType) {
            visitWildcardType((WildcardType) type, builder);
        } else if (type instanceof TypeVariable) {
            visitTypeVariable((TypeVariable<?>) type, builder);
        } else if (type instanceof GenericArrayType) {
            visitGenericArrayType((GenericArrayType) type, builder);
        } else {
            throw new IllegalArgumentException(String.format("Cannot generate signature for %s.", type));
        }
    }

    private static void visitGenericArrayType(GenericArrayType type, StringBuilder builder) {
        builder.append('[');
        visitType(type.getGenericComponentType(), builder);
    }

    private static void visitTypeVariable(TypeVariable<?> type, StringBuilder builder) {
        builder.append('T');
        builder.append(type.getName());
        builder.append(';');
    }

    private static void visitParameterizedType(ParameterizedType type, StringBuilder builder) {
        visitRawType(type.getRawType(), builder);
        builder.append('<');
        visitTypes(type.getActualTypeArguments(), builder);
        builder.append(">;");
    }

    private static void visitRawType(java.lang.reflect.Type type, StringBuilder builder) {
        if (type instanceof Class) {
            Class<?> cl = (Class<?>) type;
            if (cl.isPrimitive()) {
                builder.append(descriptorOf(cl));
            } else {
                builder.append('L');
                builder.append(cl.getName().replace('.', '/'));
            }
        } else {
            visitType(type, builder);
        }
    }

    private static void visitWildcardType(WildcardType type, StringBuilder builder) {
        Type[] upperBounds = type.getUpperBounds();
        if (upperBounds.length == 1 && upperBounds[0].equals(Object.class)) {
            if (type.getLowerBounds().length == 0) {
                builder.append('*');
                return;
            }
        } else {
            visitTypesWithPrefix('+', upperBounds, builder);
        }
        visitTypesWithPrefix('-', type.getLowerBounds(), builder);
    }

    private static void visitTypesWithPrefix(char prefix, Type[] types, StringBuilder builder) {
        for (Type upperType : types) {
            builder.append(prefix);
            visitType(upperType, builder);
        }
    }

    private static void visitClass(Class<?> type, StringBuilder builder) {
        if (type.isPrimitive()) {
            builder.append(descriptorOf(type));
        } else {
            String binaryName = type.getName().replace('.', '/');
            if (type.isArray()) {
                builder.append(binaryName);
            } else {
                builder.append('L');
                builder.append(binaryName);
                builder.append(';');
            }
        }
    }

    @Nonnull
    private static String descriptorOf(Class<?> cl) {
        return getType(cl).getDescriptor();
    }
}
