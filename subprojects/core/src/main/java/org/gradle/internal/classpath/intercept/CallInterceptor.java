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

package org.gradle.internal.classpath.intercept;

import org.codehaus.groovy.runtime.callsite.CallSite;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Set;

/**
 * Intercepts method and constructor calls as well as property reads in dynamic Groovy bytecode.
 * <p>
 * The interceptor serves as a factory for decorated Groovy {@link CallSite} instances.
 * These instances replace original CallSites where the calls are to be intercepted.
 * <p>
 * Descendants of this class should be thread-safe, making a stateless implementation is perfect.
 */
public interface CallInterceptor {

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
    Object intercept(Invocation invocation, String consumer) throws Throwable;

    MethodHandle decorateMethodHandle(MethodHandle original, MethodHandles.Lookup caller, int flags);

    Set<InterceptScope> getInterceptScopes();
}
