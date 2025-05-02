/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.Cast;
import org.gradle.internal.instrumentation.api.groovybytecode.FilterableCallInterceptor;
import org.gradle.internal.lazy.Lazy;
import org.jspecify.annotations.NullMarked;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NullMarked
public interface GroovyCallInterceptorsProvider {

    @SuppressWarnings("ClassInitializationDeadlock")
    GroovyCallInterceptorsProvider DEFAULT = new ClassSourceGroovyCallInterceptorsProvider(Instrumented.class.getName());

    List<FilterableCallInterceptor> getCallInterceptors();

    default GroovyCallInterceptorsProvider plus(GroovyCallInterceptorsProvider other) {
        return new CompositeGroovyCallInterceptorsProvider(this, other);
    }

    @NullMarked
    class ClassLoaderSourceGroovyCallInterceptorsProvider implements GroovyCallInterceptorsProvider {

        private final Lazy<List<FilterableCallInterceptor>> interceptors;

        public ClassLoaderSourceGroovyCallInterceptorsProvider(ClassLoader classLoader) {
            this(classLoader, "");
        }

        @VisibleForTesting
        public ClassLoaderSourceGroovyCallInterceptorsProvider(ClassLoader classLoader, String forPackage) {
            this.interceptors = Lazy.locking().of(() -> getInterceptorsFromClassLoader(classLoader, forPackage));
        }

        private static List<FilterableCallInterceptor> getInterceptorsFromClassLoader(ClassLoader classLoader, String forPackage) {
            ImmutableList.Builder<FilterableCallInterceptor> interceptors = ImmutableList.builder();
            for(FilterableCallInterceptor interceptor : ServiceLoader.load(FilterableCallInterceptor.class, classLoader)) {
                if (interceptor.getClass().getPackage().getName().startsWith(forPackage)) {
                    interceptors.add(interceptor);
                }
            }
            return interceptors.build();
        }

        @Override
        public List<FilterableCallInterceptor> getCallInterceptors() {
            return interceptors.get();
        }
    }

    /**
     * Use {@link ClassLoaderSourceGroovyCallInterceptorsProvider} instead that loads classes via SPI.
     * Kept to support old case where we loaded a class directly.
     */
    @NullMarked
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed")
    class ClassSourceGroovyCallInterceptorsProvider implements GroovyCallInterceptorsProvider {

        private final Lazy<List<FilterableCallInterceptor>> interceptors;

        public ClassSourceGroovyCallInterceptorsProvider(String className) {
            this.interceptors = Lazy.locking().of(() -> getInterceptorsFromClass(className));
        }

        @Override
        public List<FilterableCallInterceptor> getCallInterceptors() {
            return interceptors.get();
        }

        private static List<FilterableCallInterceptor> getInterceptorsFromClass(String className) {
            try {
                return getInterceptorsFromClass(Class.forName(className));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @param interceptorsProviderClass the class providing the Groovy call interceptors.
         * It must have a method that follows the pattern: {@code public static List<CallInterceptor> getCallInterceptors()}
         */
        @SuppressWarnings("unchecked")
        private static List<FilterableCallInterceptor> getInterceptorsFromClass(Class<?> interceptorsProviderClass) {
            Method getCallInterceptors;
            try {
                getCallInterceptors = interceptorsProviderClass.getDeclaredMethod("getCallInterceptors");
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("The provider class does not have the expected getCallInterceptors method", e);
            }

            try {
                return ImmutableList.copyOf((List<FilterableCallInterceptor>) Cast.uncheckedNonnullCast(getCallInterceptors.invoke(null)));
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Cannot access the getCallInterceptors method in the provider class", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NullMarked
    class CompositeGroovyCallInterceptorsProvider implements GroovyCallInterceptorsProvider {

        private final GroovyCallInterceptorsProvider first;
        private final GroovyCallInterceptorsProvider second;

        private CompositeGroovyCallInterceptorsProvider(GroovyCallInterceptorsProvider first, GroovyCallInterceptorsProvider second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public List<FilterableCallInterceptor> getCallInterceptors() {
            return Stream.concat(first.getCallInterceptors().stream(), second.getCallInterceptors().stream()).collect(Collectors.toList());
        }
    }
}
