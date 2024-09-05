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

import com.google.common.collect.Sets;

import javax.annotation.Nullable;
import java.util.Set;

public class CompositeCallInterceptor extends AbstractCallInterceptor implements SignatureAwareCallInterceptor, PropertyAwareCallInterceptor {

    private final CallInterceptor first;
    private final CallInterceptor second;

    public CompositeCallInterceptor(CallInterceptor first, CallInterceptor second) {
        this.first = first;
        this.second = second;
    }

    @Override
    @Nullable
    public Object intercept(Invocation invocation, String consumer) throws Throwable {
        return first.intercept(new Invocation() {
            @Override
            @Nullable
            public Object getReceiver() {
                return invocation.getReceiver();
            }

            @Override
            public int getArgsCount() {
                return invocation.getArgsCount();
            }

            @Override
            @Nullable
            public Object getArgument(int pos) {
                return invocation.getArgument(pos);
            }

            @Override
            @Nullable
            public Object callNext() throws Throwable {
                return second.intercept(invocation, consumer);
            }
        }, consumer);
    }

    @Override
    public Set<InterceptScope> getInterceptScopes() {
        return Sets.union(first.getInterceptScopes(), second.getInterceptScopes());
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
