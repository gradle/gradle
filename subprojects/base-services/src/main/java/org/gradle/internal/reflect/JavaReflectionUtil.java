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

import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.util.CollectionUtils;
import org.gradle.util.JavaMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Simple implementations of some reflection capabilities. In contrast to org.gradle.util.ReflectionUtil, this class doesn't make use of Groovy.
 */
public class JavaReflectionUtil {
    public static Object readProperty(Object target, String property) {
        try {
            Method getterMethod;
            try {
                getterMethod = target.getClass().getMethod(toMethodName("get", property));
            } catch (NoSuchMethodException e) {
                try {
                    getterMethod = target.getClass().getMethod(toMethodName("is", property));
                } catch (NoSuchMethodException e2) {
                    throw e;
                }
            }
            return getterMethod.invoke(target);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static void writeProperty(Object target, String property, Object value) {
        try {
            String setterName = toMethodName("set", property);
            for (Method method : target.getClass().getMethods()) {
                if (!method.getName().equals(setterName)) {
                    continue;
                }
                if (method.getParameterTypes().length != 1) {
                    continue;
                }
                method.invoke(target, value);
                return;
            }
            throw new NoSuchMethodException(String.format("could not find setter method '%s'", setterName));
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static String toMethodName(String prefix, String propertyName) {
        return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }

    public static Class<?> getWrapperTypeForPrimitiveType(Class<?> type) {
        if (type == Boolean.TYPE) {
            return Boolean.class;
        } else if (type == Long.TYPE) {
            return Long.class;
        } else if (type == Integer.TYPE) {
            return Integer.class;
        } else if (type == Short.TYPE) {
            return Short.class;
        } else if (type == Byte.TYPE) {
            return Byte.class;
        } else if (type == Float.TYPE) {
            return Float.class;
        } else if (type == Double.TYPE) {
            return Double.class;
        }
        throw new IllegalArgumentException(String.format("Don't know how wrapper type for primitive type %s.", type));
    }

    public static <T, R> JavaMethod<T, R> method(Class<T> target, Class<R> returnType, String name, Class<?>... paramTypes) {
        return new JavaMethod<T, R>(target, returnType, name, paramTypes);
    }

    public static <T, R> JavaMethod<T, R> method(Class<T> target, Class<R> returnType, Method method) {
        return new JavaMethod<T, R>(target, returnType, method);
    }

    public static List<Method> findAllMethods(Class<?> target, Spec<Method> predicate) {
        return findAllMethodsInternal(target, predicate, new LinkedList<Method>());
    }

    private static List<Method> findAllMethodsInternal(Class<?> target, Spec<Method> predicate, List<Method> collector) {
        for (final Method method : target.getDeclaredMethods()) {
            Method override = CollectionUtils.findFirst(collector, new Spec<Method>() {
                public boolean isSatisfiedBy(Method potentionOverride) {
                    return potentionOverride.getName().equals(method.getName())
                        && Arrays.equals(potentionOverride.getParameterTypes(), method.getParameterTypes());
                }
            });

            if (override == null && predicate.isSatisfiedBy(method)) {
                collector.add(method);
            }
        }
        Class<?> parent = target.getSuperclass();
        if (parent != null) {
            return findAllMethodsInternal(parent, predicate, collector);
        }

        return collector;
    }

}
