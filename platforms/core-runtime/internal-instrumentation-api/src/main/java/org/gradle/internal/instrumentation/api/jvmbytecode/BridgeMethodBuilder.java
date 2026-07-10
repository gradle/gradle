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

import org.objectweb.asm.MethodVisitor;

import javax.annotation.CheckReturnValue;

/**
 * A generator for a static method that replaces an intercepted method reference.
 */
public interface BridgeMethodBuilder {
    /**
     * The expected descriptor of the bridge method.
     *
     * @return the descriptor
     */
    String getBridgeMethodDescriptor();

    /**
     * Uses the provided methodVisitor to build the bridge method.
     *
     * @param methodVisitor the visitor
     */
    void buildBridgeMethod(MethodVisitor methodVisitor);

    /**
     * Adjusts the bridge method signature to accept a subtype of the owner type.
     * This is only possible for bridges of the instance or interface methods.
     * No subtype check is performed.
     *
     * @param targetType the target type
     * @return a new bridge method builder
     * @throws UnsupportedOperationException if this bridge method is not for an instance/interface method
     */
    @CheckReturnValue
    BridgeMethodBuilder withReceiverType(String targetType);
}
