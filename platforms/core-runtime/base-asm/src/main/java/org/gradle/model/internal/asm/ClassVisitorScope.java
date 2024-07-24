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

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;

import static org.gradle.model.internal.asm.AsmConstants.ASM_LEVEL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Type.getDescriptor;

/**
 * Simplifies the usage of {@link ClassVisitor}.
 */
public class ClassVisitorScope extends ClassVisitor {

    protected ClassVisitorScope(ClassVisitor cv) {
        super(ASM_LEVEL, cv);
    }

    /**
     * Adds a field to the generated type.
     */
    protected void addField(int access, String fieldName, Class<?> type) {
        addField(access, fieldName, getDescriptor(type));
    }

    /**
     * Adds a field to the generated type.
     */
    protected void addField(int access, String fieldName, Type type) {
        addField(access, fieldName, type.getDescriptor());
    }

    /**
     * Adds a field to the generated type.
     */
    protected void addField(int access, String fieldName, String descriptor) {
        visitField(access, fieldName, descriptor, null, null);
    }

    /**
     * Adds a private synthetic method to the generated type.
     */
    protected void privateSyntheticMethod(String name, String descriptor, BytecodeFragment body) {
        addMethod(ACC_PRIVATE | ACC_SYNTHETIC, name, descriptor, null, body);
    }

    /**
     * Adds a public method to the generated type.
     */
    protected void publicMethod(String name, String descriptor, BytecodeFragment body) {
        publicMethod(name, descriptor, null, body);
    }

    /**
     * Adds a public method to the generated type.
     */
    protected void publicMethod(String name, String descriptor, String signature, BytecodeFragment body) {
        addMethod(ACC_PUBLIC, name, descriptor, signature, body);
    }

    /**
     * Adds a method to the generated type.
     */
    protected void addMethod(int access, String name, String descriptor, BytecodeFragment body) {
        addMethod(access, name, descriptor, null, body);
    }

    /**
     * Adds a method to the generated type.
     */
    private void addMethod(int access, String name, String descriptor, String signature, BytecodeFragment body) {
        MethodVisitor methodVisitor = visitMethod(access, name, descriptor, signature, null);
        body.emit(methodVisitor);
        methodVisitor.visitMaxs(0, 0);
        methodVisitor.visitEnd();
    }

    /**
     * Adds a getter that returns the value that the given code leaves on the top of the stack.
     */
    protected void addGetter(String methodName, Type returnType, String methodDescriptor, BytecodeFragment body) {
        addGetter(methodName, returnType, methodDescriptor, null, body);
    }

    /**
     * Adds a getter that returns the value that the given code leaves on the top of the stack.
     */
    protected void addGetter(String methodName, Type returnType, String methodDescriptor, @Nullable String signature, BytecodeFragment body) {
        publicMethod(methodName, methodDescriptor, signature, methodVisitor -> new MethodVisitorScope(methodVisitor) {{
            emit(body);
            _IRETURN_OF(returnType);
        }});
    }
}
