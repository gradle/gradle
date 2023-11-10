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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.NonNullApi;
import org.gradle.internal.classpath.GroovyCallInterceptorsProvider;
import org.gradle.internal.instrumentation.api.capabilities.InterceptorsRequest;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
public class CallInterceptorRegistry {
    private static final Map<ClassLoader, Boolean> LOADED_FROM_CLASSLOADERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Map<InterceptorsRequest, CallSiteDecorator> groovyDecorators = new ConcurrentHashMap<>();
    private static volatile Map<InterceptorsRequest, JvmBytecodeInterceptorSet> jvmInterceptorSets = new ConcurrentHashMap<>();
    private static volatile GroovyCallSiteInterceptorSet currentGroovyCallInterceptorSet = new DefaultGroovyCallSiteInterceptorSet(GroovyCallInterceptorsProvider.DEFAULT);
    private static volatile JvmBytecodeInterceptorFactorySet currentJvmBytecodeFactorySet = new DefaultJvmBytecodeInterceptorFactorySet(JvmBytecodeInterceptorFactoryProvider.DEFAULT);

    public synchronized static void loadCallInterceptors(ClassLoader classLoader) {
        if (LOADED_FROM_CLASSLOADERS.put(classLoader, true) != null) {
            throw new RuntimeException("Cannot load interceptors twice for class loader: " + classLoader);
        }

        groovyDecorators = new ConcurrentHashMap<>();
        jvmInterceptorSets = new ConcurrentHashMap<>();
        GroovyCallInterceptorsProvider classLoaderGroovyCallInterceptors = new GroovyCallInterceptorsProvider.ClassLoaderSourceGroovyCallInterceptorsProvider(classLoader);
        GroovyCallInterceptorsProvider callInterceptors = GroovyCallInterceptorsProvider.DEFAULT.plus(classLoaderGroovyCallInterceptors);
        currentGroovyCallInterceptorSet = new DefaultGroovyCallSiteInterceptorSet(callInterceptors);
        JvmBytecodeInterceptorFactoryProvider.ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider classLoaderJvmBytecodeInterceptors = new JvmBytecodeInterceptorFactoryProvider.ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider(classLoader);
        currentJvmBytecodeFactorySet = new DefaultJvmBytecodeInterceptorFactorySet(JvmBytecodeInterceptorFactoryProvider.DEFAULT.plus(classLoaderJvmBytecodeInterceptors));
    }

    public static CallSiteDecorator getGroovyCallDecorator(InterceptorsRequest interceptorsRequest) {
        return groovyDecorators.computeIfAbsent(interceptorsRequest, type -> new DefaultCallSiteDecorate(currentGroovyCallInterceptorSet.getCallInterceptors(interceptorsRequest)));
    }

    public static JvmBytecodeInterceptorSet getJvmBytecodeInterceptors(InterceptorsRequest interceptorsRequest) {
        return jvmInterceptorSets.computeIfAbsent(interceptorsRequest, type -> currentJvmBytecodeFactorySet.getJvmBytecodeInterceptorSet(type));
    }

    /**
     * TODO: We can support getting the interceptors in the instrumented code from different locations, not just `Instrumented.currentCallDecorator`,
     *   so that replacing the call interceptors for tests would instead work by embedding a different location
     *   than this one in the instrumented code. Doing that adds much more complexity in the instrumentation.
     */
    @NonNullApi
    @VisibleForTesting
    static class GroovyCallInterceptorInternalTesting {
        static GroovyCallSiteInterceptorSet getCurrentGroovyCallSiteDecorator() {
            return currentGroovyCallInterceptorSet;
        }

        static void setCurrentGroovyCallSiteDecorator(GroovyCallSiteInterceptorSet interceptorsSet) {
            CallInterceptorRegistry.currentGroovyCallInterceptorSet = interceptorsSet;
        }
    }
}
