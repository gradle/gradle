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
 * Adds exact source positions to statically dispatched {@code Property.set} and {@code Property.convention}
 * calls in instrumented build logic. Groovy DSL property dispatch is intentionally not handled here.
 */
public final class PropertyCallSiteInterceptor implements JvmBytecodeCallInterceptor {
    private static final String PROPERTY = "org/gradle/api/provider/Property";
    private static final String CALL_SITES = "org/gradle/api/internal/provider/provenance/PropertyCallSites";

    private static final String SET_VALUE = "(Ljava/lang/Object;)V";
    private static final String SET_PROVIDER = "(Lorg/gradle/api/provider/Provider;)V";
    private static final String CONVENTION_VALUE = "(Ljava/lang/Object;)Lorg/gradle/api/provider/Property;";
    private static final String CONVENTION_PROVIDER = "(Lorg/gradle/api/provider/Provider;)Lorg/gradle/api/provider/Property;";

    private static final String INTERCEPT_SET_VALUE =
        "(Lorg/gradle/api/provider/Property;Ljava/lang/Object;Ljava/lang/String;)V";
    private static final String INTERCEPT_SET_PROVIDER =
        "(Lorg/gradle/api/provider/Property;Lorg/gradle/api/provider/Provider;Ljava/lang/String;)V";
    private static final String INTERCEPT_CONVENTION_VALUE =
        "(Lorg/gradle/api/provider/Property;Ljava/lang/Object;Ljava/lang/String;)Lorg/gradle/api/provider/Property;";
    private static final String INTERCEPT_CONVENTION_PROVIDER =
        "(Lorg/gradle/api/provider/Property;Lorg/gradle/api/provider/Provider;Ljava/lang/String;)Lorg/gradle/api/provider/Property;";

    private final InstrumentationMetadata metadata;

    public PropertyCallSiteInterceptor(InstrumentationMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public boolean visitMethodInsn(
        MethodVisitorScope methodVisitor,
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
        String interceptorDescriptor = interceptorDescriptor(name, descriptor);
        if (interceptorDescriptor == null || !(methodVisitor instanceof CallSiteSource)) {
            return false;
        }
        CallSiteSource source = (CallSiteSource) methodVisitor;
        if (source.getSourceFileName() == null || source.getLineNumber() <= 0) {
            return false;
        }
        if (!PROPERTY.equals(owner) && !metadata.isInstanceOf(owner, PROPERTY)) {
            return false;
        }

        methodVisitor._LDC(source.getSourceFileName() + ":" + source.getLineNumber());
        methodVisitor._INVOKESTATIC(CALL_SITES, name, interceptorDescriptor);
        return true;
    }

    private static @Nullable String interceptorDescriptor(String name, String descriptor) {
        if ("set".equals(name)) {
            if (SET_VALUE.equals(descriptor)) {
                return INTERCEPT_SET_VALUE;
            }
            if (SET_PROVIDER.equals(descriptor)) {
                return INTERCEPT_SET_PROVIDER;
            }
        } else if ("convention".equals(name)) {
            if (CONVENTION_VALUE.equals(descriptor)) {
                return INTERCEPT_CONVENTION_VALUE;
            }
            if (CONVENTION_PROVIDER.equals(descriptor)) {
                return INTERCEPT_CONVENTION_PROVIDER;
            }
        }
        return null;
    }

    @Override
    public @Nullable BridgeMethodBuilder findBridgeMethodBuilder(String className, int tag, String owner, String name, String descriptor) {
        return null;
    }

    @Override
    public BytecodeInterceptorType getType() {
        return BytecodeInterceptorType.INSTRUMENTATION;
    }
}
