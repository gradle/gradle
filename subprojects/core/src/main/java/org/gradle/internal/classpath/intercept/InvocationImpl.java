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

import javax.annotation.Nullable;

import static org.gradle.internal.classpath.intercept.InvocationUtils.unwrap;

/**
 * A simple implementation of the Invocation that accepts a lambda for {@link #callNext()} implementation.
 *
 * @param <R> the type of the receiver
 */
public final class InvocationImpl<R> implements Invocation {
    @FunctionalInterface
    public interface ThrowingSupplier {
        @Nullable Object get() throws Throwable;
    }

    private final R receiver;
    private final Object[] args;
    private final ThrowingSupplier callOriginal;

    public InvocationImpl(R receiver, Object[] args, ThrowingSupplier callNext) {
        this.receiver = receiver;
        this.args = args;
        this.callOriginal = callNext;
    }

    @Override
    public R getReceiver() {
        return receiver;
    }

    @Override
    public int getArgsCount() {
        return args.length;
    }

    @Override
    @Nullable
    public Object getArgument(int pos) {
        return unwrap(args[pos]);
    }

    @Override
    @Nullable
    public Object callNext() throws Throwable {
        return callOriginal.get();
    }
}
