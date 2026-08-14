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

package org.gradle.internal.instantiation.generator;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.gradle.model.internal.asm.AsmConstants.ASM_LEVEL;

/**
 * Recognises accessors whose body does nothing but {@code return super.getX()}.
 *
 * <p>When a property is upgraded to the Provider API, a type compiled against an older Gradle keeps
 * declaring the eager accessor it replaced. The generated subclass overrides that accessor, so the
 * original body no longer runs — harmless when it only delegated to super, a silent behaviour change
 * otherwise. This tells the two apart so only the second case is reported.
 *
 * <p>Deliberately strict: anything not positively recognised is reported as non-delegating, so an
 * unreadable or unusual body produces a warning rather than silence.
 */
@NullMarked
final class SuperDelegatingAccessors {

    private static final String SCRIPT_BYTECODE_ADAPTER = "org/codehaus/groovy/runtime/ScriptBytecodeAdapter";
    private static final String KOTLIN_INTRINSICS = "kotlin/jvm/internal/Intrinsics";
    private static final String GROOVY_TYPE_TRANSFORMATION = "org/codehaus/groovy/runtime/typehandling/DefaultTypeTransformation";

    private SuperDelegatingAccessors() {
    }

    static boolean isPureSuperDelegation(Method method) {
        Class<?> owner = method.getDeclaringClass();
        ClassLoader loader = owner.getClassLoader();
        if (loader == null) {
            return false;
        }
        String resource = owner.getName().replace('.', '/') + ".class";
        try (InputStream in = loader.getResourceAsStream(resource)) {
            if (in == null) {
                return false;
            }
            BodyVisitor visitor = new BodyVisitor(method.getName(), Type.getMethodDescriptor(method));
            new ClassReader(in).accept(visitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            return visitor.isPureSuperDelegation();
        } catch (IOException | RuntimeException e) {
            // Can't read it, so can't claim the body is safe to discard
            return false;
        }
    }

    private static class BodyVisitor extends ClassVisitor {
        private final String methodName;
        private final String methodDescriptor;
        private @Nullable String superName;
        private boolean visited;
        private int superCalls;
        private boolean disqualified;

        BodyVisitor(String methodName, String methodDescriptor) {
            super(ASM_LEVEL);
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
        }

        boolean isPureSuperDelegation() {
            return visited && superCalls == 1 && !disqualified;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            this.superName = superName;
        }

        @Override
        public @Nullable MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (!name.equals(methodName) || !descriptor.equals(methodDescriptor)) {
                return null;
            }
            visited = true;
            return new InstructionVisitor();
        }

        private void disqualify() {
            disqualified = true;
        }

        private class InstructionVisitor extends MethodVisitor {
            InstructionVisitor() {
                super(ASM_LEVEL);
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                // `this` only — these accessors take no arguments
                if (opcode != Opcodes.ALOAD || var != 0) {
                    disqualify();
                }
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                if (opcode == Opcodes.INVOKESPECIAL && owner.equals(superName) && name.equals(methodName)) {
                    superCalls++;
                } else if (opcode == Opcodes.INVOKESTATIC && owner.equals(SCRIPT_BYTECODE_ADAPTER) && name.equals("invokeMethodOnSuper0")) {
                    // Groovy compiles `super.getX()` to a reflective dispatch rather than INVOKESPECIAL
                    superCalls++;
                } else if (!isValuePreservingCall(opcode, owner, name)) {
                    // Anything beyond the casting, boxing and null-assertion noise the compilers add
                    // around the super call could change the value
                    disqualify();
                }
            }

            private boolean isValuePreservingCall(int opcode, String owner, String name) {
                if (owner.equals(KOTLIN_INTRINSICS) || owner.equals(GROOVY_TYPE_TRANSFORMATION)) {
                    return true;
                }
                if (owner.equals(SCRIPT_BYTECODE_ADAPTER)) {
                    return name.equals("castToType");
                }
                if (owner.startsWith("java/lang/")) {
                    return (opcode == Opcodes.INVOKESTATIC && name.equals("valueOf"))
                        || (opcode == Opcodes.INVOKEVIRTUAL && name.endsWith("Value"));
                }
                return false;
            }

            @Override
            public void visitInsn(int opcode) {
                if (opcode < Opcodes.IRETURN || opcode > Opcodes.RETURN) {
                    disqualify();
                }
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle handle, Object... args) {
                // Groovy's indy cast is fine; an indy-dispatched super call is not something we can read
                if (!name.equals("cast")) {
                    disqualify();
                }
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                if (opcode != Opcodes.CHECKCAST) {
                    disqualify();
                }
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                disqualify();
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                disqualify();
            }

            @Override
            public void visitIincInsn(int var, int increment) {
                disqualify();
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                disqualify();
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                disqualify();
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                disqualify();
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                disqualify();
            }
        }
    }
}
