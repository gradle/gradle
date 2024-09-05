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

import org.codehaus.groovy.runtime.callsite.CallSite;

import javax.annotation.Nullable;

/**
 * Represents a single invocation of the intercepted method/constructor/property.
 */
public interface Invocation {
    /**
     * Returns the receiver of the invocation.
     * It can be a {@link Class} if the invocation targets constructor, static method, or static property.
     * It can be the instance if the invocation targets the instance method or property.
     *
     * @return the receiver of the method
     * @see CallSite
     */
    @Nullable
    Object getReceiver();

    /**
     * Returns a number of arguments supplied for this invocation.
     */
    int getArgsCount();

    /**
     * Returns an <b>unwrapped</b> argument at the position {@code pos}.
     * Arguments are numbered left-to-right, from 0 to {@code getArgsCount() - 1} inclusive.
     * Throws {@link ArrayIndexOutOfBoundsException} if {@code pos} is outside the bounds.
     *
     * @param pos the position of the argument
     * @return the unwrapped value of the argument
     */
    @Nullable
    Object getArgument(int pos);

    /**
     * Returns an <b>unwrapped</b> argument at the position {@code pos} or {@code null} if the {@code pos} is greater or equal than {@link #getArgsCount()}.
     * This method is useful for handling optional arguments represented as "telescopic" overloads, like the one of the {@code Runtime.exec}:
     * <pre>
     *     Runtime.exec("/usr/bin/echo")
     *     Runtime.exec("/usr/bin/echo", new String[] {"FOO=BAR"})
     * </pre>
     *
     * @param pos the position of the argument
     * @return the unwrapped value of the argument or {@code null} if {@code pos >= getArgsCount()}
     */
    @Nullable
    default Object getOptionalArgument(int pos) {
        return pos < getArgsCount() ? getArgument(pos) : null;
    }

    /**
     * Forwards the call to the next handler and returns the result.
     * Used by interceptors when they decide that this invocation doesn't match their interception criteria or to delegate the actual call.
     * In simple cases, the next handler just calls the original Groovy implementation.
     * However, some invocation implementation may delegate to other interceptors.
     *
     * @return the value produced by the next handler
     * @throws Throwable if the next handler throws
     */
    @Nullable
    Object callNext() throws Throwable;
}
