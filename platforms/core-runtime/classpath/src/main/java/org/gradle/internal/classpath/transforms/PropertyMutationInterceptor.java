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

package org.gradle.internal.classpath.transforms;

import org.gradle.internal.instrumentation.api.jvmbytecode.BridgeMethodBuilder;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;
import org.gradle.model.internal.asm.MethodVisitorScope;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodNode;

import java.util.function.Supplier;

/**
 * Rewrites property mutation calls so that they carry their own source position.
 * <p>
 * A call site is a compile-time constant, so handing it to the runtime is free, where discovering it by walking
 * the stack at the moment of mutation costs microseconds and fails outright when no user frame is on the stack.
 * This only supplies <em>where</em>: the contributor still comes from the user code application context, because
 * a helper class shared between plugins has the same call site whichever plugin calls it.
 * <p>
 * Only plain JVM call sites are handled. A Groovy property assignment goes through dynamic dispatch and would
 * need the Groovy interception path instead.
 */
public class PropertyMutationInterceptor implements JvmBytecodeCallInterceptor {

    private static final String PROPERTY = "org/gradle/api/provider/Property";
    private static final String CALL_SITES = "org/gradle/api/internal/provider/provenance/PropertyCallSites";

    private static final String SET_VALUE_DESCRIPTOR = "(Ljava/lang/Object;)V";
    private static final String SET_PROVIDER_DESCRIPTOR = "(Lorg/gradle/api/provider/Provider;)V";
    private static final String INTERCEPT_VALUE_DESCRIPTOR =
        "(Lorg/gradle/api/provider/Property;Ljava/lang/Object;Ljava/lang/String;)V";
    private static final String INTERCEPT_PROVIDER_DESCRIPTOR =
        "(Lorg/gradle/api/provider/Property;Lorg/gradle/api/provider/Provider;Ljava/lang/String;)V";

    private final InstrumentationMetadata metadata;

    public PropertyMutationInterceptor(InstrumentationMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean visitMethodInsn(
        MethodVisitorScope mv,
        String className,
        int opcode,
        String owner,
        String name,
        String descriptor,
        boolean isInterface,
        Supplier<MethodNode> readMethodNode
    ) {
        if (opcode != Opcodes.INVOKEINTERFACE && opcode != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        if (!"set".equals(name)) {
            return false;
        }
        String interceptorDescriptor = interceptorDescriptorFor(descriptor);
        if (interceptorDescriptor == null) {
            return false;
        }
        if (!(mv instanceof CallSiteSource)) {
            return false;
        }
        CallSiteSource callSite = (CallSiteSource) mv;
        String sourceFileName = callSite.getSourceFileName();
        if (sourceFileName == null || callSite.getLineNumber() <= 0) {
            // compiled without debug information, so there is no call site to bake in
            return false;
        }
        // The declared receiver can be any Property subtype, so ask the hierarchy rather than the name.
        if (!PROPERTY.equals(owner) && !metadata.isInstanceOf(owner, PROPERTY)) {
            return false;
        }

        // One joined constant rather than a file and a line: it lands in the class constant pool, so the
        // runtime neither concatenates nor allocates.
        // stack: receiver, value -> receiver, value, callSite
        mv._LDC(sourceFileName + ":" + callSite.getLineNumber());
        mv._INVOKESTATIC(CALL_SITES, "set", interceptorDescriptor);
        return true;
    }

    @Nullable
    private static String interceptorDescriptorFor(String descriptor) {
        if (SET_VALUE_DESCRIPTOR.equals(descriptor)) {
            return INTERCEPT_VALUE_DESCRIPTOR;
        }
        if (SET_PROVIDER_DESCRIPTOR.equals(descriptor)) {
            return INTERCEPT_PROVIDER_DESCRIPTOR;
        }
        return null;
    }

    @Nullable
    @Override
    public BridgeMethodBuilder findBridgeMethodBuilder(String className, int tag, String owner, String name, String descriptor) {
        // Only direct calls are rewritten; a method reference to Property.set keeps its original behaviour.
        return null;
    }

    @Override
    public BytecodeInterceptorType getType() {
        return BytecodeInterceptorType.INSTRUMENTATION;
    }
}
