/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.asm;

import org.jspecify.annotations.NullMarked;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.List;

/**
 * Unifying type between {@code invokedynamic} and {@code CONSTANT_Dynamic} bootstrap methods.
 */
@NullMarked
public final class BootstrapMethod {
    public static BootstrapMethod fromIndy(Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        return new BootstrapMethod(bootstrapMethodHandle, Arrays.asList(bootstrapMethodArguments));
    }

    public static BootstrapMethod fromConstantDynamic(ConstantDynamic constantDynamic) {
        return new BootstrapMethod(constantDynamic.getBootstrapMethod(), new ConstantDynamicBootstrapArguments(constantDynamic));
    }

    private static class ConstantDynamicBootstrapArguments extends AbstractList<Object> {
        private final ConstantDynamic constantDynamic;

        public ConstantDynamicBootstrapArguments(ConstantDynamic constantDynamic) {
            this.constantDynamic = constantDynamic;
        }

        @Override
        public Object get(int index) {
            return constantDynamic.getBootstrapMethodArgument(index);
        }

        @Override
        public int size() {
            return constantDynamic.getBootstrapMethodArgumentCount();
        }
    }

    private final Handle handle;
    private final List<Object> arguments;

    private BootstrapMethod(Handle handle, List<Object> arguments) {
        this.handle = handle;
        this.arguments = arguments;
    }

    public Handle getHandle() {
        return handle;
    }

    public List<Object> getArguments() {
        return arguments;
    }
}
