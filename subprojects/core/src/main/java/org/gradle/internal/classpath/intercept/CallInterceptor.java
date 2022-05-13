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

import org.codehaus.groovy.runtime.callsite.AbstractCallSite;
import org.codehaus.groovy.runtime.callsite.CallSite;

/**
 * Intercepts method and constructor calls as well as property reads in dynamic Groovy bytecode.
 * <p>
 * The interceptor serves as a factory for decorated Groovy {@link CallSite} instances.
 * These instances replace original CallSites where the calls are to be intercepted.
 * <p>
 * Descendants of this class should be thread-safe, making a stateless implementation is perfect.
 */
public abstract class CallInterceptor {
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
        Object getArgument(int pos);

        /**
         * Returns an <b>unwrapped</b> argument at the position {@code pos} or {@code null} if the {@code pos} is greater or equal than {@link #getArgsCount()}.
         * This method is useful for handling optional arguments represented as "telescopic" overloads, like the one of the {@code Runtime.exec}:
         * <pre>
         *     Runtime.exec("/usr/bin/echo")
         *     Runtime.exec("/usr/bin/echo", new String[] {"FOO=BAR"})
         * </pre>
         * @param pos the position of the argument
         * @return the unwrapped value of the argument or {@code null} if {@code pos >= getArgsCount()}
         */
        default Object getOptionalArgument(int pos) {
            return pos < getArgsCount() ? getArgument(pos) : null;
        }

        /**
         * Forwards the call to the original Groovy implementation and returns the result.
         *
         * @return the value produced by the original Groovy implementation
         * @throws Throwable if the original Groovy implementation throws
         */
        Object callOriginal() throws Throwable;
    }

    /**
     * Called when the method/constructor/property read is intercepted.
     * The return value of this method becomes the result of the intercepted call.
     * The implementation may throw, and the exception will be propagated to the caller.
     * The implementation may choose to delegate to the original Groovy implementation by returning the value of {@code invocation.callOriginal()}.
     *
     * @param invocation the arguments supplied by the caller
     * @param consumer the class that invokes the intercepted call
     * @return the value to return to the caller
     * @throws Throwable if necessary to propagate it to the caller
     */
    protected abstract Object doIntercept(Invocation invocation, String consumer) throws Throwable;

    /**
     * Decorates a Groovy {@link CallSite}. The returned CallSite instance will perform the actual interception.
     *
     * @param prev the CallSite to decorate
     * @return the new CallSite capable of intercepting calls.
     */
    public CallSite decorateCallSite(CallSite prev) {
        return new DecoratingCallSite(prev);
    }

    private class DecoratingCallSite extends AbstractCallSite {
        public DecoratingCallSite(CallSite prev) {
            super(prev);
        }

        @Override
        public Object call(Object receiver, Object[] args) throws Throwable {
            return doIntercept(new AbstractInvocation<Object>(receiver, args) {
                @Override
                public Object callOriginal() throws Throwable {
                    return DecoratingCallSite.super.call(receiver, args);
                }
            }, array.owner.getName());
        }

        @Override
        public Object callGetProperty(Object receiver) throws Throwable {
            return doIntercept(new AbstractInvocation<Object>(receiver, new Object[0]) {
                @Override
                public Object callOriginal() throws Throwable {
                    return DecoratingCallSite.super.callGetProperty(receiver);
                }
            }, array.owner.getName());
        }

        @Override
        public Object callStatic(Class receiver, Object[] args) throws Throwable {
            return doIntercept(new AbstractInvocation<Class<?>>(receiver, args) {
                @Override
                public Object callOriginal() throws Throwable {
                    return DecoratingCallSite.super.callStatic(receiver, args);
                }
            }, array.owner.getName());
        }

        @Override
        public Object callConstructor(Object receiver, Object[] args) throws Throwable {
            return doIntercept(new AbstractInvocation<Object>(receiver, args) {
                @Override
                public Object callOriginal() throws Throwable {
                    return DecoratingCallSite.super.callConstructor(receiver, args);
                }
            }, array.owner.getName());
        }
    }

}
