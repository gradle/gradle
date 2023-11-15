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
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorRequest;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.function.BiFunction;

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;
import static org.objectweb.asm.commons.InstructionAdapter.OBJECT_TYPE;

/**
 * Instruments implementations of {@link Closure} in the following way:
 * <ul>
 *     <li> Adds a field {@link CallInterceptionClosureInstrumentingClassVisitor#IS_EFFECTIVELY_INSTRUMENTED_FIELD_NAME} that can be set to true to indicate
 *          that this closure is now in the scope of an instrumented call, so changing its delegate must be reflected in updating the new delegate's
 *          metaclass for call interception.
 *     <li> Overrides {@link Closure#setDelegate}, adding a call to {@link InstrumentedGroovyMetaClassHelper#addInvocationHooksInClosureDispatchObject} with
 *          the new delegate. This ensures the invariant above.
 *     <li> Adds {@link InstrumentableClosure} to the set of interfaces.
 *     <li> Renames the {@code doCall} methods to {@code doCall$original} and adds new {@code doCall methods} that surrounds the original call with
 *          {@link InstrumentedClosuresHelper#enterInstrumentedClosure} and {@link InstrumentedClosuresHelper#leaveInstrumentedClosure}, making sure that
 *          the closure is tracked and will be taken into account when a dynamically dispatched invocation happens.
 *     <li> Implements {@link InstrumentableClosure#makeEffectivelyInstrumented}, adding a call to {@link InstrumentedGroovyMetaClassHelper#addInvocationHooksToEffectivelyInstrumentClosure}.
 * </ul>
 */
@NonNullApi
public class CallInterceptionClosureInstrumentingClassVisitor extends ClassVisitor {

    private static final Type BYTECODE_INTERCEPTOR_REQUEST_TYPE = Type.getType(BytecodeInterceptorRequest.class);

    private final BytecodeInterceptorRequest interceptorRequest;

    public CallInterceptionClosureInstrumentingClassVisitor(ClassVisitor delegate, BytecodeInterceptorRequest interceptorRequest) {
        super(ASM_LEVEL, delegate);
        this.interceptorRequest = interceptorRequest;
    }

    @NonNullApi
    private enum MethodInstrumentationStrategy {
        /**
         * Whenever the closure's delegate is set, we want to make sure that the call interception hooks are added to the new delegate's metaclass.
         */
        SET_DELEGATE("setDelegate", getMethodDescriptor(Type.VOID_TYPE, getType(Object.class)), true, (classData, mv) -> {
            @NonNullApi
            class MethodVisitorScopeImpl extends MethodVisitorScope {
                public MethodVisitorScopeImpl(MethodVisitor methodVisitor) {
                    super(methodVisitor);
                }

                @Override
                public void visitCode() {
                    /*
                     * // The boolean is passed to this call rather than being checked here at the call site in order to simplify code generation.
                     * InstrumentedGroovyMetaClassHelper.addInvocationHooksInClosureDispatchObject(newDelegate, isEffectivelyInstrumented, interceptorsRequest);
                     * super.setDelegate(newDelegate);
                     */

                    _ALOAD(1);
                    _ALOAD(0);
                    _GETFIELD(classData.className, IS_EFFECTIVELY_INSTRUMENTED_FIELD_NAME, "Z");
                    _GETSTATIC(BYTECODE_INTERCEPTOR_REQUEST_TYPE, classData.interceptorRequest.name(), BYTECODE_INTERCEPTOR_REQUEST_TYPE.getDescriptor());
                    String descriptor = getMethodDescriptor(Type.VOID_TYPE, OBJECT_TYPE, BOOLEAN_TYPE, BYTECODE_INTERCEPTOR_REQUEST_TYPE);
                    _INVOKESTATIC(getType(InstrumentedGroovyMetaClassHelper.class).getInternalName(), "addInvocationHooksInClosureDispatchObject", descriptor, false);

                    _ALOAD(0);
                    _ALOAD(1);
                    _INVOKESPECIAL(getType(Closure.class).getInternalName(), "setDelegate", "(Ljava/lang/Object;)V", false);

                    mv.visitCode();
                }
            }
            return new MethodVisitorScopeImpl(classData.visitor.visitMethod(mv.access, mv.name, mv.descriptor, mv.signature, mv.exceptions));
        }),
        /**
         * Renames the Closure's original `doCall` method and adds a wrapper method that invokes the original one.
         */
        RENAME_ORIGINAL_DO_CALL("doCall", null, false, (clazz, methodData) -> {
            boolean isDoCallMethod = methodData.name.equals("doCall");
            String methodNameToVisit = isDoCallMethod ? "doCall$original" : methodData.name;
            MethodVisitor original = clazz.visitor.visitMethod(methodData.access, methodNameToVisit, methodData.descriptor, methodData.signature, methodData.exceptions);
            if (isDoCallMethod) {
                @NonNullApi
                class MethodVisitorScopeImpl extends MethodVisitorScope {
                    public MethodVisitorScopeImpl(MethodVisitor methodVisitor) {
                        super(methodVisitor);
                    }

                    @Override
                    public void visitCode() {
                        /*
                         * enterInstrumentedClosure(this);
                         * try {
                         *     return doCall$original(<args>);
                         * } finally { // similar to what javac produces, this block is inlined at the normal return and catch+rethrow exit points;
                         *     leaveInstrumentedClosure(this);
                         * }
                         */
                        _ALOAD(0);
                        String enterLeaveDescriptor = Type.getMethodDescriptor(Type.VOID_TYPE, getType(InstrumentableClosure.class));
                        _INVOKESTATIC(Type.getType(InstrumentedClosuresHelper.class).getInternalName(), "enterInstrumentedClosure", enterLeaveDescriptor, false);

                        Label tryBlockStart = new Label();
                        Label tryBlockEnd = new Label();
                        Label catchBlockStart = new Label();

                        mv.visitTryCatchBlock(tryBlockStart, tryBlockEnd, catchBlockStart, "java/lang/Throwable");

                        mv.visitLabel(tryBlockStart);
                        _ALOAD(0); // receiver reference to this
                        // Invoke the original method:
                        Type[] argumentTypes = Type.getArgumentTypes(methodData.descriptor);
                        for (int argIndex = 1; argIndex <= argumentTypes.length; ++argIndex) {
                            visitVarInsn(argumentTypes[argIndex - 1].getOpcode(Opcodes.ILOAD), argIndex);
                        }
                        _INVOKESPECIAL(clazz.className, methodNameToVisit, methodData.descriptor, false);
                        mv.visitLabel(tryBlockEnd);

                        // finally block inlined before normal return:
                        _ALOAD(0);
                        _INVOKESTATIC(Type.getType(InstrumentedClosuresHelper.class).getInternalName(), "leaveInstrumentedClosure", enterLeaveDescriptor, false);
                        // and return:
                        visitInsn(Type.getReturnType(methodData.descriptor).getOpcode(IRETURN));

                        // start exception handler:
                        mv.visitLabel(catchBlockStart);
                        Object[] locals = new Object[]{clazz.className.replaceAll("\\.", "/")};
                        // Must use an F_NEW frame, as we may encounter class versions <= V1_5, see ASM MethodWriter
                        mv.visitFrame(Opcodes.F_NEW, 1, locals, 1, new Object[]{"java/lang/Throwable"});

                        // finally block inlined before rethrowing a caught exception:
                        _ALOAD(0);
                        _INVOKESTATIC(Type.getType(InstrumentedClosuresHelper.class).getInternalName(), "leaveInstrumentedClosure", enterLeaveDescriptor, false);
                        // and rethrow:
                        mv.visitInsn(Opcodes.ATHROW);

                        visitMaxs(argumentTypes.length * 2 + 1, argumentTypes.length + 1);
                    }
                }
                MethodVisitorScope bridge = new MethodVisitorScopeImpl(
                    clazz.visitor.visitMethod(methodData.access, methodData.name, methodData.descriptor, methodData.signature, methodData.exceptions)
                );
                bridge.visitCode();
                bridge.visitEnd();
            }
            return original;
        }),

        ADD_MAKE_EFFECTIVELY_INSTRUMENTED_METHOD("makeEffectivelyInstrumented", "()V", true, (classData, methodData) -> {
            @NonNullApi
            class MethodVisitorScopeImpl extends MethodVisitorScope {
                public MethodVisitorScopeImpl(MethodVisitor methodVisitor) {
                    super(methodVisitor);
                }

                @Override
                public void visitCode() {
                    /*
                     * this.isEffectivelyInstrumented = true; // from now on, setDelegate will update the delegate's metaclass
                     * addInvocationHooksToEffectivelyInstrumentedClosure(this, interceptorsRequest);
                     */

                    _ALOAD(0);
                    _DUP();

                    _ICONST_1();
                    _PUTFIELD(classData.className, IS_EFFECTIVELY_INSTRUMENTED_FIELD_NAME, "Z");

                    _GETSTATIC(BYTECODE_INTERCEPTOR_REQUEST_TYPE, classData.interceptorRequest.name(), BYTECODE_INTERCEPTOR_REQUEST_TYPE.getDescriptor());
                    String methodDescriptor = getMethodDescriptor(Type.VOID_TYPE, CLOSURE_TYPE, BYTECODE_INTERCEPTOR_REQUEST_TYPE);
                    _INVOKESTATIC(Type.getType(InstrumentedGroovyMetaClassHelper.class), "addInvocationHooksToEffectivelyInstrumentClosure", methodDescriptor);
                }
            }
            return new MethodVisitorScopeImpl(classData.visitor.visitMethod(Opcodes.ACC_PUBLIC, methodData.name, "()V", null, null));
        }),

        /**
         * Does not perform any transformations.
         */
        DEFAULT(null, null, false, (classData, mv) -> classData.visitor.visitMethod(mv.access, mv.name, mv.descriptor, mv.signature, mv.exceptions));

        public final @Nullable String methodName;
        public final @Nullable String descriptor;
        public final boolean generateIfNotPresent;
        private final BiFunction<ClassData, MethodData, MethodVisitor> methodVisitorFactory;

        @NonNullApi
        static final class MethodData {
            public final int access;
            public final String name;
            public final String descriptor;
            public final @Nullable String signature;
            public final @Nullable String[] exceptions;

            public MethodData(int access, String name, String descriptor, @Nullable String signature, @Nullable String[] exceptions) {
                this.access = access;
                this.name = name;
                this.descriptor = descriptor;
                this.signature = signature;
                this.exceptions = exceptions;
            }
        }

        @NonNullApi
        static final class ClassData {
            public final ClassVisitor visitor;
            public final String className;
            private final BytecodeInterceptorRequest interceptorRequest;

            ClassData(ClassVisitor visitor, String className, BytecodeInterceptorRequest interceptorRequest) {
                this.visitor = visitor;
                this.className = className;
                this.interceptorRequest = interceptorRequest;
            }
        }

        MethodInstrumentationStrategy(
            @Nullable String methodName,
            @Nullable String descriptor,
            boolean generateIfNotPresent,
            BiFunction<ClassData, MethodData, MethodVisitor> methodVisitorFactory
        ) {
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.generateIfNotPresent = generateIfNotPresent;
            this.methodVisitorFactory = methodVisitorFactory;
        }
    }

    boolean inClosureImplementation = false;
    String className = null;
    EnumSet<MethodInstrumentationStrategy> usedStrategies = EnumSet.noneOf(MethodInstrumentationStrategy.class);

    private static final Type CLOSURE_TYPE = getType(Closure.class);
    private static final String CLOSURE_INTERNAL_NAME = CLOSURE_TYPE.getInternalName();

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        boolean isClosureImplementation = CLOSURE_INTERNAL_NAME.equals(superName);
        enterClass(name, isClosureImplementation);
        String[] modifiedInterfaces = interfacesWithInstrumentableClosure(interfaces, isClosureImplementation);
        super.visit(version, access, name, signature, superName, modifiedInterfaces);
    }

    @Nonnull
    private static String[] interfacesWithInstrumentableClosure(String[] interfaces, boolean isClosureImplementation) {
        String[] modifiedInterfaces = isClosureImplementation ? Arrays.copyOf(interfaces, interfaces.length + 1) : interfaces;
        if (isClosureImplementation) {
            modifiedInterfaces[modifiedInterfaces.length - 1] = Type.getInternalName(InstrumentableClosure.class);
        }
        return modifiedInterfaces;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, @Nullable String signature, @Nullable String[] exceptions) {
        if (!inClosureImplementation) {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }
        Optional<MethodInstrumentationStrategy> matchingStrategy =
            Arrays.stream(MethodInstrumentationStrategy.values()).filter(it -> name.equals(it.methodName) && (it.descriptor == null || descriptor.equals(it.descriptor))).findAny();
        matchingStrategy.ifPresent(usedStrategies::add);
        MethodInstrumentationStrategy strategy = matchingStrategy.orElse(MethodInstrumentationStrategy.DEFAULT);
        return strategy.methodVisitorFactory.apply(
            new MethodInstrumentationStrategy.ClassData(cv, className, interceptorRequest),
            new MethodInstrumentationStrategy.MethodData(access, name, descriptor, signature, exceptions)
        );
    }

    private void enterClass(String className, boolean isClosureSubtype) {
        this.className = className;
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
                    visitor.visitMaxs(4, 4);
                    visitor.visitEnd();
                }
            }

            visitField(Opcodes.ACC_PRIVATE, IS_EFFECTIVELY_INSTRUMENTED_FIELD_NAME, "Z", null, null);
        }
        super.visitEnd();
    }

    private static final String IS_EFFECTIVELY_INSTRUMENTED_FIELD_NAME = "$isEffectivelyInstrumented";
}
