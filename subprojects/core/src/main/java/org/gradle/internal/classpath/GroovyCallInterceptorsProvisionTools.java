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

import org.gradle.api.NonNullApi;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.intercept.CallInterceptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

@NonNullApi
public class GroovyCallInterceptorsProvisionTools {
    private GroovyCallInterceptorsProvisionTools() {
    }

    static List<CallInterceptor> getInterceptorsFromProvider(GroovyCallInterceptorsProvider provider) {
        return provider.getInterceptorProviderClassNames().stream().flatMap(it -> getInterceptorsFromClass(it).stream()).collect(Collectors.toList());
    }

    /**
     * @param className the class name for the class that provides the call interceptors, as per {@link Class#getName}
     */
    static List<CallInterceptor> getInterceptorsFromClass(String className) {
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
    static List<CallInterceptor> getInterceptorsFromClass(Class<?> interceptorsProviderClass) {
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
