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

package org.gradle.internal.instrumentation.api.jvmbytecode;

import org.gradle.internal.instrumentation.api.metadata.InstrumentationMetadata;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;

import java.util.function.Supplier;

public interface JvmBytecodeCallInterceptor {
    boolean visitMethodInsn(
            String className,
            int opcode,
            String owner,
            String name,
            String descriptor,
            boolean isInterface,
            Supplier<MethodNode> readMethodNode
    );

    interface Factory {
        JvmBytecodeCallInterceptor create(MethodVisitor methodVisitor, InstrumentationMetadata metadata);
    }
}
