/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

class ReflectionUtil {
    public static Method getMethodByName(Class<?> clazz, String name) {
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(name)) {
                return method;
            }
        }
        return null;
    }

    public static Method getMethodBySignature(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    public static Method findMethodByName(Class<?> clazz, String name) {
        return findMethod(clazz, name, (Class<?>[]) null);
    }

    public static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        Class<?> searchType = clazz;
        while (searchType != null) {
            Method[] methods = searchType.isInterface() ? searchType.getMethods() : searchType.getDeclaredMethods();
            for (Method method : methods) {
                if (name.equals(method.getName())
                    && (parameterTypes == null || Arrays.equals(parameterTypes, method.getParameterTypes()))) {
                    return method;
                }
            }
            searchType = searchType.getSuperclass();
        }
        return null;
    }


    public static Object invokeMethod(Object target, Method method, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            sneakyThrow(e);
            return null;
        } catch (InvocationTargetException e) {
            sneakyThrow(e.getCause());
            return null;
        }
    }

    private static void sneakyThrow(Throwable t) {
        ReflectionUtil.<RuntimeException>doSneakyThrow(t);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void doSneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
