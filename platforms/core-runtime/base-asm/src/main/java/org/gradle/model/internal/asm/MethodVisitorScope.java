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

package org.gradle.model.internal.asm;

import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.List;

/**
 * Simplifies emitting bytecode to a {@link MethodVisitor} by providing a JVM bytecode DSL.
 */
@SuppressWarnings({"NewMethodNamingConvention", "SpellCheckingInspection"})
public class MethodVisitorScope extends MethodVisitor {

    private static final String BOXED_BOOLEAN_TYPE = Type.getType(Boolean.class).getInternalName();
    private static final String BOXED_CHAR_TYPE = Type.getType(Character.class).getInternalName();
    private static final String BOXED_BYTE_TYPE = Type.getType(Byte.class).getInternalName();
    private static final String BOXED_SHORT_TYPE = Type.getType(Short.class).getInternalName();
    private static final String BOXED_INT_TYPE = Type.getType(Integer.class).getInternalName();
    private static final String BOXED_LONG_TYPE = Type.getType(Long.class).getInternalName();
    private static final String BOXED_FLOAT_TYPE = Type.getType(Float.class).getInternalName();
    private static final String BOXED_DOUBLE_TYPE = Type.getType(Double.class).getInternalName();

    private static final String RETURN_PRIMITIVE_BOOLEAN = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
    private static final String RETURN_CHAR = Type.getMethodDescriptor(Type.CHAR_TYPE);
    private static final String RETURN_PRIMITIVE_BYTE = Type.getMethodDescriptor(Type.BYTE_TYPE);
    private static final String RETURN_PRIMITIVE_SHORT = Type.getMethodDescriptor(Type.SHORT_TYPE);
    private static final String RETURN_INT = Type.getMethodDescriptor(Type.INT_TYPE);
    private static final String RETURN_PRIMITIVE_LONG = Type.getMethodDescriptor(Type.LONG_TYPE);
    private static final String RETURN_PRIMITIVE_FLOAT = Type.getMethodDescriptor(Type.FLOAT_TYPE);
    private static final String RETURN_PRIMITIVE_DOUBLE = Type.getMethodDescriptor(Type.DOUBLE_TYPE);

    public MethodVisitorScope(MethodVisitor methodVisitor) {
        super(AsmConstants.ASM_LEVEL, methodVisitor);
    }

    protected void emit(BytecodeFragment bytecode) {
        bytecode.emit(mv);
    }

    /**
     * Unboxes or casts the value at the top of the stack.
     */
    protected void _UNBOX(Type targetType) {
        switch (targetType.getSort()) {
            case Type.BOOLEAN:
                unbox(BOXED_BOOLEAN_TYPE, "booleanValue", RETURN_PRIMITIVE_BOOLEAN);
                break;
            case Type.CHAR:
                unbox(BOXED_CHAR_TYPE, "charValue", RETURN_CHAR);
                break;
            case Type.BYTE:
                unbox(BOXED_BYTE_TYPE, "byteValue", RETURN_PRIMITIVE_BYTE);
                break;
            case Type.SHORT:
                unbox(BOXED_SHORT_TYPE, "shortValue", RETURN_PRIMITIVE_SHORT);
                break;
            case Type.INT:
                unbox(BOXED_INT_TYPE, "intValue", RETURN_INT);
                break;
            case Type.LONG:
                unbox(BOXED_LONG_TYPE, "longValue", RETURN_PRIMITIVE_LONG);
                break;
            case Type.FLOAT:
                unbox(BOXED_FLOAT_TYPE, "floatValue", RETURN_PRIMITIVE_FLOAT);
                break;
            case Type.DOUBLE:
                unbox(BOXED_DOUBLE_TYPE, "doubleValue", RETURN_PRIMITIVE_DOUBLE);
                break;
            default:
                _CHECKCAST(targetType);
                break;
        }
    }

    private void unbox(String boxedType, String unboxMethod, String unboxMethodDescriptor) {
        _CHECKCAST(boxedType);
        _INVOKEVIRTUAL(boxedType, unboxMethod, unboxMethodDescriptor);
    }

    /**
     * Boxes the value at the top of the stack, if primitive
     */
    protected void _AUTOBOX(Class<?> valueClass, Type valueType) {
        if (valueClass.isPrimitive()) {
            // Box value
            Type boxedType = Type.getType(JavaReflectionUtil.getWrapperTypeForPrimitiveType(valueClass));
            _INVOKESTATIC(boxedType, "valueOf", Type.getMethodDescriptor(boxedType, valueType));
        }
    }

    /**
     * @see org.objectweb.asm.Opcodes#F_SAME
     */
    protected void _F_SAME() {
        super.visitFrame(Opcodes.F_SAME, 0, new Object[0], 0, new Object[0]);
    }

    protected void _INVOKESPECIAL(Type owner, String name, String descriptor) {
        _INVOKESPECIAL(owner.getInternalName(), name, descriptor);
    }

    protected void _INVOKESPECIAL(String owner, String name, String descriptor) {
        _INVOKESPECIAL(owner, name, descriptor, false);
    }

    protected void _INVOKESPECIAL(Type owner, String name, String descriptor, boolean isInterface) {
        _INVOKESPECIAL(owner.getInternalName(), name, descriptor, isInterface);
    }

    protected void _INVOKESPECIAL(String owner, String name, String descriptor, boolean isInterface) {
        super.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, descriptor, isInterface);
    }

    protected void _INVOKEINTERFACE(Type owner, String name, String descriptor) {
        _INVOKEINTERFACE(owner.getInternalName(), name, descriptor);
    }

    protected void _INVOKEINTERFACE(String owner, String name, String descriptor) {
        super.visitMethodInsn(Opcodes.INVOKEINTERFACE, owner, name, descriptor, true);
    }

    protected void _INVOKESTATIC(Type owner, String name, String descriptor) {
        _INVOKESTATIC(owner.getInternalName(), name, descriptor);
    }

    protected void _INVOKESTATIC(String owner, String name, String descriptor) {
        super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, descriptor, false);
    }

    protected void _INVOKESTATIC(String owner, String name, String descriptor, boolean targetIsInterface) {
        super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, descriptor, targetIsInterface);
    }

    protected void _INVOKEVIRTUAL(Type owner, String name, String descriptor) {
        _INVOKEVIRTUAL(owner.getInternalName(), name, descriptor);
    }

    protected void _INVOKEVIRTUAL(String owner, String name, String descriptor) {
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, descriptor, false);
    }

    protected void _INVOKEDYNAMIC(String name, String descriptor, Handle bootstrapMethodHandle, List<?> bootstrapMethodArguments) {
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments.toArray());
    }

    protected void _SWAP() {
        super.visitInsn(Opcodes.SWAP);
    }

    protected void _POP() {
        super.visitInsn(Opcodes.POP);
    }

    protected void _DUP() {
        super.visitInsn(Opcodes.DUP);
    }

    protected void _ICONST_0() {
        super.visitInsn(Opcodes.ICONST_0);
    }

    protected void _ICONST_1() {
        super.visitInsn(Opcodes.ICONST_1);
    }

    protected void _ACONST_NULL() {
        super.visitInsn(Opcodes.ACONST_NULL);
    }

    protected void _LDC(Object value) {
        super.visitLdcInsn(value);
    }

    protected void _NEW(Type type) {
        super.visitTypeInsn(Opcodes.NEW, type.getInternalName());
    }

    protected void _CHECKCAST(Type type) {
        _CHECKCAST(type.getInternalName());
    }

    private void _CHECKCAST(String internalName) {
        super.visitTypeInsn(Opcodes.CHECKCAST, internalName);
    }

    protected void _INSTANCEOF(Type type) {
        super.visitTypeInsn(Opcodes.INSTANCEOF, type.getInternalName());
    }

    protected void _ILOAD_OF(Type type, int var) {
        super.visitVarInsn(type.getOpcode(Opcodes.ILOAD), var);
    }

    protected void _ALOAD(int var) {
        super.visitVarInsn(Opcodes.ALOAD, var);
    }

    protected void _ASTORE(int var) {
        super.visitVarInsn(Opcodes.ASTORE, var);
    }

    protected void _ANEWARRAY(Type type) {
        super.visitTypeInsn(Opcodes.ANEWARRAY, type.getInternalName());
    }

    protected void _AALOAD() {
        super.visitInsn(Opcodes.AALOAD);
    }

    protected void _AASTORE() {
        super.visitInsn(Opcodes.AASTORE);
    }

    protected void _IFNONNULL(Label label) {
        super.visitJumpInsn(Opcodes.IFNONNULL, label);
    }

    protected void _IFNULL(Label label) {
        super.visitJumpInsn(Opcodes.IFNULL, label);
    }

    protected void _IFEQ(Label label) {
        super.visitJumpInsn(Opcodes.IFEQ, label);
    }

    protected void _GOTO(Label label) {
        super.visitJumpInsn(Opcodes.GOTO, label);
    }

    protected void _ARETURN() {
        super.visitInsn(Opcodes.ARETURN);
    }

    protected void _IRETURN_OF(Type type) {
        super.visitInsn(type.getOpcode(Opcodes.IRETURN));
    }

    protected void _IRETURN() {
        super.visitInsn(Opcodes.IRETURN);
    }

    protected void _RETURN() {
        super.visitInsn(Opcodes.RETURN);
    }

    protected void _PUTFIELD(String owner, String name, String descriptor) {
        super.visitFieldInsn(Opcodes.PUTFIELD, owner, name, descriptor);
    }

    protected void _PUTFIELD(String owner, String name, Type fieldType) {
        _PUTFIELD(owner, name, fieldType.getDescriptor());
    }

    protected void _PUTFIELD(Type owner, String name, Type fieldType) {
        _PUTFIELD(owner, name, fieldType.getDescriptor());
    }

    protected void _PUTFIELD(Type owner, String name, String descriptor) {
        _PUTFIELD(owner.getInternalName(), name, descriptor);
    }

    protected void _GETFIELD(String owner, String name, String descriptor) {
        super.visitFieldInsn(Opcodes.GETFIELD, owner, name, descriptor);
    }

    protected void _GETFIELD(String owner, String name, Type fieldType) {
        _GETFIELD(owner, name, fieldType.getDescriptor());
    }

    protected void _GETFIELD(Type owner, String name, Type fieldType) {
        _GETFIELD(owner, name, fieldType.getDescriptor());
    }

    protected void _GETFIELD(Type owner, String name, String descriptor) {
        _GETFIELD(owner.getInternalName(), name, descriptor);
    }

    protected void _PUTSTATIC(String owner, String name, String descriptor) {
        super.visitFieldInsn(Opcodes.PUTSTATIC, owner, name, descriptor);
    }

    protected void _PUTSTATIC(Type owner, String name, Type fieldType) {
        _PUTSTATIC(owner, name, fieldType.getDescriptor());
    }

    protected void _PUTSTATIC(Type owner, String name, String descriptor) {
        _PUTSTATIC(owner.getInternalName(), name, descriptor);
    }

    protected void _GETSTATIC(String owner, String name, String descriptor) {
        super.visitFieldInsn(Opcodes.GETSTATIC, owner, name, descriptor);
    }

    protected void _GETSTATIC(Type owner, String name, Type fieldType) {
        _GETSTATIC(owner, name, fieldType.getDescriptor());
    }

    protected void _GETSTATIC(Type owner, String name, String descriptor) {
        _GETSTATIC(owner.getInternalName(), name, descriptor);
    }
}
