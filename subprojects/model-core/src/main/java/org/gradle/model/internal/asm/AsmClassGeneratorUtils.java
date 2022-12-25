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
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import static org.objectweb.asm.Type.getType;

public class AsmClassGeneratorUtils {
    /**
     * Generates the signature for the given constructor
     */
    public static String signature(Constructor<?> constructor) {
        return signature(constructor, false);
    }

    /**
     * Generates the signature for the given constructor, optionally adding a `name` parameter before all other parameters.
     */
    public static String signature(Constructor<?> constructor, boolean addNameParameter) {
        StringBuilder builder = new StringBuilder();
        visitFormalTypeParameters(builder, constructor.getTypeParameters());
        if (addNameParameter) {
            visitType(String.class, builder);
        }
        visitParameters(builder, constructor.getGenericParameterTypes());
        builder.append("V");
        visitExceptions(builder, constructor.getGenericExceptionTypes());
        return builder.toString();
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
        visitFormalTypeParameters(builder, method.getTypeParameters());
        visitParameters(builder, method.getGenericParameterTypes());
        visitType(method.getGenericReturnType(), builder);
        visitExceptions(builder, method.getGenericExceptionTypes());
        return builder.toString();
    }

    private static void visitExceptions(StringBuilder builder, java.lang.reflect.Type[] exceptionTypes) {
        for (java.lang.reflect.Type exceptionType : exceptionTypes) {
            builder.append('^');
            visitType(exceptionType, builder);
        }
    }

    private static void visitParameters(StringBuilder builder, java.lang.reflect.Type[] parameterTypes) {
        builder.append('(');
        for (java.lang.reflect.Type paramType : parameterTypes) {
            visitType(paramType, builder);
        }
        builder.append(")");
    }

    private static void visitFormalTypeParameters(StringBuilder builder, TypeVariable<?>[] typeParameters) {
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
            Class<?> cl = (Class<?>) type;
            if (cl.isPrimitive()) {
                builder.append(descriptorOf(cl));
            } else {
                if (cl.isArray()) {
                    builder.append(cl.getName().replace('.', '/'));
                } else {
                    builder.append('L');
                    builder.append(cl.getName().replace('.', '/'));
                    builder.append(';');
                }
            }
        } else if (type instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) type;
            visitNested(parameterizedType.getRawType(), builder);
            builder.append('<');
            for (java.lang.reflect.Type param : parameterizedType.getActualTypeArguments()) {
                visitType(param, builder);
            }
            builder.append(">;");
        } else if (type instanceof WildcardType) {
            WildcardType wildcardType = (WildcardType) type;
            if (wildcardType.getUpperBounds().length == 1 && wildcardType.getUpperBounds()[0].equals(Object.class)) {
                if (wildcardType.getLowerBounds().length == 0) {
                    builder.append('*');
                    return;
                }
            } else {
                for (java.lang.reflect.Type upperType : wildcardType.getUpperBounds()) {
                    builder.append('+');
                    visitType(upperType, builder);
                }
            }
            for (java.lang.reflect.Type lowerType : wildcardType.getLowerBounds()) {
                builder.append('-');
                visitType(lowerType, builder);
            }
        } else if (type instanceof TypeVariable) {
            TypeVariable<?> typeVar = (TypeVariable<?>) type;
            builder.append('T');
            builder.append(typeVar.getName());
            builder.append(';');
        } else if (type instanceof GenericArrayType) {
            GenericArrayType arrayType = (GenericArrayType) type;
            builder.append('[');
            visitType(arrayType.getGenericComponentType(), builder);
        } else {
            throw new IllegalArgumentException(String.format("Cannot generate signature for %s.", type));
        }
    }

    private static void visitNested(java.lang.reflect.Type type, StringBuilder builder) {
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

    @Nonnull
    private static String descriptorOf(Class<?> cl) {
        return getType(cl).getDescriptor();
    }
}
