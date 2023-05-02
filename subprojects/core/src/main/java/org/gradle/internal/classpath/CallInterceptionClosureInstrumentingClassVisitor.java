/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

import groovy.lang.Closure;
import org.gradle.api.NonNullApi;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.Function;

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;
import static org.objectweb.asm.Opcodes.DUP2;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;
import static org.objectweb.asm.commons.InstructionAdapter.OBJECT_TYPE;

@NonNullApi
public class CallInterceptionClosureInstrumentingClassVisitor extends ClassVisitor {
    public CallInterceptionClosureInstrumentingClassVisitor(ClassVisitor delegate) {
        super(ASM_LEVEL, delegate);
    }

    @NonNullApi
    private enum MethodInstrumentationStrategy {
        /**
         * Whenever the closure's delegate is set, we want to intercept the invocations through the new delegate's metaclass.
         */
        SET_DELEGATE("setDelegate", getMethodDescriptor(Type.VOID_TYPE, getType(Object.class)), true, mv -> {
            @NonNullApi
            class MethodVisitorScopeImpl extends MethodVisitorScope {
                public MethodVisitorScopeImpl(MethodVisitor methodVisitor) {
                    super(methodVisitor);
                }

                @Override
                public void visitCode() {
                    String descriptor = getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE);
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitMethodInsn(Opcodes.INVOKESTATIC, getType(InstrumentedGroovyMetaClassHelper.class).getInternalName(), "addInvocationHooksInClosureDispatchObject", descriptor, false);
                    mv.visitVarInsn(Opcodes.ALOAD, 0);
                    mv.visitVarInsn(Opcodes.ALOAD, 1);
                    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, getType(Closure.class).getInternalName(), "setDelegate", descriptor, false);
                    mv.visitCode();
                }
            }
            return new MethodVisitorScopeImpl(mv);
        }),
        /**
         * Intercepts the calls to the closure super constructor with the two arguments being the owner and the delegate. Adds the hooks to the metaclasses of both.
         */
        DEFAULT(null, null, false, mv -> {
            @NonNullApi
            class MethodVisitorScopeImpl extends MethodVisitorScope {
                public MethodVisitorScopeImpl(MethodVisitor methodVisitor) {
                    super(methodVisitor);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    if (owner.equals(CLOSURE_INTERNAL_NAME) &&
                        name.equals("<init>") &&
                        descriptor.equals(Type.getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, OBJECT_TYPE))
                    ) {
                        visitInsn(DUP2);
                        _INVOKESTATIC(Type.getType(InstrumentedGroovyMetaClassHelper.class), "addInvocationHooksInClosureConstructor", getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, OBJECT_TYPE));
                        _INVOKESPECIAL(Type.getType(Closure.class), "<init>", getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, OBJECT_TYPE));
                    } else {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                }
            }
            return new MethodVisitorScopeImpl(mv);
        });

        public final @Nullable String methodName;
        public final @Nullable String descriptor;
        public final boolean generateIfNotPresent;
        private final Function<MethodVisitor, MethodVisitor> addHook;

        MethodInstrumentationStrategy(@Nullable String methodName, @Nullable String descriptor, boolean generateIfNotPresent, Function<MethodVisitor, MethodVisitor> methodVisitor) {
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.generateIfNotPresent = generateIfNotPresent;
            this.addHook = methodVisitor;
        }
    }

    boolean inClosureImplementation = false;
    EnumSet<MethodInstrumentationStrategy> usedStrategies = EnumSet.noneOf(MethodInstrumentationStrategy.class);

    private static final String CLOSURE_INTERNAL_NAME = getType(Closure.class).getInternalName();

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        boolean isClosureImplementation = superName.equals(CLOSURE_INTERNAL_NAME);
        enterClass(isClosureImplementation);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, @Nullable String signature, @Nullable String[] exceptions) {
        MethodVisitor superVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!inClosureImplementation) {
            return superVisitor;
        }
        Optional<MethodInstrumentationStrategy> matchingStrategy =
            Arrays.stream(MethodInstrumentationStrategy.values()).filter(it -> name.equals(it.methodName) && descriptor.equals(it.descriptor)).findAny();
        matchingStrategy.ifPresent(usedStrategies::add);
        return matchingStrategy.orElse(MethodInstrumentationStrategy.DEFAULT).addHook.apply(superVisitor);
    }

    private void enterClass(boolean isClosureSubtype) {
        inClosureImplementation = isClosureSubtype;
        usedStrategies.clear();
    }

    @Override
    public void visitEnd() {
        if (inClosureImplementation) {
            for (MethodInstrumentationStrategy methodInstrumentationStrategy : MethodInstrumentationStrategy.values()) {
                if (methodInstrumentationStrategy.generateIfNotPresent && !usedStrategies.contains(methodInstrumentationStrategy)) {
                    assert methodInstrumentationStrategy.methodName != null;
                    assert methodInstrumentationStrategy.descriptor != null;
                    MethodVisitor visitor = visitMethod(Opcodes.ACC_PUBLIC, methodInstrumentationStrategy.methodName, methodInstrumentationStrategy.descriptor, null, null);
                    visitor.visitCode();
                    visitor.visitInsn(Opcodes.RETURN);
                    visitor.visitMaxs(3, 3);
                    visitor.visitEnd();
                }
            }
        }
        super.visitEnd();
    }
}
