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

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorType;

import javax.annotation.Nullable;

public class CompositeCallInterceptor extends CallInterceptor implements SignatureAwareCallInterceptor, PropertyAwareCallInterceptor {

    private final CallInterceptor first;
    private final CallInterceptor second;

    public CompositeCallInterceptor(CallInterceptor first, CallInterceptor second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public Object doIntercept(Invocation invocation, String consumer) throws Throwable {
        return first.doIntercept(new Invocation() {
            @Override
            public Object getReceiver() {
                return invocation.getReceiver();
            }

            @Override
            public int getArgsCount() {
                return invocation.getArgsCount();
            }

            @Override
            public Object getArgument(int pos) {
                return invocation.getArgument(pos);
            }

            @Override
            public Object callOriginal() throws Throwable {
                return second.doIntercept(invocation, consumer);
            }
        }, consumer);
    }

    @Override
    public BytecodeInterceptorType getType() {
        throw new UnsupportedOperationException("Calling CompositeCallInterceptor.getType() is not supported");
    }

    @Override
    InterceptScope[] getInterceptScopes() {
        throw new UnsupportedOperationException("Calling CompositeCallInterceptor.getInterceptScopes() is not supported");
    }

    @Nullable
    @Override
    public Class<?> matchesProperty(Class<?> receiverClass) {
        Class<?> typeOfProperty = null;
        if (first instanceof PropertyAwareCallInterceptor) {
            typeOfProperty = ((PropertyAwareCallInterceptor) first).matchesProperty(receiverClass);
        }
        if (typeOfProperty == null && second instanceof PropertyAwareCallInterceptor) {
            typeOfProperty = ((PropertyAwareCallInterceptor) second).matchesProperty(receiverClass);
        }
        return typeOfProperty;
    }

    @Nullable
    @Override
    public SignatureMatch matchesMethodSignature(Class<?> receiverClass, Class<?>[] argumentClasses, boolean isStatic) {
        SignatureMatch signatureMatch = null;
        if (first instanceof SignatureAwareCallInterceptor) {
            signatureMatch = ((SignatureAwareCallInterceptor) first).matchesMethodSignature(receiverClass, argumentClasses, isStatic);
        }
        if (signatureMatch == null && second instanceof SignatureAwareCallInterceptor) {
            signatureMatch = ((SignatureAwareCallInterceptor) second).matchesMethodSignature(receiverClass, argumentClasses, isStatic);
        }
        return signatureMatch;
    }
}
