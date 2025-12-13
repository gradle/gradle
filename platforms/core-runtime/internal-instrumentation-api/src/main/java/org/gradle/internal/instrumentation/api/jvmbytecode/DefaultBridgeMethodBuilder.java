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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import javax.annotation.CheckReturnValue;
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
public abstract class DefaultBridgeMethodBuilder implements BridgeMethodBuilder {
    private static final Type VISITOR_CONTEXT_TYPE = Type.getType(BytecodeInterceptorFilter.class);
    private final String bridgeDesc;
    private final String interceptorOwner;
    private final String interceptorName;
    private final String interceptorDesc;

    private final boolean hasKotlinDefaultMask;
    @Nullable
    private final String binaryClassName;
    @Nullable
    private final BytecodeInterceptorFilter context;

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
        switch (originalTag) {
            case H_INVOKESTATIC: {
                return new StaticBridgeMethodBuilder(originalDesc, interceptorOwner, interceptorName, interceptorDesc);
            }
            case H_NEWINVOKESPECIAL: {
                if (Type.getMethodType(interceptorDesc).getReturnType().getSort() != Type.VOID) {
                    throw new IllegalArgumentException(String.format("Cannot intercept constructor %s of %s with a non-void returning method %s.%s(%s)!",
                        originalDesc, originalOwner, interceptorOwner, interceptorName, interceptorDesc));
                }
                return new ConstructorBridgeMethodBuilder(originalOwner, originalDesc, interceptorOwner, interceptorName, interceptorDesc);
            }
            case H_INVOKEINTERFACE:
            case H_INVOKEVIRTUAL: {
                return new InstanceBridgeMethodBuilder(originalTag, originalOwner, originalDesc, interceptorOwner, interceptorName, interceptorDesc);
            }
            default:
                throw new IllegalArgumentException("Unsupported tag " + originalTag);
        }
    }

    private DefaultBridgeMethodBuilder(String bridgeDesc, String interceptorOwner, String interceptorName, String interceptorDesc) {
        this(bridgeDesc, interceptorOwner, interceptorName, interceptorDesc, false, null, null);
    }

    /**
     * Creates the copy of the provided bridge method builder with an adjusted bridge method descriptor.
     * The bridge descriptor isn't validated for compatibility with the provided builder.
     *
     * @param builder the builder to copy the other data from
     * @param bridgeDesc the new bridge method descriptor
     */
    protected DefaultBridgeMethodBuilder(DefaultBridgeMethodBuilder builder, String bridgeDesc) {
        this(bridgeDesc, builder.interceptorOwner, builder.interceptorName, builder.interceptorDesc, builder.hasKotlinDefaultMask, builder.binaryClassName, builder.context);
    }

    /**
     * Creates the copy of the provided bridge method builder with adjusted extra parameters.
     *
     * @param builder the builder to copy the other data from
     * @param hasKotlinDefaultMask if the interceptor method accepts Kotlin default mask argument
     * @param binaryClassName if the interceptor method accepts a binary class name of the class where rewrite happens
     * @param context if the interceptor method accepts the intercepting context
     *
     * @see #copy(boolean, String, BytecodeInterceptorFilter)
     */
    protected DefaultBridgeMethodBuilder(
        DefaultBridgeMethodBuilder builder,
        boolean hasKotlinDefaultMask,
        @Nullable String binaryClassName,
        @Nullable BytecodeInterceptorFilter context
    ) {
        this(builder.bridgeDesc, builder.interceptorOwner, builder.interceptorName, builder.interceptorDesc, hasKotlinDefaultMask, binaryClassName, context);
    }

    private DefaultBridgeMethodBuilder(
        String bridgeDesc,
        String interceptorOwner,
        String interceptorName,
        String interceptorDesc,
        boolean hasKotlinDefaultMask,
        @Nullable String binaryClassName,
        @Nullable BytecodeInterceptorFilter context
    ) {
        this.bridgeDesc = bridgeDesc;
        this.interceptorOwner = interceptorOwner;
        this.interceptorName = interceptorName;
        this.interceptorDesc = interceptorDesc;
        this.hasKotlinDefaultMask = hasKotlinDefaultMask;
        this.binaryClassName = binaryClassName;
        this.context = context;
    }

    @Override
    public BridgeMethodBuilder withReceiverType(String targetType) {
        throw new UnsupportedOperationException("Receiver type refinement isn't supported for " + getClass().getSimpleName());
    }

    /**
     * Use when the interceptor method handles Kotlin method with default parameter values.
     *
     * @return adjusted builder
     */
    @CheckReturnValue
    public final DefaultBridgeMethodBuilder withKotlinDefaultMask() {
        return copy(true, binaryClassName, context);
    }

    /**
     * Pass the provided class name to the interceptor method after the original arguments.
     *
     * @param className the class name
     * @return adjusted builder
     */
    @CheckReturnValue
    public final DefaultBridgeMethodBuilder withClassName(String className) {
        return copy(hasKotlinDefaultMask, getObjectType(className).getClassName(), context);
    }

    /**
     * Pass the provided filter to the interceptor method after the original arguments.
     *
     * @param context the context
     * @return adjusted builder
     */
    @CheckReturnValue
    public final DefaultBridgeMethodBuilder withVisitorContext(BytecodeInterceptorFilter context) {
        return copy(hasKotlinDefaultMask, binaryClassName, context);
    }

    protected abstract DefaultBridgeMethodBuilder copy(boolean hasKotlinDefaultMask, @Nullable String binaryClassName, @Nullable BytecodeInterceptorFilter context);

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
            // Note that we cannot reasonably get a method reference to a Kotlin method that accepts the default mask.
            // A proxy method generated by the Kotlin compiler calls this method normally, and we instrument that method call.
            // However, in theory, we can see a reference to the method that accepts all arguments, though currently the compiler generates a proxy method too.
            // Either way, we just signal the interceptor that all arguments are provided.
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
     *
     * @param mv the method visitor to write bytecode with
     */
    protected final void copyBridgeMethodArgsOnStack(MethodVisitorScope mv) {
        Type[] args = getBridgeMethod().getArgumentTypes();
        for (int i = 0; i < args.length; i++) {
            mv._ILOAD_OF(args[i], i);
        }
    }

    private @NonNull Type getBridgeMethod() {
        return Type.getMethodType(bridgeDesc);
    }

    private static class StaticBridgeMethodBuilder extends DefaultBridgeMethodBuilder {
        private StaticBridgeMethodBuilder(String bridgeDesc, String interceptorOwner, String interceptorName, String interceptorDesc) {
            // When intercepting a static method, the interceptor signature matches the original one.
            super(bridgeDesc, interceptorOwner, interceptorName, interceptorDesc);
        }

        private StaticBridgeMethodBuilder(StaticBridgeMethodBuilder builder, boolean hasKotlinDefaultMask, @Nullable String binaryClassName, @Nullable BytecodeInterceptorFilter context) {
            super(builder, hasKotlinDefaultMask, binaryClassName, context);
        }

        @Override
        protected StaticBridgeMethodBuilder copy(boolean hasKotlinDefaultMask, @Nullable String binaryClassName, @Nullable BytecodeInterceptorFilter context) {
            return new StaticBridgeMethodBuilder(this, hasKotlinDefaultMask, binaryClassName, context);
        }
    }

    private static class ConstructorBridgeMethodBuilder extends DefaultBridgeMethodBuilder {
        private final String originalOwner;
        private final String originalConstructorDesc;

        ConstructorBridgeMethodBuilder(
            String originalOwner,
            String originalConstructorDesc,
            String interceptorOwner,
            String interceptorName,
            String interceptorDesc
        ) {
            super(buildConstructorBridgeDesc(originalOwner, originalConstructorDesc), interceptorOwner, interceptorName, interceptorDesc);
            this.originalOwner = originalOwner;
            this.originalConstructorDesc = originalConstructorDesc;
        }

        private ConstructorBridgeMethodBuilder(ConstructorBridgeMethodBuilder builder, boolean hasKotlinDefaultMask, @Nullable String binaryClassName, @Nullable BytecodeInterceptorFilter context) {
            super(builder, hasKotlinDefaultMask, binaryClassName, context);
            this.originalOwner = builder.originalOwner;
            this.originalConstructorDesc = builder.originalConstructorDesc;
        }

        @Override
        protected ConstructorBridgeMethodBuilder copy(boolean hasKotlinDefaultMask, @Nullable String binaryClassName, @Nullable BytecodeInterceptorFilter context) {
            return new ConstructorBridgeMethodBuilder(this, hasKotlinDefaultMask, binaryClassName, context);
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

        private static String buildConstructorBridgeDesc(String owner, String desc) {
            // The constructor is represented as NEWINVOKESPECIAL:Owner.<init>(...)V. The bridge method have to call
            // the constructor itself and return the constructed value. Thus, we change the return value to Owner.
            Type originalOwner = Type.getObjectType(owner);
            Type originalMethodType = Type.getMethodType(desc);
            return Type.getMethodDescriptor(originalOwner, originalMethodType.getArgumentTypes());
        }
    }

    private static class InstanceBridgeMethodBuilder extends DefaultBridgeMethodBuilder {
        private final int tag;
        private final String originalDesc;

        InstanceBridgeMethodBuilder(int tag, String originalOwner, String originalDesc, String interceptorOwner, String interceptorName, String interceptorDesc) {
            super(buildInstanceBridgeDesc(tag, originalOwner, originalDesc), interceptorOwner, interceptorName, interceptorDesc);
            this.tag = tag;
            this.originalDesc = originalDesc;
        }

        private InstanceBridgeMethodBuilder(String refinedOwner, InstanceBridgeMethodBuilder builder) {
            super(builder, buildInstanceBridgeDesc(builder.tag, refinedOwner, builder.originalDesc));
            this.tag = builder.tag;
            this.originalDesc = builder.originalDesc;
        }

        private InstanceBridgeMethodBuilder(InstanceBridgeMethodBuilder builder, boolean hasKotlinDefaultMask, @Nullable String binaryClassName, @Nullable BytecodeInterceptorFilter context) {
            super(builder, hasKotlinDefaultMask, binaryClassName, context);
            this.tag = builder.tag;
            this.originalDesc = builder.originalDesc;
        }

        @Override
        protected InstanceBridgeMethodBuilder copy(boolean hasKotlinDefaultMask, @Nullable String binaryClassName, @Nullable BytecodeInterceptorFilter context) {
            return new InstanceBridgeMethodBuilder(this, hasKotlinDefaultMask, binaryClassName, context);
        }

        @Override
        public BridgeMethodBuilder withReceiverType(String targetType) {
            return new InstanceBridgeMethodBuilder(targetType, this);
        }

        private static String buildInstanceBridgeDesc(int tag, String owner, String desc) {
            assert tag == H_INVOKEINTERFACE || tag == H_INVOKEVIRTUAL;
            // When intercepting an instance method, the interceptor gets the receiver as the first argument.
            Type originalOwner = Type.getObjectType(owner);
            Type originalMethodType = Type.getMethodType(desc);

            List<Type> interceptorArguments = new ArrayList<>(originalMethodType.getArgumentCount() + 1);
            interceptorArguments.add(originalOwner);
            interceptorArguments.addAll(Arrays.asList(originalMethodType.getArgumentTypes()));

            return Type.getMethodDescriptor(originalMethodType.getReturnType(), interceptorArguments.toArray(new Type[0]));
        }
    }
}
