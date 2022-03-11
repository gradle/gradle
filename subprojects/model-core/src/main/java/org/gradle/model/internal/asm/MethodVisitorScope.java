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

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;
import static org.gradle.internal.reflect.JavaReflectionUtil.getWrapperTypeForPrimitiveType;
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
import static org.objectweb.asm.Opcodes.INSTANCEOF;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * Simplifies emitting bytecode to a {@link MethodVisitor} by providing a JVM bytecode DSL.
 */
@SuppressWarnings({"NewMethodNamingConvention", "SpellCheckingInspection"})
public class MethodVisitorScope extends MethodVisitor {

    public MethodVisitorScope(MethodVisitor methodVisitor) {
        super(ASM_LEVEL, methodVisitor);
    }

    protected void emit(BytecodeFragment bytecode) {
        bytecode.emit(mv);
    }

    protected void unboxOrCastTo(Type targetType) {
        AsmClassGeneratorUtils.unboxOrCast(this, targetType);
    }

    /**
     * Boxes the value at the top of the stack, if primitive
     */
    protected void maybeBox(Class<?> valueClass, Type valueType) {
        if (valueClass.isPrimitive()) {
            // Box value
            Type boxedType = getType(getWrapperTypeForPrimitiveType(valueClass));
            _INVOKESTATIC(boxedType, "valueOf", getMethodDescriptor(boxedType, valueType));
        }
    }

    /**
     * @see org.objectweb.asm.Opcodes#F_SAME
     */
    protected void _F_SAME() {
        super.visitFrame(F_SAME, 0, new Object[0], 0, new Object[0]);
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
        super.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
    }

    protected void _INVOKEINTERFACE(Type owner, String name, String descriptor) {
        _INVOKEINTERFACE(owner.getInternalName(), name, descriptor);
    }

    protected void _INVOKEINTERFACE(String owner, String name, String descriptor) {
        super.visitMethodInsn(INVOKEINTERFACE, owner, name, descriptor, true);
    }

    protected void _INVOKESTATIC(Type owner, String name, String descriptor) {
        _INVOKESTATIC(owner.getInternalName(), name, descriptor);
    }

    protected void _INVOKESTATIC(String owner, String name, String descriptor) {
        super.visitMethodInsn(INVOKESTATIC, owner, name, descriptor, false);
    }

    protected void _INVOKESTATIC(String owner, String name, String descriptor, boolean targetIsInterface) {
        super.visitMethodInsn(INVOKESTATIC, owner, name, descriptor, targetIsInterface);
    }

    protected void _INVOKEVIRTUAL(Type owner, String name, String descriptor) {
        _INVOKEVIRTUAL(owner.getInternalName(), name, descriptor);
    }

    protected void _INVOKEVIRTUAL(String owner, String name, String descriptor) {
        super.visitMethodInsn(INVOKEVIRTUAL, owner, name, descriptor, false);
    }

    protected void _INVOKEDYNAMIC(String name, String descriptor, Handle bootstrapMethodHandle, List<?> bootstrapMethodArguments) {
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments.toArray());
    }

    protected void _SWAP() {
        super.visitInsn(SWAP);
    }

    protected void _DUP() {
        super.visitInsn(DUP);
    }

    protected void _ICONST_0() {
        super.visitInsn(ICONST_0);
    }

    protected void _ICONST_1() {
        super.visitInsn(ICONST_1);
    }

    protected void _ACONST_NULL() {
        super.visitInsn(ACONST_NULL);
    }

    protected void _LDC(Object value) {
        super.visitLdcInsn(value);
    }

    protected void _NEW(Type type) {
        super.visitTypeInsn(NEW, type.getInternalName());
    }

    protected void _CHECKCAST(Type type) {
        super.visitTypeInsn(CHECKCAST, type.getInternalName());
    }

    protected void _INSTANCEOF(Type type) {
        super.visitTypeInsn(INSTANCEOF, type.getInternalName());
    }

    protected void _ALOAD(int var) {
        super.visitVarInsn(ALOAD, var);
    }

    protected void _ASTORE(int var) {
        super.visitVarInsn(ASTORE, var);
    }

    protected void _ANEWARRAY(Type type) {
        super.visitTypeInsn(ANEWARRAY, type.getInternalName());
    }

    protected void _AALOAD() {
        super.visitInsn(AALOAD);
    }

    protected void _AASTORE() {
        super.visitInsn(AASTORE);
    }

    protected void _IFNONNULL(Label label) {
        super.visitJumpInsn(IFNONNULL, label);
    }

    protected void _IFNULL(Label label) {
        super.visitJumpInsn(IFNULL, label);
    }

    protected void _IFEQ(Label label) {
        super.visitJumpInsn(IFEQ, label);
    }

    protected void _GOTO(Label label) {
        super.visitJumpInsn(GOTO, label);
    }

    protected void _ARETURN() {
        super.visitInsn(ARETURN);
    }

    protected void _IRETURN() {
        super.visitInsn(IRETURN);
    }

    protected void _RETURN() {
        super.visitInsn(RETURN);
    }

    protected void _PUTFIELD(String owner, String name, String descriptor) {
        super.visitFieldInsn(PUTFIELD, owner, name, descriptor);
    }

    protected void _PUTFIELD(Type owner, String name, Type fieldType) {
        _PUTFIELD(owner.getInternalName(), name, fieldType.getDescriptor());
    }

    protected void _GETFIELD(String owner, String name, String descriptor) {
        super.visitFieldInsn(GETFIELD, owner, name, descriptor);
    }

    protected void _GETFIELD(Type owner, String name, Type fieldType) {
        _GETFIELD(owner, name, fieldType.getDescriptor());
    }

    protected void _GETFIELD(Type owner, String name, String descriptor) {
        _GETFIELD(owner.getInternalName(), name, descriptor);
    }

    protected void _GETSTATIC(String owner, String name, String descriptor) {
        super.visitFieldInsn(GETSTATIC, owner, name, descriptor);
    }

    protected void _GETSTATIC(Type owner, String name, Type fieldType) {
        _GETSTATIC(owner, name, fieldType.getDescriptor());
    }

    protected void _GETSTATIC(Type owner, String name, String descriptor) {
        _GETSTATIC(owner.getInternalName(), name, descriptor);
    }
}
