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

import org.gradle.api.NonNullApi;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorRequest;
import org.gradle.internal.lazy.Lazy;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

import static org.gradle.internal.classpath.intercept.CallInterceptorRegistry.getGroovyCallDecorator;

@NonNullApi
public interface CallInterceptorResolver {

    @Nullable
    CallInterceptor resolveCallInterceptor(InterceptScope scope);

    boolean isAwareOfCallSiteName(String name);

    @NonNullApi
    final class ClosureCallInterceptorResolver implements CallInterceptorResolver {

        private static final Lazy<Map<BytecodeInterceptorRequest, ClosureCallInterceptorResolver>> RESOLVERS = Lazy.locking().of(() -> {
            Map<BytecodeInterceptorRequest, ClosureCallInterceptorResolver> resolvers = new EnumMap<>(BytecodeInterceptorRequest.class);
            for (BytecodeInterceptorRequest request : BytecodeInterceptorRequest.values()) {
                resolvers.put(request, new ClosureCallInterceptorResolver(request));
            }
            return resolvers;
        });

        private final BytecodeInterceptorRequest interceptorRequest;

        public ClosureCallInterceptorResolver(BytecodeInterceptorRequest interceptorRequest) {
            this.interceptorRequest = interceptorRequest;
        }

        @Nullable
        @Override
        public CallInterceptor resolveCallInterceptor(InterceptScope scope) {
            CallSiteDecorator currentDecorator = getGroovyCallDecorator(interceptorRequest);
            if (currentDecorator instanceof CallInterceptorResolver) {
                return ((CallInterceptorResolver) currentDecorator).resolveCallInterceptor(scope);
            }
            return null;
        }

        @Override
        public boolean isAwareOfCallSiteName(String name) {
            CallSiteDecorator currentDecorator = getGroovyCallDecorator(interceptorRequest);
            if (currentDecorator instanceof CallInterceptorResolver) {
                return ((CallInterceptorResolver) currentDecorator).isAwareOfCallSiteName(name);
            }
            return false;
        }

        public static CallInterceptorResolver of(BytecodeInterceptorRequest request) {
            return RESOLVERS.get().get(request);
        }
    }
}
