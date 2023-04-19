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

import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.model.internal.asm.MethodVisitorScope;
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

import static org.gradle.internal.classpath.CommonTypes.NO_EXCEPTIONS;
import static org.gradle.internal.classpath.CommonTypes.OBJECT_TYPE;
import static org.gradle.internal.classpath.CommonTypes.STRING_TYPE;
import static org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SYNTHETIC;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Type.getMethodDescriptor;
import static org.objectweb.asm.Type.getType;

/**
 * A class visitor that makes all lambdas in code serializable.
 * It adds {@link LambdaMetafactory#FLAG_SERIALIZABLE} to all bootstrap methods that create lambdas and generate synthetic deserialization method in the processed class.
 * If deserialization method is already present, it is renamed and the new implementation falls back to it.
 */
class LambdaSerializationTransformer extends ClassVisitor {
    private static final Type SERIALIZED_LAMBDA_TYPE = getType(SerializedLambda.class);

    private static final String RETURN_OBJECT_FROM_SERIALIZED_LAMBDA = getMethodDescriptor(OBJECT_TYPE, SERIALIZED_LAMBDA_TYPE);
    private static final String RETURN_STRING = getMethodDescriptor(CommonTypes.STRING_TYPE);
    private static final String RETURN_BOOLEAN_FROM_OBJECT = getMethodDescriptor(Type.BOOLEAN_TYPE, OBJECT_TYPE);
    private static final String RETURN_OBJECT_FROM_INT = getMethodDescriptor(OBJECT_TYPE, Type.INT_TYPE);

    private static final String LAMBDA_METAFACTORY_TYPE = getType(LambdaMetafactory.class).getInternalName();
    private static final String LAMBDA_METAFACTORY_METHOD_DESCRIPTOR = getMethodDescriptor(
        getType(CallSite.class),
        getType(MethodHandles.Lookup.class),
        STRING_TYPE,
        getType(MethodType.class),
        getType(Object[].class));

    private static final String DESERIALIZE_LAMBDA = "$deserializeLambda$";
    private static final String RENAMED_DESERIALIZE_LAMBDA = "$renamedDeserializeLambda$";


    private final List<LambdaFactoryDetails> lambdaFactories = new ArrayList<>();
    private String className;
    private boolean hasDeserializeLambda;
    private boolean isInterface;

    protected LambdaSerializationTransformer(ClassVisitor classVisitor) {
        super(AsmConstants.ASM_LEVEL, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
        this.isInterface = (access & ACC_INTERFACE) != 0;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals(DESERIALIZE_LAMBDA) && descriptor.equals(RETURN_OBJECT_FROM_SERIALIZED_LAMBDA)) {
            hasDeserializeLambda = true;
            return super.visitMethod(access, RENAMED_DESERIALIZE_LAMBDA, descriptor, signature, exceptions);
        }
        return new LambdaFactoryCallRewriter(super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    @Override
    public void visitEnd() {
        if (!lambdaFactories.isEmpty() || hasDeserializeLambda) {
            generateLambdaDeserializeMethod();
        }
        super.visitEnd();
    }

    private void generateLambdaDeserializeMethod() {
        new MethodVisitorScope(visitStaticPrivateMethod(DESERIALIZE_LAMBDA, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA)) {{
            Label next = null;
            for (LambdaFactoryDetails factory : lambdaFactories) {
                if (next != null) {
                    visitLabel(next);
                    _F_SAME();
                }
                next = new Label();
                Handle implHandle = (Handle) factory.bootstrapMethodArguments.get(1);

                // Handling of a single lambda.
                // Let's estimate the generated bytecode size along the way to put it into getEstimatedSingleLambdaHandlingCodeLength.
                _ALOAD(0); // bytecode size = 1, total so far 1
                _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getImplMethodName", RETURN_STRING); // +3 = 4
                _LDC(implHandle.getName()); // +3 = 7 (LDC can be encoded as 2 bytes if the reference is short, but let's not count on that.)
                _INVOKEVIRTUAL(OBJECT_TYPE, "equals", RETURN_BOOLEAN_FROM_OBJECT); // +3 = 10
                // IFEQ itself is 3 bytes. ASM may sometimes encode it in 8 if the offset is too far away.
                // This is unlikely to happen here, but better to be consistent.
                _IFEQ(next); // +8 = 18.

                _ALOAD(0); // +1 = 19
                _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getImplMethodSignature", RETURN_STRING); // +3 = 22
                _LDC(implHandle.getDesc()); // +3 = 25
                _INVOKEVIRTUAL(OBJECT_TYPE, "equals", RETURN_BOOLEAN_FROM_OBJECT); // +3 = 28
                _IFEQ(next); // +8 = 36

                // Argument handling is obviously of variable size. Let's estimate the size of the single loop iteration.
                Type[] argumentTypes = Type.getArgumentTypes(factory.descriptor);
                for (int i = 0; i < argumentTypes.length; i++) {
                    _ALOAD(0);  // 1
                    _LDC(i); // +3 = 4
                    _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getCapturedArg", RETURN_OBJECT_FROM_INT); // +3 = 7
                    _UNBOX(argumentTypes[i]); // +3 if not primitive or +6 otherwise, so 10 or 13 per argument in total.
                }
                _INVOKEDYNAMIC(factory.name, factory.descriptor, factory.bootstrapMethodHandle, factory.bootstrapMethodArguments); // +5 = 41
                _ARETURN(); // +1 = 42
            }
            if (next != null) {
                visitLabel(next);
                _F_SAME();
            }
            // Epilogue, its estimation is in getEstimatedEpilogueLength.
            if (hasDeserializeLambda) {
                _ALOAD(0);  // 1 byte
                _INVOKESTATIC(className, RENAMED_DESERIALIZE_LAMBDA, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA, isInterface); // +3 = 4
            } else {
                _ACONST_NULL(); // 1 byte
            }
            _ARETURN(); // +1, total 2 or 5 depending on whether original lambda serialization is present.
            visitMaxs(0, 0);
            visitEnd();
        }};
    }

    int getEstimatedDeserializationPrologueLength() {
        return 0;
    }

    int getEstimatedEpilogueLength() {
        return hasDeserializeLambda ? 5 : 2;
    }

    int getEstimatedSingleLambdaHandlingCodeLength(Type[] arguments) {
        int argumentHandlingLength = 0;
        for (Type arg : arguments) {
            argumentHandlingLength += getEstimatedArgumentHandlingCodeLength(arg);
        }
        return 42 + argumentHandlingLength;
    }

    private static int getEstimatedArgumentHandlingCodeLength(Type argument) {
        int loadSize = 7;  // size of SerializedLambda.getCapturedArg(<n>) call
        // unboxing of a primitive adds "invokevirtual" to "checkcast".
        int unboxingSize = isPrimitiveArgument(argument) ? 6 : 3;
        return loadSize + unboxingSize;
    }

    private static boolean isPrimitiveArgument(Type argument) {
        switch (argument.getSort()) {
            case Type.BOOLEAN:
            case Type.CHAR:
            case Type.SHORT:
            case Type.INT:
            case Type.FLOAT:
            case Type.LONG:
            case Type.DOUBLE:
                return true;
        }
        return false;
    }

    private MethodVisitor visitStaticPrivateMethod(String name, String descriptor) {
        return super.visitMethod(ACC_STATIC | ACC_SYNTHETIC | ACC_PRIVATE, name, descriptor, null, NO_EXCEPTIONS);
    }

    private void addSerializedLambda(LambdaFactoryDetails lambdaFactoryDetails) {
        lambdaFactories.add(lambdaFactoryDetails);
    }

    private class LambdaFactoryCallRewriter extends MethodVisitorScope {

        public LambdaFactoryCallRewriter(MethodVisitor methodVisitor) {
            super(methodVisitor);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            if (bootstrapMethodHandle.getOwner().equals(LAMBDA_METAFACTORY_TYPE) && bootstrapMethodHandle.getName().equals("metafactory")) {
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
                addSerializedLambda(new LambdaFactoryDetails(name, descriptor, altMethod, args));
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
