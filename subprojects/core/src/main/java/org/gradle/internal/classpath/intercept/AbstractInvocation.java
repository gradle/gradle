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

import static org.gradle.internal.classpath.intercept.InvocationUtils.unwrap;

/**
 * A base implementation of the Invocation that provides everything except {@link #callOriginal()}.
 *
 * @param <R> the type of the receiver
 */
public abstract class AbstractInvocation<R> implements Invocation {
    protected final R receiver;
    protected final Object[] args;

    public AbstractInvocation(R receiver, Object[] args) {
        this.receiver = receiver;
        this.args = args;
    }

    @Override
    public Object getReceiver() {
        return receiver;
    }

    @Override
    public int getArgsCount() {
        return args.length;
    }

    @Override
    public Object getArgument(int pos) {
        return unwrap(args[pos]);
    }

}
