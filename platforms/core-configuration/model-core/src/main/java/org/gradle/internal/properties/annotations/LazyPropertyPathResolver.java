/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.properties.annotations;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.provider.HasConfigurableValue;
import org.gradle.api.provider.Provider;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.instrumentation.api.annotations.BackedByLazyProperty;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves the path of {@link BackedByLazyProperty} to the lazy property behind an eager getter.
 */
public final class LazyPropertyPathResolver {

    private static final ClassValue<ConcurrentMap<String, Optional<Method>>> GETTERS = new ClassValue<ConcurrentMap<String, Optional<Method>>>() {
        @Override
        protected ConcurrentMap<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private static final ClassValue<Map<String, String>> BACKED_PROPERTIES = new ClassValue<Map<String, String>>() {
        @Override
        protected Map<String, String> computeValue(Class<?> type) {
            Map<String, String> paths = new HashMap<>();
            collectBackedProperties(type, paths, new HashSet<>());
            return ImmutableMap.copyOf(paths);
        }
    };

    private LazyPropertyPathResolver() {
    }

    /**
     * Returns the lazy property that {@code path} points to, starting at {@code owner}.
     *
     * <p>Returns null when a getter on the path is declared {@code @Nullable} and returns null.
     * The eager getter then has no lazy property, as for an object that stores its options in fields.</p>
     *
     * @throws InvalidUserCodeException when the path cannot be resolved
     */
    @Nullable
    public static Object resolve(Object owner, String propertyName, String path) {
        Object current = owner;
        for (String segment : segmentsOf(path)) {
            if (segment.isEmpty()) {
                throw failure(owner.getClass(), propertyName, path, "the path has an empty segment");
            }
            Method getter = findGetter(current.getClass(), segment);
            if (getter == null) {
                throw failure(owner.getClass(), propertyName, path, missingGetter(segment, current.getClass()));
            }
            Object next = invoke(getter, current);
            if (next == null) {
                if (getter.getAnnotatedReturnType().isAnnotationPresent(Nullable.class)) {
                    return null;
                }
                throw failure(owner.getClass(), propertyName, path, "segment '" + segment + "' is null");
            }
            current = next;
        }
        if (!isLazyType(current.getClass())) {
            throw failure(owner.getClass(), propertyName, path, notLazy(current.getClass()));
        }
        return current;
    }

    /**
     * Resolves {@code path} on the declared return types, starting at {@code ownerType}.
     *
     * @return why the path is invalid, or {@code null} when it is valid
     */
    @Nullable
    public static String findStaticProblem(Class<?> ownerType, String path) {
        Class<?> current = ownerType;
        for (String segment : segmentsOf(path)) {
            if (segment.isEmpty()) {
                return "the path has an empty segment";
            }
            Method getter = findGetter(current, segment);
            if (getter == null) {
                return missingGetter(segment, current);
            }
            current = getter.getReturnType();
        }
        return isLazyType(current) ? null : notLazy(current);
    }

    /**
     * Returns the {@link BackedByLazyProperty} path of the getter for {@code propertyName}, searching the class hierarchy,
     * so an override that does not repeat the annotation still finds it.
     */
    @Nullable
    public static String findBackingPath(Class<?> type, String propertyName) {
        return BACKED_PROPERTIES.get(type).get(propertyName);
    }

    private static void collectBackedProperties(@Nullable Class<?> type, Map<String, String> paths, Set<Class<?>> visited) {
        if (type == null || type == Object.class || !visited.add(type)) {
            return;
        }
        for (Method method : type.getDeclaredMethods()) {
            BackedByLazyProperty annotation = method.getDeclaredAnnotation(BackedByLazyProperty.class);
            if (annotation == null) {
                continue;
            }
            PropertyAccessorType accessorType = PropertyAccessorType.of(method);
            if (accessorType == PropertyAccessorType.GET_GETTER || accessorType == PropertyAccessorType.IS_GETTER) {
                // Subclasses are visited first, so the most specific annotation wins
                paths.putIfAbsent(accessorType.propertyNameFor(method), annotation.value());
            }
        }
        collectBackedProperties(type.getSuperclass(), paths, visited);
        for (Class<?> iface : type.getInterfaces()) {
            collectBackedProperties(iface, paths, visited);
        }
    }

    private static String[] segmentsOf(String path) {
        return path.split("\\.", -1);
    }

    private static boolean isLazyType(Class<?> type) {
        return Provider.class.isAssignableFrom(type) || HasConfigurableValue.class.isAssignableFrom(type);
    }

    private static String missingGetter(String segment, Class<?> type) {
        String suffix = capitalize(segment);
        return "segment '" + segment + "' has no getter get" + suffix + "() or is" + suffix + "() on type '" + displayName(type) + "'";
    }

    private static String notLazy(Class<?> type) {
        return "the path ends at type '" + displayName(type) + "', which is not a Provider or a configurable value";
    }

    private static InvalidUserCodeException failure(Class<?> ownerType, String propertyName, String path, String reason) {
        Method getter = findGetter(ownerType, propertyName);
        String getterName = getter != null ? getter.getName() : "get" + capitalize(propertyName);
        return new InvalidUserCodeException(String.format(
            "Cannot resolve @%s(\"%s\") of %s.%s(): %s.",
            BackedByLazyProperty.class.getSimpleName(), path, displayName(ownerType), getterName, reason
        ));
    }

    private static String displayName(Class<?> type) {
        return GeneratedSubclasses.unpack(type).getName();
    }

    @Nullable
    private static Object invoke(Method getter, Object target) {
        try {
            return getter.invoke(target);
        } catch (InvocationTargetException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Nullable
    private static Method findGetter(Class<?> type, String propertyName) {
        return GETTERS.get(type).computeIfAbsent(propertyName, name -> Optional.ofNullable(lookupGetter(type, name))).orElse(null);
    }

    @Nullable
    private static Method lookupGetter(Class<?> type, String propertyName) {
        String suffix = capitalize(propertyName);
        Method getter = lookupMethod(type, "get" + suffix);
        if (getter == null) {
            getter = lookupMethod(type, "is" + suffix);
            if (getter != null && getter.getReturnType() != boolean.class && getter.getReturnType() != Boolean.class) {
                getter = null;
            }
        }
        if (getter != null) {
            makeAccessible(getter);
        }
        return getter;
    }

    // Searches the most specific type first, so a covariant override wins over the method it narrows
    @Nullable
    private static Method lookupMethod(Class<?> type, String name) {
        Queue<Class<?>> interfaces = new ArrayDeque<>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            Method method = declaredGetter(current, name);
            if (method != null) {
                return method;
            }
            for (Class<?> iface : current.getInterfaces()) {
                interfaces.add(iface);
            }
        }
        Set<Class<?>> visited = new HashSet<>();
        while (!interfaces.isEmpty()) {
            Class<?> iface = interfaces.remove();
            if (!visited.add(iface)) {
                continue;
            }
            Method method = declaredGetter(iface, name);
            if (method != null) {
                return method;
            }
            for (Class<?> superInterface : iface.getInterfaces()) {
                interfaces.add(superInterface);
            }
        }
        return null;
    }

    @Nullable
    private static Method declaredGetter(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)
                && method.getParameterCount() == 0
                && !method.isBridge()
                && !Modifier.isStatic(method.getModifiers())
                && method.getReturnType() != void.class) {
                return method;
            }
        }
        return null;
    }

    private static void makeAccessible(Method method) {
        try {
            method.setAccessible(true);
        } catch (RuntimeException e) {
            // A public method stays callable without it
        }
    }

    private static String capitalize(String name) {
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
