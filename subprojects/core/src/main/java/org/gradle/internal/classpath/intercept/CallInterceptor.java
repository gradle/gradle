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
import org.codehaus.groovy.vmplugin.v8.IndyInterface;
import org.gradle.api.GradleException;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptor;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Intercepts method and constructor calls as well as property reads in dynamic Groovy bytecode.
 * <p>
 * The interceptor serves as a factory for decorated Groovy {@link CallSite} instances.
 * These instances replace original CallSites where the calls are to be intercepted.
 * <p>
 * Descendants of this class should be thread-safe, making a stateless implementation is perfect.
 */
public abstract class CallInterceptor implements BytecodeInterceptor {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle INTERCEPTOR;

    static {
        try {
            INTERCEPTOR = LOOKUP.findVirtual(CallInterceptor.class, "interceptMethodHandle", MethodType.methodType(Object.class, MethodHandle.class, int.class, String.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new GradleException("Failed to set up an interceptor method", e);
        }
    }

    private final InterceptScope[] interceptScopes;

    protected CallInterceptor(InterceptScope... interceptScopes) {
        this.interceptScopes = interceptScopes;
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
    public abstract Object doIntercept(Invocation invocation, String consumer) throws Throwable;

    MethodHandle decorateMethodHandle(MethodHandle original, MethodHandles.Lookup caller, int flags) {
        MethodHandle spreader = original.asSpreader(Object[].class, original.type().parameterCount());
        MethodHandle decorated = MethodHandles.insertArguments(INTERCEPTOR, 0, this, spreader, flags, caller.lookupClass().getName());
        return decorated.asCollector(Object[].class, original.type().parameterCount()).asType(original.type());
    }

    private Object interceptMethodHandle(MethodHandle original, int flags, String consumer, Object[] args) throws Throwable {
        boolean isSpread = (flags & IndyInterface.SPREAD_CALL) != 0;
        return doIntercept(new MethodHandleInvocation(original, args, isSpread), consumer);
    }

    InterceptScope[] getInterceptScopes() {
        return interceptScopes;
    }
}
