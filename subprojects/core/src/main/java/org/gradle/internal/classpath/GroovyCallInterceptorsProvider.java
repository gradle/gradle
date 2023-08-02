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
import org.gradle.api.NonNullApi;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.intercept.CallInterceptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@NonNullApi
public interface GroovyCallInterceptorsProvider {

    GroovyCallInterceptorsProvider DEFAULT = new ClassSourceGroovyCallInterceptorsProvider(Instrumented.class.getName());

    List<CallInterceptor> getCallInterceptors();

    default GroovyCallInterceptorsProvider plus(GroovyCallInterceptorsProvider other) {
        return new CompositeGroovyCallInterceptorsProvider(this, other);
    }

    class ClassSourceGroovyCallInterceptorsProvider implements GroovyCallInterceptorsProvider {

        private final String className;

        public ClassSourceGroovyCallInterceptorsProvider(String className) {
            this.className = className;
        }
        @Override
        public List<CallInterceptor> getCallInterceptors() {
            return getInterceptorsFromClass(className);
        }

        private static List<CallInterceptor> getInterceptorsFromClass(String className) {
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
        private static List<CallInterceptor> getInterceptorsFromClass(Class<?> interceptorsProviderClass) {
            Method getCallInterceptors;
            try {
                getCallInterceptors = interceptorsProviderClass.getDeclaredMethod("getCallInterceptors");
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("The provider class does not have the expected getCallInterceptors method", e);
            }

            List<CallInterceptor> result;
            try {
                result = Cast.uncheckedNonnullCast(getCallInterceptors.invoke(null));
            } catch (IllegalAccessException e) {
                throw new IllegalArgumentException("Cannot access the getCallInterceptors method in the provider class", e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }

            return result;
        }
    }

    class ClassLoaderSourceGroovyCallInterceptorsProvider implements GroovyCallInterceptorsProvider {

        private final ClassLoader classLoader;
        private final Predicate<CallInterceptor> filter;

        public ClassLoaderSourceGroovyCallInterceptorsProvider(ClassLoader classLoader) {
            this(classLoader, "org.gradle");
        }

        @VisibleForTesting
        public ClassLoaderSourceGroovyCallInterceptorsProvider(ClassLoader classLoader, String forPackage) {
            this.classLoader = classLoader;
            this.filter = callInterceptor -> callInterceptor.getClass().getPackage().getName().startsWith(forPackage);
        }

        @Override
        public List<CallInterceptor> getCallInterceptors() {
            List<CallInterceptor> interceptors = new ArrayList<>();
            for(CallInterceptor interceptor : ServiceLoader.load(CallInterceptor.class, classLoader)) {
                if (filter.test(interceptor)) {
                    interceptors.add(interceptor);
                }
            }
            return interceptors;
        }
    }

    class CompositeGroovyCallInterceptorsProvider implements GroovyCallInterceptorsProvider {

        private final GroovyCallInterceptorsProvider first;
        private final GroovyCallInterceptorsProvider second;

        private CompositeGroovyCallInterceptorsProvider(GroovyCallInterceptorsProvider first, GroovyCallInterceptorsProvider second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public List<CallInterceptor> getCallInterceptors() {
            return Stream.concat(first.getCallInterceptors().stream(), second.getCallInterceptors().stream()).collect(Collectors.toList());
        }
    }
}
