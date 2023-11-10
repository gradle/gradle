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
import org.gradle.internal.instrumentation.api.capabilities.InterceptorsRequest;
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

        private static final Lazy<Map<InterceptorsRequest, ClosureCallInterceptorResolver>> RESOLVERS = Lazy.locking().of(() -> {
            Map<InterceptorsRequest, ClosureCallInterceptorResolver> resolvers = new EnumMap<>(InterceptorsRequest.class);
            for (InterceptorsRequest request : InterceptorsRequest.values()) {
                resolvers.put(request, new ClosureCallInterceptorResolver(request));
            }
            return resolvers;
        });

        private final InterceptorsRequest interceptorsRequest;

        public ClosureCallInterceptorResolver(InterceptorsRequest interceptorsRequest) {
            this.interceptorsRequest = interceptorsRequest;
        }

        @Nullable
        @Override
        public CallInterceptor resolveCallInterceptor(InterceptScope scope) {
            CallSiteDecorator currentDecorator = getGroovyCallDecorator(interceptorsRequest);
            if (currentDecorator instanceof CallInterceptorResolver) {
                return ((CallInterceptorResolver) currentDecorator).resolveCallInterceptor(scope);
            }
            return null;
        }

        @Override
        public boolean isAwareOfCallSiteName(String name) {
            CallSiteDecorator currentDecorator = getGroovyCallDecorator(interceptorsRequest);
            if (currentDecorator instanceof CallInterceptorResolver) {
                return ((CallInterceptorResolver) currentDecorator).isAwareOfCallSiteName(name);
            }
            return false;
        }

        public static CallInterceptorResolver of(InterceptorsRequest request) {
            return RESOLVERS.get().get(request);
        }
    }
}
