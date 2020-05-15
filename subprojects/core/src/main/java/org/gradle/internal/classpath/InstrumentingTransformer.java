/*
 * Copyright 2020 the original author or authors.
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

import org.codehaus.groovy.runtime.callsite.CallSiteArray;
import org.gradle.api.Action;
import org.gradle.api.file.RelativePath;
import org.gradle.internal.Pair;
import org.gradle.internal.hash.Hasher;
import org.gradle.model.internal.asm.AsmClassGeneratorUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

class InstrumentingTransformer implements CachedClasspathTransformer.Transform {
    private static final Type SYSTEM_TYPE = Type.getType(System.class);
    private static final Type STRING_TYPE = Type.getType(String.class);
    private static final Type INTEGER_TYPE = Type.getType(Integer.class);
    private static final Type INSTRUMENTED_TYPE = Type.getType(Instrumented.class);
    private static final Type OBJECT_TYPE = Type.getType(Object.class);
    private static final Type SERIALIZED_LAMBDA_TYPE = Type.getType(SerializedLambda.class);
    private static final Type LONG_TYPE = Type.getType(Long.class);
    private static final Type BOOLEAN_TYPE = Type.getType(Boolean.class);

    private static final String RETURN_PRIMITIVE_BOOLEAN = Type.getMethodDescriptor(Type.BOOLEAN_TYPE);
    private static final String RETURN_INT = Type.getMethodDescriptor(Type.INT_TYPE);
    private static final String RETURN_STRING = Type.getMethodDescriptor(STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING = Type.getMethodDescriptor(STRING_TYPE, STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING_STRING = Type.getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING_STRING_STRING = Type.getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING = Type.getMethodDescriptor(INTEGER_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INT = Type.getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, Type.INT_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INTEGER = Type.getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, INTEGER_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_STRING = Type.getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INT_STRING = Type.getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, Type.INT_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INTEGER_STRING = Type.getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, INTEGER_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING = Type.getMethodDescriptor(LONG_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_PRIMITIVE_LONG = Type.getMethodDescriptor(LONG_TYPE, STRING_TYPE, Type.LONG_TYPE);
    private static final String RETURN_LONG_FROM_STRING_LONG = Type.getMethodDescriptor(LONG_TYPE, STRING_TYPE, LONG_TYPE);
    private static final String RETURN_LONG_FROM_STRING_STRING = Type.getMethodDescriptor(LONG_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_PRIMITIVE_LONG_STRING = Type.getMethodDescriptor(LONG_TYPE, STRING_TYPE, Type.LONG_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_LONG_STRING = Type.getMethodDescriptor(LONG_TYPE, STRING_TYPE, LONG_TYPE, STRING_TYPE);
    private static final String RETURN_PRIMITIVE_BOOLEAN_FROM_STRING = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE);
    private static final String RETURN_PRIMITIVE_BOOLEAN_FROM_STRING_STRING = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_OBJECT_FROM_INT = Type.getMethodDescriptor(OBJECT_TYPE, Type.INT_TYPE);
    private static final String RETURN_BOOLEAN_FROM_OBJECT = Type.getMethodDescriptor(Type.BOOLEAN_TYPE, OBJECT_TYPE);
    private static final String RETURN_PROPERTIES = Type.getMethodDescriptor(Type.getType(Properties.class));
    private static final String RETURN_PROPERTIES_FROM_STRING = Type.getMethodDescriptor(Type.getType(Properties.class), STRING_TYPE);
    private static final String RETURN_CALL_SITE_ARRAY = Type.getMethodDescriptor(Type.getType(CallSiteArray.class));
    private static final String RETURN_VOID_FROM_CALL_SITE_ARRAY = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(CallSiteArray.class));
    private static final String RETURN_OBJECT_FROM_SERIALIZED_LAMBDA = Type.getMethodDescriptor(OBJECT_TYPE, SERIALIZED_LAMBDA_TYPE);

    private static final String INSTRUMENTED_CALL_SITE_METHOD = "$instrumentedCallSiteArray";
    private static final String CREATE_CALL_SITE_ARRAY_METHOD = "$createCallSiteArray";

    private static final String[] NO_EXCEPTIONS = new String[0];

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putString(InstrumentingTransformer.class.getSimpleName());
        hasher.putInt(8); // decoration format, increment this when making changes
    }

    @Override
    public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor) {
        return Pair.of(entry.getPath(), new InstrumentingVisitor(visitor));
    }

    private static class InstrumentingVisitor extends ClassVisitor {
        String className;
        private boolean hasGroovyCallSites;
        private final List<LambdaFactoryDetails> lambdaFactories = new ArrayList<>();

        public InstrumentingVisitor(ClassVisitor visitor) {
            super(Opcodes.ASM7, visitor);
        }

        public void addSerializedLambda(LambdaFactoryDetails lambdaFactoryDetails) {
            lambdaFactories.add(lambdaFactoryDetails);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                hasGroovyCallSites = true;
            }
            return new InstrumentingMethodVisitor(this, methodVisitor);
        }

        @Override
        public void visitEnd() {
            if (hasGroovyCallSites) {
                generateCallSiteFactoryMethod();
            }
            if (!lambdaFactories.isEmpty()) {
                generateLambdaDeserializeMethod();
            }
            super.visitEnd();
        }

        private void generateLambdaDeserializeMethod() {
            MethodVisitor methodVisitor = super.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE, "$deserializeLambda$", RETURN_OBJECT_FROM_SERIALIZED_LAMBDA, null, NO_EXCEPTIONS);
            methodVisitor.visitCode();

            Label next = null;
            for (LambdaFactoryDetails factory : lambdaFactories) {
                if (next != null) {
                    methodVisitor.visitLabel(next);
                    methodVisitor.visitFrame(Opcodes.F_SAME, 0, new Object[0], 0, new Object[0]);
                }
                next = new Label();
                methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SERIALIZED_LAMBDA_TYPE.getInternalName(), "getImplMethodName", RETURN_STRING, false);
                String implementationName = ((Handle) factory.bootstrapMethodArguments.get(1)).getName();
                methodVisitor.visitLdcInsn(implementationName);
                methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OBJECT_TYPE.getInternalName(), "equals", RETURN_BOOLEAN_FROM_OBJECT, false);
                methodVisitor.visitJumpInsn(Opcodes.IFEQ, next);
                Type[] argumentTypes = Type.getArgumentTypes(factory.descriptor);
                for (int i = 0; i < argumentTypes.length; i++) {
                    Type argumentType = argumentTypes[i];
                    methodVisitor.visitVarInsn(Opcodes.ALOAD, 0);
                    methodVisitor.visitLdcInsn(i);
                    methodVisitor.visitMethodInsn(Opcodes.INVOKEVIRTUAL, SERIALIZED_LAMBDA_TYPE.getInternalName(), "getCapturedArg", RETURN_OBJECT_FROM_INT, false);
                    AsmClassGeneratorUtils.unboxOrCast(methodVisitor, argumentType);
                }
                methodVisitor.visitInvokeDynamicInsn(factory.name, factory.descriptor, factory.bootstrapMethodHandle, factory.bootstrapMethodArguments.toArray());
                methodVisitor.visitInsn(Opcodes.ARETURN);
            }
            methodVisitor.visitLabel(next);
            methodVisitor.visitFrame(Opcodes.F_SAME, 0, new Object[0], 0, new Object[0]);
            methodVisitor.visitInsn(Opcodes.ACONST_NULL);
            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }

        private void generateCallSiteFactoryMethod() {
            MethodVisitor methodVisitor = super.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_PRIVATE, INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY, null, NO_EXCEPTIONS);
            methodVisitor.visitCode();
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, className, CREATE_CALL_SITE_ARRAY_METHOD, RETURN_CALL_SITE_ARRAY, false);
            methodVisitor.visitInsn(Opcodes.DUP);
            methodVisitor.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "groovyCallSites", RETURN_VOID_FROM_CALL_SITE_ARRAY, false);
            methodVisitor.visitInsn(Opcodes.ARETURN);
            methodVisitor.visitMaxs(2, 0);
            methodVisitor.visitEnd();
        }
    }

    private static class InstrumentingMethodVisitor extends MethodVisitor {
        private final InstrumentingVisitor owner;
        private final String className;

        public InstrumentingMethodVisitor(InstrumentingVisitor owner, MethodVisitor methodVisitor) {
            super(Opcodes.ASM7, methodVisitor);
            this.owner = owner;
            this.className = owner.className;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            // TODO - load the class literal instead of class name to pass to the methods on Instrumented
            if (opcode == Opcodes.INVOKESTATIC) {
                if (owner.equals(SYSTEM_TYPE.getInternalName())) {
                    if (name.equals("getProperty")) {
                        if (descriptor.equals(RETURN_STRING_FROM_STRING)) {
                            visitLdcInsn(Type.getObjectType(className).getClassName());
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "systemProperty", RETURN_STRING_FROM_STRING_STRING, false);
                            return;
                        }
                        if (descriptor.equals(RETURN_STRING_FROM_STRING_STRING)) {
                            visitLdcInsn(Type.getObjectType(className).getClassName());
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "systemProperty", RETURN_STRING_FROM_STRING_STRING_STRING, false);
                            return;
                        }
                    } else if (name.equals("getProperties") && descriptor.equals(RETURN_PROPERTIES)) {
                        visitLdcInsn(Type.getObjectType(className).getClassName());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "systemProperties", RETURN_PROPERTIES_FROM_STRING, false);
                        return;
                    }
                } else if (owner.equals(INTEGER_TYPE.getInternalName()) && name.equals("getInteger")) {
                    if (descriptor.equals(RETURN_INTEGER_FROM_STRING)) {
                        visitLdcInsn(Type.getObjectType(className).getClassName());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "getInteger", RETURN_INTEGER_FROM_STRING_STRING, false);
                        return;
                    }
                    if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INT)) {
                        visitLdcInsn(Type.getObjectType(className).getClassName());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "getInteger", RETURN_INTEGER_FROM_STRING_INT_STRING, false);
                        return;
                    }
                    if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INTEGER)) {
                        visitLdcInsn(Type.getObjectType(className).getClassName());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "getInteger", RETURN_INTEGER_FROM_STRING_INTEGER_STRING, false);
                        return;
                    }
                } else if (owner.equals(LONG_TYPE.getInternalName()) && name.equals("getLong")) {
                    if (descriptor.equals(RETURN_LONG_FROM_STRING)) {
                        visitLdcInsn(Type.getObjectType(className).getClassName());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "getLong", RETURN_LONG_FROM_STRING_STRING, false);
                        return;
                    }
                    if (descriptor.equals(RETURN_LONG_FROM_STRING_PRIMITIVE_LONG)) {
                        visitLdcInsn(Type.getObjectType(className).getClassName());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "getLong", RETURN_LONG_FROM_STRING_PRIMITIVE_LONG_STRING, false);
                        return;
                    }
                    if (descriptor.equals(RETURN_LONG_FROM_STRING_LONG)) {
                        visitLdcInsn(Type.getObjectType(className).getClassName());
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "getLong", RETURN_LONG_FROM_STRING_LONG_STRING, false);
                        return;
                    }
                } else if (owner.equals(BOOLEAN_TYPE.getInternalName()) && name.equals("getBoolean") && descriptor.equals(RETURN_PRIMITIVE_BOOLEAN_FROM_STRING)) {
                    visitLdcInsn(Type.getObjectType(className).getClassName());
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, INSTRUMENTED_TYPE.getInternalName(), "getBoolean", RETURN_PRIMITIVE_BOOLEAN_FROM_STRING_STRING, false);
                    return;
                } else if (owner.equals(className) && name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, className, INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY, false);
                    return;
                }
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (descriptor.endsWith(")" + Type.getType(Action.class).getDescriptor()) && bootstrapMethodHandle.getOwner().equals(Type.getType(LambdaMetafactory.class).getInternalName()) && bootstrapMethodHandle.getName().equals("metafactory")) {
                Handle altMethod = new Handle(Opcodes.H_INVOKESTATIC,
                    Type.getType(LambdaMetafactory.class).getInternalName(),
                    "altMetafactory",
                    Type.getMethodDescriptor(Type.getType(CallSite.class), Type.getType(MethodHandles.Lookup.class), STRING_TYPE, Type.getType(MethodType.class), Type.getType(Object[].class)),
                    false);
                List<Object> args = new ArrayList<>(bootstrapMethodArguments.length + 1);
                Collections.addAll(args, bootstrapMethodArguments);
                args.add(LambdaMetafactory.FLAG_SERIALIZABLE);
                super.visitInvokeDynamicInsn(name, descriptor, altMethod, args.toArray());
                owner.addSerializedLambda(new LambdaFactoryDetails(name, descriptor, altMethod, args));
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }
    }

    private static class LambdaFactoryDetails {
        final String name;
        final String descriptor;
        final Handle bootstrapMethodHandle;
        final List<?> bootstrapMethodArguments;

        public LambdaFactoryDetails(String name, String descriptor, Handle bootstrapMethodHandle, List<?> bootstrapMethodArguments) {
            this.name = name;
            this.descriptor = descriptor;
            this.bootstrapMethodHandle = bootstrapMethodHandle;
            this.bootstrapMethodArguments = bootstrapMethodArguments;
        }
    }
}
