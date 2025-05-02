/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.internal.instrumentation.api.groovybytecode.CallInterceptor;
import org.gradle.internal.instrumentation.api.groovybytecode.InterceptScope;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.lazy.Lazy;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

import static org.gradle.internal.classpath.intercept.CallInterceptorRegistry.getGroovyCallDecorator;

@NullMarked
public interface CallInterceptorResolver {

    @Nullable
    CallInterceptor resolveCallInterceptor(InterceptScope scope);

    boolean isAwareOfCallSiteName(String name);

    @NullMarked
    final class ClosureCallInterceptorResolver implements CallInterceptorResolver {

        private static final Lazy<Map<BytecodeInterceptorFilter, ClosureCallInterceptorResolver>> RESOLVERS = Lazy.locking().of(() -> {
            Map<BytecodeInterceptorFilter, ClosureCallInterceptorResolver> resolvers = new EnumMap<>(BytecodeInterceptorFilter.class);
            for (BytecodeInterceptorFilter filter : BytecodeInterceptorFilter.values()) {
                resolvers.put(filter, new ClosureCallInterceptorResolver(filter));
            }
            return resolvers;
        });

        private final BytecodeInterceptorFilter interceptorFilter;

        public ClosureCallInterceptorResolver(BytecodeInterceptorFilter interceptorFilter) {
            this.interceptorFilter = interceptorFilter;
        }

        @Nullable
        @Override
        public CallInterceptor resolveCallInterceptor(InterceptScope scope) {
            CallSiteDecorator currentDecorator = getGroovyCallDecorator(interceptorFilter);
            if (currentDecorator instanceof CallInterceptorResolver) {
                return ((CallInterceptorResolver) currentDecorator).resolveCallInterceptor(scope);
            }
            return null;
        }

        @Override
        public boolean isAwareOfCallSiteName(String name) {
            CallSiteDecorator currentDecorator = getGroovyCallDecorator(interceptorFilter);
            if (currentDecorator instanceof CallInterceptorResolver) {
                return ((CallInterceptorResolver) currentDecorator).isAwareOfCallSiteName(name);
            }
            return false;
        }

        public static CallInterceptorResolver of(BytecodeInterceptorFilter filter) {
            return RESOLVERS.get().get(filter);
        }
    }
}
