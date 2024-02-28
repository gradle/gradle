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

package org.gradle.api.internal.tasks.testing.failure;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.testing.TestFailure;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * A mapper that maps a {@link Throwable} thrown during test execution into a {@link TestFailure}.
 * <p>
 * Implementors of this class should not depend on classes outside the standard library, as there is no guarantee that they will be on the test VM's classpath.
 * Instead, they should rely completely on reflection.
 */
@NonNullApi
public abstract class TestFailureMapper {

    /**
     * Decides whether this mapper supports the given {@link Throwable}.
     * A {@link Throwable} is supported if its class name, or any of its superclasses, is present in the list returned by {@link #getSupportedClassNames()}.
     * <p>
     * This method does the check purely by reflective means, and don't need the checked class to be on the classpath.
     *
     * @param cls the {@link Class} to checked
     * @return {@code true} if cls or one of its superclasses is contained in the list returned by {@link #getSupportedClassNames()}, {@code false} otherwise
     */
    public boolean supports(Class<?> cls) {
        if (getSupportedClassNames().contains(cls.getName())) {
            return true;
        }

        Class<?> superclass = cls.getSuperclass();
        if (superclass == null) {
            return false;
        } else {
            return supports(superclass);
        }
    }

    /**
     * Returns a list of fully qualified class names that this mapper supports.
     * <p>
     * See {@link #supports(Class)} for more information.
     *
     * @return a list of fully qualified class names that this mapper supports
     */
    protected abstract List<String> getSupportedClassNames();

    /**
     * Maps the given {@link Throwable} to a {@link TestFailure}.
     * <p>
     * Implementors of this method should not depend on classes outside the standard library, as there is no guarantee that they are on the JVM's classpath.
     * <p>
     * If needed, {@code rootMapper} can be used to recursively map inner failures by platform-specific means.
     *
     * @param throwable the {@link Throwable} to be mapped
     * @param rootMapper the {@link ThrowableToTestFailureMapper} to be used to recursively map inner failures.
     * @return a {@link TestFailure} representing the given {@link Throwable}
     * @throws Exception if an error occurs while mapping the {@link Throwable}
     */
    public abstract TestFailure map(Throwable throwable, ThrowableToTestFailureMapper rootMapper) throws Exception;

    /**
     * Utility method to invoke a method on an object by reflective means.
     */
    @Nullable
    protected static <T> T invokeMethod(@Nullable Object obj, String methodName, Class<T> targetClass) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (obj == null) {
            return null;
        }

        Method method = obj.getClass().getMethod(methodName);
        Object result = method.invoke(obj);
        if (result == null) {
            return null;
        } else {
            return targetClass.cast(result);
        }
    }

    /**
     * Utility method to invoke a method on an object by reflective means.
     *
     * @return the result of the method invocation as an {@link Object}
     * @see #invokeMethod(Object, String, Class) for the generic version of this method
     */
    @Nullable
    protected static Object invokeMethod(@Nullable Object obj, String methodName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        return invokeMethod(obj, methodName, Object.class);
    }
}
