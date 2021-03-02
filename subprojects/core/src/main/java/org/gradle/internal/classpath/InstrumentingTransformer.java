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
import org.gradle.api.specs.Spec;
import org.gradle.internal.Pair;
import org.gradle.internal.hash.Hasher;
import org.gradle.model.internal.asm.AsmClassGeneratorUtils;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
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

import static org.gradle.internal.classanalysis.AsmConstants.ASM_LEVEL;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.F_SAME;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.IFEQ;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getObjectType;
import static org.objectweb.asm.Type.getType;

class InstrumentingTransformer implements CachedClasspathTransformer.Transform {

    /**
     * Decoration format. Increment this when making changes.
     */
    private static final int DECORATION_FORMAT = 16;

    private static final Type SYSTEM_TYPE = getType(System.class);
    private static final Type STRING_TYPE = getType(String.class);
    private static final Type INTEGER_TYPE = getType(Integer.class);
    private static final Type INSTRUMENTED_TYPE = getType(Instrumented.class);
    private static final Type OBJECT_TYPE = getType(Object.class);
    private static final Type SERIALIZED_LAMBDA_TYPE = getType(SerializedLambda.class);
    private static final Type LONG_TYPE = getType(Long.class);
    private static final Type BOOLEAN_TYPE = getType(Boolean.class);

    private static final String RETURN_STRING = getMethodDescriptor(STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING = getMethodDescriptor(STRING_TYPE, STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING_STRING = getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_STRING_FROM_STRING_STRING_STRING = getMethodDescriptor(STRING_TYPE, STRING_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INT = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, Type.INT_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INTEGER = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, INTEGER_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INT_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, Type.INT_TYPE, STRING_TYPE);
    private static final String RETURN_INTEGER_FROM_STRING_INTEGER_STRING = getMethodDescriptor(INTEGER_TYPE, STRING_TYPE, INTEGER_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_PRIMITIVE_LONG = getMethodDescriptor(LONG_TYPE, STRING_TYPE, Type.LONG_TYPE);
    private static final String RETURN_LONG_FROM_STRING_LONG = getMethodDescriptor(LONG_TYPE, STRING_TYPE, LONG_TYPE);
    private static final String RETURN_LONG_FROM_STRING_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_PRIMITIVE_LONG_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE, Type.LONG_TYPE, STRING_TYPE);
    private static final String RETURN_LONG_FROM_STRING_LONG_STRING = getMethodDescriptor(LONG_TYPE, STRING_TYPE, LONG_TYPE, STRING_TYPE);
    private static final String RETURN_PRIMITIVE_BOOLEAN_FROM_STRING = getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE);
    private static final String RETURN_PRIMITIVE_BOOLEAN_FROM_STRING_STRING = getMethodDescriptor(Type.BOOLEAN_TYPE, STRING_TYPE, STRING_TYPE);
    private static final String RETURN_OBJECT_FROM_INT = getMethodDescriptor(OBJECT_TYPE, Type.INT_TYPE);
    private static final String RETURN_BOOLEAN_FROM_OBJECT = getMethodDescriptor(Type.BOOLEAN_TYPE, OBJECT_TYPE);
    private static final String RETURN_PROPERTIES = getMethodDescriptor(getType(Properties.class));
    private static final String RETURN_PROPERTIES_FROM_STRING = getMethodDescriptor(getType(Properties.class), STRING_TYPE);
    private static final String RETURN_CALL_SITE_ARRAY = getMethodDescriptor(getType(CallSiteArray.class));
    private static final String RETURN_VOID_FROM_CALL_SITE_ARRAY = getMethodDescriptor(Type.VOID_TYPE, getType(CallSiteArray.class));
    private static final String RETURN_OBJECT_FROM_SERIALIZED_LAMBDA = getMethodDescriptor(OBJECT_TYPE, SERIALIZED_LAMBDA_TYPE);

    private static final String LAMBDA_METAFACTORY_TYPE = getType(LambdaMetafactory.class).getInternalName();
    private static final String LAMBDA_METAFACTORY_METHOD_DESCRIPTOR = getMethodDescriptor(getType(CallSite.class), getType(MethodHandles.Lookup.class), STRING_TYPE, getType(MethodType.class), getType(Object[].class));

    private static final String INSTRUMENTED_CALL_SITE_METHOD = "$instrumentedCallSiteArray";
    private static final String CREATE_CALL_SITE_ARRAY_METHOD = "$createCallSiteArray";
    private static final String DESERIALIZE_LAMBDA = "$deserializeLambda$";
    private static final String RENAMED_DESERIALIZE_LAMBDA = "$renamedDeserializeLambda$";

    private static final String[] NO_EXCEPTIONS = new String[0];

    @Override
    public void applyConfigurationTo(Hasher hasher) {
        hasher.putString(InstrumentingTransformer.class.getSimpleName());
        hasher.putInt(DECORATION_FORMAT);
    }

    @Override
    public Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor) {
        return Pair.of(entry.getPath(), new InstrumentingVisitor(new InstrumentingBackwardsCompatibilityVisitor(visitor)));
    }

    private static class InstrumentingVisitor extends ClassVisitor {
        String className;
        private final List<LambdaFactoryDetails> lambdaFactories = new ArrayList<>();
        private boolean hasGroovyCallSites;
        private boolean hasDeserializeLambda;
        private boolean isInterface;

        public InstrumentingVisitor(ClassVisitor visitor) {
            super(ASM_LEVEL, visitor);
        }

        public void addSerializedLambda(LambdaFactoryDetails lambdaFactoryDetails) {
            lambdaFactories.add(lambdaFactoryDetails);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
            this.isInterface = (access & ACC_INTERFACE) != 0;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                hasGroovyCallSites = true;
            } else if (name.equals(DESERIALIZE_LAMBDA) && descriptor.equals(RETURN_OBJECT_FROM_SERIALIZED_LAMBDA)) {
                hasDeserializeLambda = true;
                return super.visitMethod(access, RENAMED_DESERIALIZE_LAMBDA, descriptor, signature, exceptions);
            }
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new InstrumentingMethodVisitor(this, methodVisitor);
        }

        @Override
        public void visitEnd() {
            if (hasGroovyCallSites) {
                generateCallSiteFactoryMethod();
            }
            if (!lambdaFactories.isEmpty() || hasDeserializeLambda) {
                generateLambdaDeserializeMethod();
            }
            super.visitEnd();
        }

        private void generateLambdaDeserializeMethod() {
            new MethodVisitorScope(
                visitStaticPrivateMethod(DESERIALIZE_LAMBDA, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA)
            ) {
                {
                    visitCode();
                    Label next = null;
                    for (LambdaFactoryDetails factory : lambdaFactories) {
                        if (next != null) {
                            visitLabel(next);
                            _F_SAME();
                        }
                        next = new Label();
                        _ALOAD(0);
                        _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getImplMethodName", RETURN_STRING);
                        _LDC(((Handle) factory.bootstrapMethodArguments.get(1)).getName());
                        _INVOKEVIRTUAL(OBJECT_TYPE, "equals", RETURN_BOOLEAN_FROM_OBJECT);
                        _IFEQ(next);
                        Type[] argumentTypes = Type.getArgumentTypes(factory.descriptor);
                        for (int i = 0; i < argumentTypes.length; i++) {
                            _ALOAD(0);
                            _LDC(i);
                            _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getCapturedArg", RETURN_OBJECT_FROM_INT);
                            unboxOrCastTo(argumentTypes[i]);
                        }
                        _INVOKEDYNAMIC(factory.name, factory.descriptor, factory.bootstrapMethodHandle, factory.bootstrapMethodArguments);
                        _ARETURN();
                    }
                    if (next != null) {
                        visitLabel(next);
                        _F_SAME();
                    }
                    if (hasDeserializeLambda) {
                        _ALOAD(0);
                        _INVOKESTATIC(className, RENAMED_DESERIALIZE_LAMBDA, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA, isInterface);
                    } else {
                        _ACONST_NULL();
                    }
                    _ARETURN();
                    visitMaxs(0, 0);
                    visitEnd();
                }
            };
        }

        private void generateCallSiteFactoryMethod() {
            new MethodVisitorScope(
                visitStaticPrivateMethod(INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY)
            ) {
                {
                    visitCode();
                    _INVOKESTATIC(className, CREATE_CALL_SITE_ARRAY_METHOD, RETURN_CALL_SITE_ARRAY);
                    _DUP();
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "groovyCallSites", RETURN_VOID_FROM_CALL_SITE_ARRAY);
                    _ARETURN();
                    visitMaxs(2, 0);
                    visitEnd();
                }
            };
        }

        private MethodVisitor visitStaticPrivateMethod(String name, String descriptor) {
            return super.visitMethod(
                ACC_STATIC | ACC_SYNTHETIC | ACC_PRIVATE,
                name,
                descriptor,
                null,
                NO_EXCEPTIONS
            );
        }
    }

    private static class InstrumentingMethodVisitor extends MethodVisitorScope {
        private final InstrumentingVisitor owner;
        private final String className;

        public InstrumentingMethodVisitor(InstrumentingVisitor owner, MethodVisitor methodVisitor) {
            super(methodVisitor);
            this.owner = owner;
            this.className = owner.className;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (opcode == INVOKESTATIC && visitINVOKESTATIC(owner, name, descriptor)) {
                return;
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        private boolean visitINVOKESTATIC(String owner, String name, String descriptor) {
            // TODO - load the class literal instead of class name to pass to the methods on Instrumented
            if (owner.equals(SYSTEM_TYPE.getInternalName())) {
                if (name.equals("getProperty")) {
                    if (descriptor.equals(RETURN_STRING_FROM_STRING)) {
                        _LDC(binaryClassNameOf(className));
                        _INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperty", RETURN_STRING_FROM_STRING_STRING);
                        return true;
                    }
                    if (descriptor.equals(RETURN_STRING_FROM_STRING_STRING)) {
                        _LDC(binaryClassNameOf(className));
                        _INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperty", RETURN_STRING_FROM_STRING_STRING_STRING);
                        return true;
                    }
                } else if (name.equals("getProperties") && descriptor.equals(RETURN_PROPERTIES)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "systemProperties", RETURN_PROPERTIES_FROM_STRING);
                    return true;
                }
            } else if (owner.equals(INTEGER_TYPE.getInternalName()) && name.equals("getInteger")) {
                if (descriptor.equals(RETURN_INTEGER_FROM_STRING)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INT)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_INT_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_INTEGER_FROM_STRING_INTEGER)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getInteger", RETURN_INTEGER_FROM_STRING_INTEGER_STRING);
                    return true;
                }
            } else if (owner.equals(LONG_TYPE.getInternalName()) && name.equals("getLong")) {
                if (descriptor.equals(RETURN_LONG_FROM_STRING)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_LONG_FROM_STRING_PRIMITIVE_LONG)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_PRIMITIVE_LONG_STRING);
                    return true;
                }
                if (descriptor.equals(RETURN_LONG_FROM_STRING_LONG)) {
                    _LDC(binaryClassNameOf(className));
                    _INVOKESTATIC(INSTRUMENTED_TYPE, "getLong", RETURN_LONG_FROM_STRING_LONG_STRING);
                    return true;
                }
            } else if (owner.equals(BOOLEAN_TYPE.getInternalName()) && name.equals("getBoolean") && descriptor.equals(RETURN_PRIMITIVE_BOOLEAN_FROM_STRING)) {
                _LDC(binaryClassNameOf(className));
                _INVOKESTATIC(INSTRUMENTED_TYPE, "getBoolean", RETURN_PRIMITIVE_BOOLEAN_FROM_STRING_STRING);
                return true;
            } else if (owner.equals(className) && name.equals(CREATE_CALL_SITE_ARRAY_METHOD) && descriptor.equals(RETURN_CALL_SITE_ARRAY)) {
                _INVOKESTATIC(className, INSTRUMENTED_CALL_SITE_METHOD, RETURN_CALL_SITE_ARRAY);
                return true;
            }
            return false;
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (isGradleLambdaDescriptor(descriptor) && bootstrapMethodHandle.getOwner().equals(LAMBDA_METAFACTORY_TYPE) && bootstrapMethodHandle.getName().equals("metafactory")) {
                Handle altMethod = new Handle(
                    H_INVOKESTATIC,
                    LAMBDA_METAFACTORY_TYPE,
                    "altMetafactory",
                    LAMBDA_METAFACTORY_METHOD_DESCRIPTOR,
                    false
                );
                List<Object> args = new ArrayList<>(bootstrapMethodArguments.length + 1);
                Collections.addAll(args, bootstrapMethodArguments);
                args.add(LambdaMetafactory.FLAG_SERIALIZABLE);
                super.visitInvokeDynamicInsn(name, descriptor, altMethod, args.toArray());
                owner.addSerializedLambda(new LambdaFactoryDetails(name, descriptor, altMethod, args));
            } else {
                super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
            }
        }

        private boolean isGradleLambdaDescriptor(String descriptor) {
            return descriptor.endsWith(ACTION_LAMBDA_SUFFIX)
                || descriptor.endsWith(SPEC_LAMBDA_SUFFIX);
        }

        private String binaryClassNameOf(String className) {
            return getObjectType(className).getClassName();
        }

        private static final String ACTION_LAMBDA_SUFFIX = ")" + getType(Action.class).getDescriptor();
        private static final String SPEC_LAMBDA_SUFFIX = ")" + getType(Spec.class).getDescriptor();
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

    /**
     * Simplifies emitting bytecode to a {@link MethodVisitor} by providing a JVM bytecode DSL.
     */
    @SuppressWarnings("NewMethodNamingConvention")
    private static class MethodVisitorScope extends MethodVisitor {

        public MethodVisitorScope(MethodVisitor methodVisitor) {
            super(ASM_LEVEL, methodVisitor);
        }

        protected void unboxOrCastTo(Type targetType) {
            AsmClassGeneratorUtils.unboxOrCast(this, targetType);
        }

        /**
         * @see org.objectweb.asm.Opcodes#F_SAME
         */
        protected void _F_SAME() {
            super.visitFrame(F_SAME, 0, new Object[0], 0, new Object[0]);
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

        protected void _DUP() {
            super.visitInsn(DUP);
        }

        protected void _ACONST_NULL() {
            super.visitInsn(ACONST_NULL);
        }

        protected void _LDC(Object value) {
            super.visitLdcInsn(value);
        }

        protected void _ALOAD(int var) {
            super.visitVarInsn(ALOAD, var);
        }

        protected void _IFEQ(Label label) {
            super.visitJumpInsn(IFEQ, label);
        }

        protected void _ARETURN() {
            super.visitInsn(ARETURN);
        }
    }
}
