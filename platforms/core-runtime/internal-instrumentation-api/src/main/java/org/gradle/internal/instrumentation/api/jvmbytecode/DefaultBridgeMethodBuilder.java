/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.instrumentation.api.jvmbytecode;

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.H_INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.H_INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.H_NEWINVOKESPECIAL;
import static org.objectweb.asm.Type.getObjectType;

/**
 * The implementation of the bridge method builder that handles typical invocation cases and can compute the bridge method signature.
 */
public class DefaultBridgeMethodBuilder implements BridgeMethodBuilder {
    private static final Type VISITOR_CONTEXT_TYPE = Type.getType(BytecodeInterceptorFilter.class);
    private final String bridgeDesc;
    private final String interceptorOwner;
    private final String interceptorName;
    private final String interceptorDesc;

    private boolean hasKotlinDefaultMask;
    @Nullable
    private String binaryClassName;
    @Nullable
    private BytecodeInterceptorFilter context;

    /**
     * Constructor that accepts the original handle and the interceptor method.
     * The interceptor must be a static method with a specific signature.
     * <p>
     * If the original method is an instance method, then the interceptor method will get the receiver of the original as a first argument.
     * Other arguments of the original method will be passed to the interceptor in the same order.
     * <p>
     * If the original method is a static method, then the interceptor method will get all its arguments in the same order.
     *
     * @param originalTag the tag from the original method handle
     * @param originalOwner the owner of the original method handle
     * @param originalDesc the descriptor of the original method handle
     * @param interceptorOwner the owner of the interceptor method
     * @param interceptorName the name of the interceptor method
     * @param interceptorDesc the descriptor of the interceptor method
     * @see #withClassName(String)
     */
    public static DefaultBridgeMethodBuilder create(
        int originalTag,
        String originalOwner,
        String originalDesc,
        String interceptorOwner,
        String interceptorName,
        String interceptorDesc
    ) {
        String bridgeDesc = buildBridgeDescriptor(originalTag, originalOwner, originalDesc);
        if (originalTag == H_NEWINVOKESPECIAL) {
            if (Type.getMethodType(interceptorDesc).getReturnType().getSort() != Type.VOID) {
                throw new IllegalArgumentException(String.format("Cannot intercept constructor %s of %s with a non-void returning method %s.%s(%s)!",
                    originalDesc, originalOwner, interceptorOwner, interceptorName, interceptorDesc));
            }
            return new ConstructorBridgeMethodBuilder(originalOwner, originalDesc, bridgeDesc, interceptorOwner, interceptorName, interceptorDesc);
        }
        return new DefaultBridgeMethodBuilder(bridgeDesc, interceptorOwner, interceptorName, interceptorDesc);
    }

    private DefaultBridgeMethodBuilder(String bridgeDesc, String interceptorOwner, String interceptorName, String interceptorDesc) {
        this.bridgeDesc = bridgeDesc;
        this.interceptorOwner = interceptorOwner;
        this.interceptorName = interceptorName;
        this.interceptorDesc = interceptorDesc;
    }

    /**
     * Use when the interceptor method handles Kotlin method with default parameter values.
     *
     * @return this
     */
    public final DefaultBridgeMethodBuilder withKotlinDefaultMask() {
        hasKotlinDefaultMask = true;
        return this;
    }

    /**
     * Pass the provided class name to the interceptor method after the original arguments.
     *
     * @param className the class name
     * @return this
     */
    public final DefaultBridgeMethodBuilder withClassName(String className) {
        binaryClassName = getObjectType(className).getClassName();
        return this;
    }

    /**
     * Pass the provided filter to the interceptor method after the original arguments.
     *
     * @param context the context
     * @return this
     */
    public final DefaultBridgeMethodBuilder withVisitorContext(BytecodeInterceptorFilter context) {
        this.context = context;
        return this;
    }

    @Override
    public final String getBridgeMethodDescriptor() {
        return bridgeDesc;
    }

    @Override
    public final void buildBridgeMethod(MethodVisitor methodVisitor) {
        MethodVisitorScope mv = new MethodVisitorScope(methodVisitor);
        buildBridgeMethodImpl(mv);
        // We rely on COMPUTE_MAXs.
        // TODO(mlopatkin) Can we get a proper value?
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Generates the basic interceptor implementation that copies bridge method arguments onto stack, adds contextual arguments like caller class name, and invokes the interceptor method.
     * Whatever left on stack is returned.
     *
     * @param mv the method visitor to write bytecode with
     */
    protected void buildBridgeMethodImpl(MethodVisitorScope mv) {
        copyBridgeMethodArgsOnStack(mv);

        if (hasKotlinDefaultMask) {
            mv._LDC(0);
        }
        if (binaryClassName != null) {
            mv._LDC(binaryClassName);
        }
        if (context != null) {
            mv._GETSTATIC(VISITOR_CONTEXT_TYPE, context.name(), VISITOR_CONTEXT_TYPE.getDescriptor());
        }

        mv._INVOKESTATIC(interceptorOwner, interceptorName, interceptorDesc);
        mv._IRETURN_OF(getBridgeMethod().getReturnType());
    }

    /**
     * Helper to copy all bridge method arguments onto stack
     * @param mv the method visitor to write bytecode with
     */
    protected final void copyBridgeMethodArgsOnStack(MethodVisitorScope mv) {
        Type[] args = getBridgeMethod().getArgumentTypes();
        for (int i = 0; i < args.length; i++) {
            mv._ILOAD_OF(args[i], i);
        }
    }

    private @Nonnull Type getBridgeMethod() {
        return Type.getMethodType(bridgeDesc);
    }

    private static String buildBridgeDescriptor(int tag, String owner, String desc) {
        switch (tag) {
            case H_INVOKESTATIC:
                // When intercepting a static method, the interceptor signature matches the original one.
                return desc;
            case H_NEWINVOKESPECIAL: {
                // The constructor is represented as NEWINVOKESPECIAL:Owner.<init>(...)V. The bridge method have to call
                // the constructor itself and return the constructed value. Thus, we change the return value to Owner.
                Type originalOwner = Type.getObjectType(owner);
                Type originalMethodType = Type.getMethodType(desc);
                return Type.getMethodDescriptor(originalOwner, originalMethodType.getArgumentTypes());
            }
            case H_INVOKEINTERFACE:
            case H_INVOKEVIRTUAL: {
                // When intercepting an instance method, the interceptor gets the receiver as the first argument.
                Type originalOwner = Type.getObjectType(owner);
                Type originalMethodType = Type.getMethodType(desc);

                List<Type> interceptorArguments = new ArrayList<>(originalMethodType.getArgumentCount() + 1);
                interceptorArguments.add(originalOwner);
                interceptorArguments.addAll(Arrays.asList(originalMethodType.getArgumentTypes()));

                return Type.getMethodDescriptor(originalMethodType.getReturnType(), interceptorArguments.toArray(new Type[0]));
            }
            default:
                throw new IllegalArgumentException("Unsupported tag " + tag);
        }
    }

    private static class ConstructorBridgeMethodBuilder extends DefaultBridgeMethodBuilder {
        private final String originalOwner;
        private final String originalConstructorDesc;

        private ConstructorBridgeMethodBuilder(
            String originalOwner,
            String originalConstructorDesc,
            String bridgeDesc,
            String interceptorOwner,
            String interceptorName,
            String interceptorDesc
        ) {
            super(bridgeDesc, interceptorOwner, interceptorName, interceptorDesc);
            this.originalOwner = originalOwner;
            this.originalConstructorDesc = originalConstructorDesc;
        }

        @Override
        protected void buildBridgeMethodImpl(MethodVisitorScope mv) {
            // Unlike the INVOKESPECIAL opcode, a method reference with NEWINVOKESPECIAL tag cannot represent super constructor call.
            // Thus, we can simply call the constructor ourselves.
            Type ownerType = getObjectType(originalOwner);
            mv._NEW(ownerType);
            mv._DUP();
            copyBridgeMethodArgsOnStack(mv);
            mv._INVOKESPECIAL(ownerType, "<init>", originalConstructorDesc); // <ref>, {<ref>, arg0, ..., argN} -> {}
            mv._DUP();
            // <ref>, <ref>
            // The top <ref> will be consumed by the interceptor.
            // The constructor's interceptor returns nothing, so the next <ref> will be used as the return value.
            super.buildBridgeMethodImpl(mv);
        }
    }
}
