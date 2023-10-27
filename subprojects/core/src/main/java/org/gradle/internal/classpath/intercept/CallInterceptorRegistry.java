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
import org.gradle.internal.classpath.JvmBytecodeInterceptorSet;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

@NonNullApi
public class CallInterceptorRegistry {
    private static final Map<ClassLoader, Boolean> LOADED_FROM_CLASSLOADERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile CallSiteDecorator currentGroovyCallDecorator = new CallInterceptorsSet(GroovyCallInterceptorsProvider.DEFAULT);
    private static volatile JvmBytecodeInterceptorSet currentJvmBytecodeInterceptors = JvmBytecodeInterceptorSet.DEFAULT;

    public synchronized static void loadCallInterceptors(ClassLoader classLoader) {
        if (LOADED_FROM_CLASSLOADERS.put(classLoader, true) != null) {
            throw new RuntimeException("Cannot load interceptors twice for class loader: " + classLoader);
        }

        GroovyCallInterceptorsProvider classLoaderGroovyCallInterceptors = new GroovyCallInterceptorsProvider.ClassLoaderSourceGroovyCallInterceptorsProvider(classLoader);
        GroovyCallInterceptorsProvider callInterceptors = GroovyCallInterceptorsProvider.DEFAULT.plus(classLoaderGroovyCallInterceptors);
        currentGroovyCallDecorator = new CallInterceptorsSet(callInterceptors);
        JvmBytecodeInterceptorSet.ClassLoaderSourceJvmBytecodeInterceptorSet classLoaderJvmBytecodeInterceptors = new JvmBytecodeInterceptorSet.ClassLoaderSourceJvmBytecodeInterceptorSet(classLoader);
        currentJvmBytecodeInterceptors = JvmBytecodeInterceptorSet.DEFAULT.plus(classLoaderJvmBytecodeInterceptors);
    }

    public static CallSiteDecorator getGroovyCallDecorator() {
        return currentGroovyCallDecorator;
    }

    public static JvmBytecodeInterceptorSet getJvmBytecodeInterceptors() {
        return currentJvmBytecodeInterceptors;
    }

    /**
     * TODO: We can support getting the interceptors in the instrumented code from different locations, not just `Instrumented.currentCallDecorator`,
     *   so that replacing the call interceptors for tests would instead work by embedding a different location
     *   than this one in the instrumented code. Doing that adds much more complexity in the instrumentation.
     */
    @NonNullApi
    @VisibleForTesting
    static class GroovyCallInterceptorInternalTesting {
        static CallSiteDecorator getCurrentGroovyCallSiteDecorator() {
            return getGroovyCallDecorator();
        }

        static void setCurrentGroovyCallSiteDecorator(CallSiteDecorator interceptorsSet) {
            CallInterceptorRegistry.currentGroovyCallDecorator = interceptorsSet;
        }
    }
}
