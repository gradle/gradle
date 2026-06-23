/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.demos.instrumentation;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A demo ASM {@link ClassVisitor} that injects a {@code System.out.println(...)} at the start of every
 * method named {@code greeting} — a minimal, self-contained illustration of bytecode instrumentation
 * performed by {@link InstrumentClasses}. All other methods (and classes without a {@code greeting}
 * method) pass through unchanged.
 */
public class LogMethodVisitor extends ClassVisitor {

    public LogMethodVisitor(ClassVisitor classVisitor) {
        // Use the latest ASM opcode version supported by your environment
        super(Opcodes.ASM9, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        // Delegate parsing to the downstream visitor chain
        MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);

        // Intercept only our target method
        if ("greeting".equals(name)) {
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitCode() {
                    // 1. Get the static field System.out (type PrintStream)
                    mv.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");

                    // 2. Load the constant String value onto the stack
                    mv.visitLdcInsn("[ASM Injected] Here is a greeting:");

                    // 3. Invoke System.out.println(String)
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);

                    // Continue with the execution of the original method code
                    super.visitCode();
                }
            };
        }
        return mv;
    }
}
