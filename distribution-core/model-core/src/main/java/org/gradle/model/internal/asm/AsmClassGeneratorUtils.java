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

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

public class AsmClassGeneratorUtils {
    private static final Type BOOLEAN_TYPE = Type.getType(Boolean.class);
    private static final Type CHARACTER_TYPE = Type.getType(Character.class);
    private static final Type BYTE_TYPE = Type.getType(ByteArrayOutputStream.class);
    private static final Type SHORT_TYPE = Type.getType(Short.class);
    private static final Type INTEGER_TYPE = Type.getType(Integer.class);
    private static final Type LONG_TYPE = Type.getType(Long.class);
    private static final Type FLOAT_TYPE = Type.getType(Float.class);
    private static final Type DOUBLE_TYPE = Type.getType(Double.class);

    private static final String RETURN_PRIMITIVE_BOOLEAN = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
    private static final String RETURN_CHAR = Type.getMethodDescriptor(Type.CHAR_TYPE);
    private static final String RETURN_PRIMITIVE_BYTE = Type.getMethodDescriptor(Type.BYTE_TYPE);
    private static final String RETURN_PRIMITIVE_SHORT = Type.getMethodDescriptor(Type.SHORT_TYPE);
    private static final String RETURN_INT = Type.getMethodDescriptor(Type.INT_TYPE);
    private static final String RETURN_PRIMITIVE_LONG = Type.getMethodDescriptor(Type.LONG_TYPE);
    private static final String RETURN_PRIMITIVE_FLOAT = Type.getMethodDescriptor(Type.FLOAT_TYPE);
    private static final String RETURN_PRIMITIVE_DOUBLE = Type.getMethodDescriptor(Type.DOUBLE_TYPE);

    /**
     * Unboxes or casts the value at the top of the stack.
     */
    public static void unboxOrCast(MethodVisitor methodVisitor, Type targetType) {
        switch (targetType.getSort()) {
            case Type.BOOLEAN:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, BOOLEAN_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BOOLEAN_TYPE.getInternalName(), "booleanValue", RETURN_PRIMITIVE_BOOLEAN, false);
                return;
            case Type.CHAR:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, CHARACTER_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CHARACTER_TYPE.getInternalName(), "charValue", RETURN_CHAR, false);
                return;
            case Type.BYTE:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, BYTE_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, BYTE_TYPE.getInternalName(), "byteValue", RETURN_PRIMITIVE_BYTE, false);
                break;
            case Type.SHORT:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, SHORT_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SHORT_TYPE.getInternalName(), "shortValue", RETURN_PRIMITIVE_SHORT, false);
                break;
            case Type.INT:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, INTEGER_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, INTEGER_TYPE.getInternalName(), "intValue", RETURN_INT, false);
                return;
            case Type.LONG:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, LONG_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, LONG_TYPE.getInternalName(), "longValue", RETURN_PRIMITIVE_LONG, false);
                return;
            case Type.FLOAT:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, FLOAT_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FLOAT_TYPE.getInternalName(), "floatValue", RETURN_PRIMITIVE_FLOAT, false);
                return;
            case Type.DOUBLE:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, DOUBLE_TYPE.getInternalName());
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, DOUBLE_TYPE.getInternalName(), "doubleValue", RETURN_PRIMITIVE_DOUBLE, false);
                return;
            default:
                methodVisitor.visitTypeInsn(Opcodes.CHECKCAST, targetType.getInternalName());
        }
    }

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
                builder.append(Type.getType(cl).getDescriptor());
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
            TypeVariable<?> typeVar = (TypeVariable) type;
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
                builder.append(Type.getType(cl).getDescriptor());
            } else {
                builder.append('L');
                builder.append(cl.getName().replace('.', '/'));
            }
        } else {
            visitType(type, builder);
        }
    }
}
