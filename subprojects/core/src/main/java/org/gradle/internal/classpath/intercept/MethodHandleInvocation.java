/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.classpath.intercept;

import java.lang.invoke.MethodHandle;

import static org.gradle.internal.classpath.intercept.InvocationUtils.unwrap;

/**
 * The implementation of {@link Invocation} that forwards the call to a MethodHandle. Supports both normal and spread Groovy calls.
 */
class MethodHandleInvocation implements Invocation {
    private final MethodHandle original;
    private final Object[] originalArgs;
    private final Object[] unspreadArgs;
    private final int unspreadArgsOffset;

    public MethodHandleInvocation(MethodHandle original, Object[] originalArgs, boolean isSpread) {
        this.original = original;
        this.originalArgs = originalArgs;
        if (isSpread) {
            unspreadArgs = (Object[]) originalArgs[1];
            unspreadArgsOffset = 0;
        } else {
            unspreadArgs = originalArgs;
            unspreadArgsOffset = 1;
        }
    }

    @Override
    public Object getReceiver() {
        return unwrap(originalArgs[0]);
    }

    @Override
    public int getArgsCount() {
        return unspreadArgs.length - unspreadArgsOffset;
    }

    @Override
    public Object getArgument(int pos) {
        return unwrap(unspreadArgs[pos + unspreadArgsOffset]);
    }

    @Override
    public Object callOriginal() throws Throwable {
        return original.invokeExact(originalArgs);
    }
}
