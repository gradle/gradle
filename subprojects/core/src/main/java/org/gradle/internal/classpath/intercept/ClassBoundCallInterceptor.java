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

/**
 * A special case of the CallInterceptor for static methods and static properties.
 * It only intercepts the calls where the receiver is the class of interest.
 * <p>
 * It is possible but not strictly necessary to use this interceptor to intercept constructors.
 * Due to the way constructor interception works, having an {@link InterceptScope#constructorsOf(Class)
 * as a scope already guarantees that the invocation would have the given class object as the receiver.
 */
public abstract class ClassBoundCallInterceptor extends AbstractCallInterceptor {
    private final Class<?> expectedReceiver;

    public ClassBoundCallInterceptor(Class<?> expectedReceiver, InterceptScope... scopes) {
        super(scopes);
        this.expectedReceiver = expectedReceiver;
    }

    @Override
    public final Object intercept(Invocation invocation, String consumer) throws Throwable {
        if (!expectedReceiver.equals(invocation.getReceiver())) {
            return invocation.callOriginal();
        }
        return interceptSafe(invocation, consumer);
    }

    /**
     * Same as the {@link AbstractCallInterceptor#intercept(Invocation, String)} but the {@code invocation.getReceiver()} is guaranteed
     * to be the {@code expectedReceiver} passed to the constructor.
     *
     * @param invocation the arguments supplied by the caller
     * @param consumer the class that invokes the intercepted call
     * @return the value to return to the caller
     * @throws Throwable if necessary to propagate it to the caller
     */
    protected abstract Object interceptSafe(Invocation invocation, String consumer) throws Throwable;
}
