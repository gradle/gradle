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

package org.gradle.internal.tools.api.impl;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.NEW;

/**
 * Adapts members selected by {@link ApiMemberSelector}, stripping out method implementations and replacing them
 * with a "stub" that will throw an exception if called at runtime.
 *
 * The exception is created by calling its default constructor.
 *
 * All members (including but not limited to stripped and stubbed methods) are delegated to a {@link ClassWriter}
 * responsible for writing new API classes.
 */
public class MethodStubbingApiMemberAdapter extends ClassVisitor {

    private final String exceptionClassName;

    public MethodStubbingApiMemberAdapter(ClassWriter cv) {
        this(cv, "java/lang/Error");
    }

    public MethodStubbingApiMemberAdapter(ClassWriter cv, String exceptionClassName) {
        super(Opcodes.ASM9, cv);
        this.exceptionClassName = exceptionClassName;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
        if ((access & ACC_ABSTRACT) == 0) {
            mv.visitCode();
            mv.visitTypeInsn(NEW, exceptionClassName);
            mv.visitInsn(DUP);
            mv.visitMethodInsn(
                INVOKESPECIAL, exceptionClassName, "<init>", "()V", false);
            mv.visitInsn(ATHROW);
            mv.visitMaxs(2, 0);
            mv.visitEnd();
        }
        return mv;
    }
}
