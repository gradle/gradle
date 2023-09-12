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

package org.gradle.internal.classpath.transforms;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.NonNullApi;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.CodeSizeEvaluator;

import javax.annotation.Nullable;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.gradle.internal.classpath.transforms.CommonTypes.NO_EXCEPTIONS;
import static org.gradle.internal.classpath.transforms.CommonTypes.OBJECT_TYPE;
import static org.gradle.internal.classpath.transforms.CommonTypes.STRING_TYPE;
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
@NonNullApi
class LambdaSerializationTransformer extends ClassVisitor {
    // The method's bytecode must be less than MAX_CODE_SIZE bytes in length.
    private static final int MAX_CODE_SIZE = 65536;

    private static final Type SERIALIZED_LAMBDA_TYPE = getType(SerializedLambda.class);

    private static final String RETURN_OBJECT_FROM_SERIALIZED_LAMBDA = getMethodDescriptor(OBJECT_TYPE, SERIALIZED_LAMBDA_TYPE);
    private static final String RETURN_STRING = Type.getMethodDescriptor(CommonTypes.STRING_TYPE);
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
    public void visit(int version, int access, String name, @Nullable String signature, @Nullable String superName, @Nullable String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
        this.isInterface = (access & ACC_INTERFACE) != 0;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, @Nullable String signature, @Nullable String[] exceptions) {
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
        // There may be so many lambdas, the resulting deserialization method wouldn't fit into the JVM size limits.
        // If this happens, we split the generated method into a chain of multiple.
        // The first split is always $deserializeLambda$. It processes as many lambdas as possible,
        // and if there are too many, it calls another split method $deserializeLambda0$ as its last operation.
        // $deserializeLambda0$ may call $deserializeLambda1$ if needed, and so on.
        PeekingIterator<LambdaFactoryDetails> factoriesIterator = Iterators.peekingIterator(lambdaFactories.iterator());
        int nextSplitMethodIndex = 0;
        String currentSplitMethodName = DESERIALIZE_LAMBDA;
        do {
            String nextSplitMethodName = String.format("$deserializeLambda%d$", nextSplitMethodIndex);
            generateSplitLambdaDeserializeMethod(currentSplitMethodName, nextSplitMethodName, factoriesIterator);
            currentSplitMethodName = nextSplitMethodName;
            ++nextSplitMethodIndex;
        } while (factoriesIterator.hasNext());
    }

    private void generateSplitLambdaDeserializeMethod(String methodName, String nextSplitMethodName, PeekingIterator<LambdaFactoryDetails> factoriesIterator) {
        CodeSizeEvaluator sizeEvaluator = new CodeSizeEvaluator(visitStaticPrivateMethod(methodName, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA));
        new MethodVisitorScope(sizeEvaluator) {{
            Label next = null;
            while (factoriesIterator.hasNext()) {
                LambdaFactoryDetails factory = factoriesIterator.peek();
                Type[] argumentTypes = Type.getArgumentTypes(factory.descriptor);

                int codeSizeSoFar = sizeEvaluator.getMaxSize();
                if (codeSizeSoFar + getEstimatedSingleLambdaHandlingCodeLength(argumentTypes) + getEstimatedEpilogueLength() >= MAX_CODE_SIZE) {
                    // In theory, it is possible to have a lambda so big, its handling won't fit in a single method, and such a lambda will cause an infinite loop here.
                    // However, the number of captured lambda variables is limited by the max number of method arguments allowed by the JVM.
                    // This limit is 255 arguments as of Java 20. Deserializing that many is not a problem for the current implementation.
                    // The check is here as a future-proofing measure, in case the limit is relaxed or the generated code size grows.
                    if (codeSizeSoFar == 0) {
                        // This is the first lambda to process in this method, but it doesn't fit already - cannot proceed.
                        throw new InvalidUserCodeException(
                            "Cannot generate the deserialization method for class " + className
                                + " because lambda implementation " + factory.name + " has too many captured arguments (" + argumentTypes.length + ")");
                    }
                    break;
                }

                // Current lambda seems to fit, remove it from the sequence.
                factoriesIterator.next();

                if (next != null) {
                    visitLabel(next);
                    _F_SAME();
                }
                next = new Label();
                Handle implHandle = (Handle) factory.bootstrapMethodArguments.get(1);

                // Handling of a single lambda.
                // Let's estimate the generated bytecode size. When changing the code, don't forget to update the estimation in
                // getEstimatedSingleLambdaHandlingCodeLength.
                // * Each ALOAD_0 is 1 byte.
                // * INVOKEVIRTUAL and INVOKESTATIC are 3 bytes.
                // * INVOKEDYNAMIC is 5.
                // * LDC can be 2 or 3 bytes (in which case it is actually LDC_W, but ASM hides this from us).
                //   It depends on how big is the argument, and we have no control over this, so let's be pessimistic.
                // * IFEQ itself is 3 bytes. However, ASM's CodeSizeEvaluator gives an upper bound of 8, because offsets
                //   that don't fit in SHORT type have to be encoded differently. We're unlikely to encounter such offsets in this
                //   code, but it is better to be consistent with the CodeSizeEvaluator.
                // * _UNBOX takes 3 bytes if the target type is a reference and 6 if it is a primitive.
                // * ARETURN is 1 byte
                // * ACONST_NULL is one byte
                // Labels aren't represented in the code.

                _ALOAD(0);
                _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getImplMethodName", RETURN_STRING);
                _LDC(implHandle.getName());
                _INVOKEVIRTUAL(OBJECT_TYPE, "equals", RETURN_BOOLEAN_FROM_OBJECT);
                _IFEQ(next);

                _ALOAD(0);
                _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getImplMethodSignature", RETURN_STRING);
                _LDC(implHandle.getDesc());
                _INVOKEVIRTUAL(OBJECT_TYPE, "equals", RETURN_BOOLEAN_FROM_OBJECT);
                _IFEQ(next);
                // SerializedLambda check takes at most 36 bytes.

                // Primitive argument handling takes 13 bytes per argument, reference takes 10 bytes.
                // When changing this, update getEstimatedArgumentHandlingCodeLength.
                for (int i = 0; i < argumentTypes.length; i++) {
                    _ALOAD(0);
                    _LDC(i);
                    _INVOKEVIRTUAL(SERIALIZED_LAMBDA_TYPE, "getCapturedArg", RETURN_OBJECT_FROM_INT);
                    _UNBOX(argumentTypes[i]);
                }

                _INVOKEDYNAMIC(factory.name, factory.descriptor, factory.bootstrapMethodHandle, factory.bootstrapMethodArguments);
                _ARETURN();
                // Creating the lambda and returning it take 6 bytes more, so 42 bytes + argument handling cost in total for this lambda.
            }
            if (next != null) {
                visitLabel(next);
                _F_SAME();
            }
            // Epilogue, its estimation is in getEstimatedEpilogueLength.
            if (factoriesIterator.hasNext()) {
                // We failed to fit all remaining lambdas in this method, a new split has to be generated,
                // and this method must delegate to it.
                _ALOAD(0);
                _INVOKESTATIC(className, nextSplitMethodName, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA, isInterface);
            } else if (hasDeserializeLambda) {
                _ALOAD(0);
                _INVOKESTATIC(className, RENAMED_DESERIALIZE_LAMBDA, RETURN_OBJECT_FROM_SERIALIZED_LAMBDA, isInterface);
            } else {
                _ACONST_NULL();
            }
            _ARETURN();
            // Epilogue takes 5 bytes if we need to call the other method (next split or original), or 2 bytes to just return null.
            visitMaxs(0, 0);
            visitEnd();
        }};
    }

    int getEstimatedDeserializationPrologueLength() {
        return 0;
    }

    // Estimated length of the $deserializeLambda*$ method's epilogue that calls the renamed original $deserializeLambda$ or the next split method.
    int getEstimatedEpilogueLength() {
        return hasDeserializeLambda ? 5 : 2;
    }

    int getEstimatedSingleLambdaHandlingCodeLength(Type[] arguments) {
        // See the generateSplitLambdaDeserializeMethod to find where this number comes from.
        int nonArgumentCodeSize = 42;
        int argumentHandlingLength = 0;
        for (Type arg : arguments) {
            argumentHandlingLength += getEstimatedArgumentHandlingCodeLength(arg);
        }
        return nonArgumentCodeSize + argumentHandlingLength;
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

    @NonNullApi
    private class LambdaFactoryCallRewriter extends MethodVisitorScope {

        public LambdaFactoryCallRewriter(@Nullable MethodVisitor methodVisitor) {
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

    @NonNullApi
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
