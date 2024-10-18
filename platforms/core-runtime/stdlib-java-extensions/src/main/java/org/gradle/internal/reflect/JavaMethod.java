/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.internal.reflect;

import org.gradle.api.GradleException;
import org.gradle.internal.UncheckedException;
import org.gradle.util.internal.CollectionUtils;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class JavaMethod<T, R> {
    private final Method method;
    private final Class<R> returnType;

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> of(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return new JavaMethod<T, R>(target, returnType, name, paramTypes);
    }

    /**
     * Locates the given static method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> ofStatic(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        return new JavaMethod<T, R>(target, returnType, name, true, paramTypes);
    }

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> of(T target, Class<R> returnType, String name, Class<?>... paramTypes) throws NoSuchMethodException {
        @SuppressWarnings("unchecked")
        Class<T> targetClass = (Class<T>) target.getClass();
        return of(targetClass, returnType, name, paramTypes);
    }

    /**
     * Locates the given method. Searches all methods, including private methods.
     */
    public static <T, R> JavaMethod<T, R> of(Class<R> returnType, Method method) throws NoSuchMethodException {
        return new JavaMethod<T, R>(returnType, method);
    }

    public JavaMethod(Class<T> target, Class<R> returnType, String name, boolean allowStatic, Class<?>... paramTypes) {
        this(returnType, findMethod(target, name, allowStatic, paramTypes));
    }

    public JavaMethod(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) {
        this(target, returnType, name, false, paramTypes);
    }

    public JavaMethod(Class<R> returnType, Method method) {
        this.returnType = returnType;
        this.method = method;
        method.setAccessible(true);
    }

    private static Method findMethod(Class<?> target, String name, boolean allowStatic, Class<?>[] paramTypes) {
        // First try to find a method from all public methods
        Method method = findMethodFrom(target.getMethods(), name, allowStatic, paramTypes);
        if (method == null) {
            // Else search declared methods recursively
            method = findDeclaredMethod(target, name, allowStatic, paramTypes);
        }
        if (method != null) {
            return method;
        }
        throw new NoSuchMethodException(String.format("Could not find method %s(%s) on %s.", name, CollectionUtils.join(", ", paramTypes), target.getSimpleName()));
    }

    @Nullable
    private static Method findDeclaredMethod(Class<?> origTarget, String name, boolean allowStatic, Class<?>[] paramTypes) {
        Class<?> target = origTarget;
        while (target != null) {
            Method method = findMethodFrom(target.getDeclaredMethods(), name, allowStatic, paramTypes);
            if (method != null) {
                return method;
            }
            target = target.getSuperclass();
        }
        return null;
    }

    @Nullable
    private static Method findMethodFrom(Method[] methods, String name, boolean allowStatic, Class<?>[] paramTypes) {
        for (Method method : methods) {
            if (!allowStatic && Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (method.getName().equals(name) && Arrays.equals(method.getParameterTypes(), paramTypes)) {
                return method;
            }
        }
        return null;
    }

    public boolean isStatic() {
        return Modifier.isStatic(method.getModifiers());
    }

    public R invokeStatic(@Nullable Object... args) {
        return invoke(null, args);
    }

    public R invoke(@Nullable T target, @Nullable Object... args) {
        try {
            Object result = method.invoke(target, args);
            return returnType.cast(result);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw UncheckedException.throwAsUncheckedException(cause != null ? cause : e);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not call %s.%s() on %s", method.getDeclaringClass().getSimpleName(), method.getName(), target), e);
        }
    }

    public Method getMethod() {
        return method;
    }

    public Class<?>[] getParameterTypes() {
        return method.getParameterTypes();
    }

    @Override
    public String toString() {
        return method.toString();
    }
}
