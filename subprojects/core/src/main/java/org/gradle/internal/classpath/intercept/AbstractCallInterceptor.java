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

import com.google.common.collect.ImmutableSet;
import org.codehaus.groovy.vmplugin.v8.IndyInterface;
import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

public abstract class AbstractCallInterceptor implements CallInterceptor {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodHandle INTERCEPTOR;

    static {
        try {
            INTERCEPTOR = LOOKUP.findVirtual(AbstractCallInterceptor.class, "interceptMethodHandle", MethodType.methodType(Object.class, MethodHandle.class, int.class, String.class, Object[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new GradleException("Failed to set up an interceptor method", e);
        }
    }

    private final Set<InterceptScope> interceptScopes;

    protected AbstractCallInterceptor(InterceptScope... interceptScopes) {
        this.interceptScopes = ImmutableSet.copyOf(interceptScopes);
    }

    @Override
    public MethodHandle decorateMethodHandle(MethodHandle original, MethodHandles.Lookup caller, int flags) {
        MethodHandle spreader = original.asSpreader(Object[].class, original.type().parameterCount());
        MethodHandle decorated = MethodHandles.insertArguments(INTERCEPTOR, 0, this, spreader, flags, caller.lookupClass().getName());
        return decorated.asCollector(Object[].class, original.type().parameterCount()).asType(original.type());
    }

    @Nullable
    private Object interceptMethodHandle(MethodHandle original, int flags, String consumer, Object[] args) throws Throwable {
        boolean isSpread = (flags & IndyInterface.SPREAD_CALL) != 0;
        return intercept(new MethodHandleInvocation(original, args, isSpread), consumer);
    }

    @Override
    public Set<InterceptScope> getInterceptScopes() {
        return interceptScopes;
    }
}
