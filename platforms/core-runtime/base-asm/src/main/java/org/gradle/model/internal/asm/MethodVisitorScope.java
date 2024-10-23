/*
 * Copyright 2022 the original author or authors.
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

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.List;

import static org.gradle.model.internal.asm.AsmClassGeneratorUtils.getWrapperTypeForPrimitiveType;
import static org.gradle.model.internal.asm.AsmConstants.ASM_LEVEL;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.GOTO;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.IFNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.POP;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Simplifies emitting bytecode to a {@link MethodVisitor} by providing a JVM bytecode DSL.
 */
@SuppressWarnings({"NewMethodNamingConvention", "SpellCheckingInspection"})
public class MethodVisitorScope extends MethodVisitor {

    private static final String BOXED_BOOLEAN_TYPE = getType(Boolean.class).getInternalName();
    private static final String BOXED_CHAR_TYPE = getType(Character.class).getInternalName();
    private static final String BOXED_BYTE_TYPE = getType(Byte.class).getInternalName();
    private static final String BOXED_SHORT_TYPE = getType(Short.class).getInternalName();
    private static final String BOXED_INT_TYPE = getType(Integer.class).getInternalName();
    private static final String BOXED_LONG_TYPE = getType(Long.class).getInternalName();
    private static final String BOXED_FLOAT_TYPE = getType(Float.class).getInternalName();
    private static final String BOXED_DOUBLE_TYPE = getType(Double.class).getInternalName();

    private static final String RETURN_PRIMITIVE_BOOLEAN = getMethodDescriptor(Type.BOOLEAN_TYPE);
    private static final String RETURN_CHAR = getMethodDescriptor(Type.CHAR_TYPE);
    private static final String RETURN_PRIMITIVE_BYTE = getMethodDescriptor(Type.BYTE_TYPE);
    private static final String RETURN_PRIMITIVE_SHORT = getMethodDescriptor(Type.SHORT_TYPE);
    private static final String RETURN_INT = getMethodDescriptor(Type.INT_TYPE);
    private static final String RETURN_PRIMITIVE_LONG = getMethodDescriptor(Type.LONG_TYPE);
    private static final String RETURN_PRIMITIVE_FLOAT = getMethodDescriptor(Type.FLOAT_TYPE);
    private static final String RETURN_PRIMITIVE_DOUBLE = getMethodDescriptor(Type.DOUBLE_TYPE);

    public MethodVisitorScope(MethodVisitor methodVisitor) {
        super(ASM_LEVEL, methodVisitor);
    }

    public void emit(BytecodeFragment bytecode) {
        bytecode.emit(mv);
    }

    /**
     * Unboxes or casts the value at the top of the stack.
     */
    public void _UNBOX(Type targetType) {
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
    public void _AUTOBOX(Class<?> valueClass, Type valueType) {
        if (valueClass.isPrimitive()) {
            // Box value
            Type boxedType = getType(getWrapperTypeForPrimitiveType(valueClass));
            _INVOKESTATIC(boxedType, "valueOf", getMethodDescriptor(boxedType, valueType));
        }
    }

    /**
     * @see org.objectweb.asm.Opcodes#F_SAME
     */
    public void _F_SAME() {
        super.visitFrame(F_SAME, 0, new Object[0], 0, new Object[0]);
    }

    public void _INVOKESPECIAL(Type owner, String name, String descriptor) {
        _INVOKESPECIAL(owner.getInternalName(), name, descriptor);
    }

    public void _INVOKESPECIAL(String owner, String name, String descriptor) {
        _INVOKESPECIAL(owner, name, descriptor, false);
    }

    public void _INVOKESPECIAL(Type owner, String name, String descriptor, boolean isInterface) {
        _INVOKESPECIAL(owner.getInternalName(), name, descriptor, isInterface);
    }

    public void _INVOKESPECIAL(String owner, String name, String descriptor, boolean isInterface) {
        super.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
    }

    public void _INVOKEINTERFACE(Type owner, String name, String descriptor) {
        _INVOKEINTERFACE(owner.getInternalName(), name, descriptor);
    }

    public void _INVOKEINTERFACE(String owner, String name, String descriptor) {
        super.visitMethodInsn(INVOKEINTERFACE, owner, name, descriptor, true);
    }

    public void _INVOKESTATIC(Type owner, String name, String descriptor) {
        _INVOKESTATIC(owner.getInternalName(), name, descriptor);
    }

    public void _INVOKESTATIC(String owner, String name, String descriptor) {
        super.visitMethodInsn(INVOKESTATIC, owner, name, descriptor, false);
    }

    public void _INVOKESTATIC(String owner, String name, String descriptor, boolean targetIsInterface) {
        super.visitMethodInsn(INVOKESTATIC, owner, name, descriptor, targetIsInterface);
    }

    public void _INVOKEVIRTUAL(Type owner, String name, String descriptor) {
        _INVOKEVIRTUAL(owner.getInternalName(), name, descriptor);
    }

    public void _INVOKEVIRTUAL(String owner, String name, String descriptor) {
        super.visitMethodInsn(INVOKEVIRTUAL, owner, name, descriptor, false);
    }

    public void _INVOKEDYNAMIC(String name, String descriptor, Handle bootstrapMethodHandle, List<?> bootstrapMethodArguments) {
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments.toArray());
    }

    public void _SWAP() {
        super.visitInsn(SWAP);
    }

    public void _POP() {
        super.visitInsn(POP);
    }

    public void _DUP() {
        super.visitInsn(DUP);
    }

    public void _ICONST_0() {
        super.visitInsn(ICONST_0);
    }

    public void _ICONST_1() {
        super.visitInsn(ICONST_1);
    }

    public void _ACONST_NULL() {
        super.visitInsn(ACONST_NULL);
    }

    public void _LDC(Object value) {
        super.visitLdcInsn(value);
    }

    public void _NEW(Type type) {
        super.visitTypeInsn(NEW, type.getInternalName());
    }

    public void _CHECKCAST(Type type) {
        _CHECKCAST(type.getInternalName());
    }

    private void _CHECKCAST(String internalName) {
        super.visitTypeInsn(CHECKCAST, internalName);
    }

    public void _INSTANCEOF(Type type) {
        super.visitTypeInsn(INSTANCEOF, type.getInternalName());
    }

    public void _ILOAD_OF(Type type, int var) {
        super.visitVarInsn(type.getOpcode(ILOAD), var);
    }

    public void _ALOAD(int var) {
        super.visitVarInsn(ALOAD, var);
    }

    public void _ASTORE(int var) {
        super.visitVarInsn(ASTORE, var);
    }

    public void _ANEWARRAY(Type type) {
        super.visitTypeInsn(ANEWARRAY, type.getInternalName());
    }

    public void _AALOAD() {
        super.visitInsn(AALOAD);
    }

    public void _AASTORE() {
        super.visitInsn(AASTORE);
    }

    public void _IFNONNULL(Label label) {
        super.visitJumpInsn(IFNONNULL, label);
    }

    public void _IFNULL(Label label) {
        super.visitJumpInsn(IFNULL, label);
    }

    public void _IFEQ(Label label) {
        super.visitJumpInsn(IFEQ, label);
    }

    public void _GOTO(Label label) {
        super.visitJumpInsn(GOTO, label);
    }

    public void _ARETURN() {
        super.visitInsn(ARETURN);
    }

    public void _IRETURN_OF(Type type) {
        super.visitInsn(type.getOpcode(IRETURN));
    }

    public void _IRETURN() {
        super.visitInsn(IRETURN);
    }

    public void _RETURN() {
        super.visitInsn(RETURN);
    }

    public void _PUTFIELD(String owner, String name, String descriptor) {
        super.visitFieldInsn(PUTFIELD, owner, name, descriptor);
    }

    public void _PUTFIELD(String owner, String name, Type fieldType) {
        _PUTFIELD(owner, name, fieldType.getDescriptor());
    }

    public void _PUTFIELD(Type owner, String name, Type fieldType) {
        _PUTFIELD(owner, name, fieldType.getDescriptor());
    }

    public void _PUTFIELD(Type owner, String name, String descriptor) {
        _PUTFIELD(owner.getInternalName(), name, descriptor);
    }

    public void _GETFIELD(String owner, String name, String descriptor) {
        super.visitFieldInsn(GETFIELD, owner, name, descriptor);
    }

    public void _GETFIELD(String owner, String name, Type fieldType) {
        _GETFIELD(owner, name, fieldType.getDescriptor());
    }

    public void _GETFIELD(Type owner, String name, Type fieldType) {
        _GETFIELD(owner, name, fieldType.getDescriptor());
    }

    public void _GETFIELD(Type owner, String name, String descriptor) {
        _GETFIELD(owner.getInternalName(), name, descriptor);
    }

    public void _PUTSTATIC(String owner, String name, String descriptor) {
        super.visitFieldInsn(PUTSTATIC, owner, name, descriptor);
    }

    public void _PUTSTATIC(Type owner, String name, Type fieldType) {
        _PUTSTATIC(owner, name, fieldType.getDescriptor());
    }

    public void _PUTSTATIC(Type owner, String name, String descriptor) {
        _PUTSTATIC(owner.getInternalName(), name, descriptor);
    }

    public void _GETSTATIC(String owner, String name, String descriptor) {
        super.visitFieldInsn(GETSTATIC, owner, name, descriptor);
    }

    public void _GETSTATIC(Type owner, String name, Type fieldType) {
        _GETSTATIC(owner, name, fieldType.getDescriptor());
    }

    public void _GETSTATIC(Type owner, String name, String descriptor) {
        _GETSTATIC(owner.getInternalName(), name, descriptor);
    }
}
