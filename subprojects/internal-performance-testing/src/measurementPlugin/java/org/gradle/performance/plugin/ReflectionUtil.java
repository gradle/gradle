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
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

class ReflectionUtil {
    public static Class<?> loadClassIfAvailable(String className) {
        try {
            return ReflectionUtil.class.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

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
        final Method method = doFindMethod(clazz, name, parameterTypes);
        final Method publicMethod = findPublicMethodInPublicClassOrInterface(method);
        return publicMethod != null ? publicMethod : method;
    }

    private static Method doFindMethod(Class<?> clazz, String name, Class<?>[] parameterTypes) {
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

    // required in some cases to find correct method to dispatch to
    // it's not possible to call public methods on private classes directly
    private static Method findPublicMethodInPublicClassOrInterface(Method method) {
        if (method == null || !isPublic(method)) {
            return null;
        }
        Class<?> clazz = method.getDeclaringClass();
        if (isPublic(clazz)) {
            return method;
        }

        Set<Class<?>> allInterfaces = new LinkedHashSet<Class<?>>();

        while (clazz != null) {
            if (isPublic(clazz)) {
                try {
                    return clazz.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }
            collectInterfaces(allInterfaces, clazz);
            clazz = clazz.getSuperclass();
        }

        for (Class<?> ifc : allInterfaces) {
            if (isPublic(ifc)) {
                try {
                    return ifc.getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    // ignore
                }
            }
        }

        return null;
    }

    private static boolean isPublic(Member m) {
        return (m.getModifiers() & Modifier.PUBLIC) != 0;
    }

    private static boolean isPublic(Class cl) {
        return (cl.getModifiers() & Modifier.PUBLIC) != 0;
    }

    private static void collectInterfaces(Set<Class<?>> allInterfaces, Class clazz) {
        for (Class<?> ifc : clazz.getInterfaces()) {
            allInterfaces.add(ifc);
            collectInterfaces(allInterfaces, ifc);
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
