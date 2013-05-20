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

import org.gradle.internal.UncheckedException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Simple implementations of some reflection capabilities. In contrast to org.gradle.util.ReflectionUtil,
 * this class doesn't make use of Groovy.
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
            for (Method method: target.getClass().getMethods()) {
                if (!method.getName().equals(setterName)) { continue; }
                if (method.getParameterTypes().length != 1) { continue; }
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

    public static Object invokeMethodWrapException(Object target, String name) {
        return invokeMethodWrapException(target, name, new Object[0]);
    }

    public static Object invokeMethodWrapException(Object target, String name, Object... args) {
        try {
            return invokeMethod(target, name, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object invokeMethodWrapException(Object target, String name, Class<?>[] argTypes, Object... args) {
        try {
            return invokeMethod(target, name, argTypes, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Object invokeMethod(Object target, String name) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return invokeMethod(target, name, new Object[0]);
    }
    public static Object invokeMethod(Object target, String name, Object... args) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Class<?>[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }

        return invokeMethod(target, name, argTypes, args);
    }

    public static Object invokeMethod(Object target, String name, Class<?>[] argTypes, Object... args) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        return target.getClass().getMethod(name, argTypes).invoke(target, args);
    }

}
