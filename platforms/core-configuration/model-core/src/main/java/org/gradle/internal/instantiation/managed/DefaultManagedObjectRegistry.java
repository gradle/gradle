/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.instantiation.managed;

import org.gradle.internal.UncheckedException;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Default implementation of {@link ManagedObjectRegistry}.
 * <p>
 * Responsible for discovering services that provide Gradle-managed types, and creating instances of those types.
 * <p>
 * Discovers all services annotated with {@link ManagedObjectProvider} and searches its methods for those annotated
 * with {@link ManagedObjectCreator}.
 */
public class DefaultManagedObjectRegistry implements ManagedObjectRegistry {

    private final @Nullable ManagedObjectRegistry parent;
    private final ReflectionCache reflectionCache;
    private final ConcurrentMap<Class<?>, MethodHandle> factoryByPublicType = new ConcurrentHashMap<>();

    public DefaultManagedObjectRegistry() {
        this(null, new ReflectionCache());
    }

    private DefaultManagedObjectRegistry(
        @Nullable ManagedObjectRegistry parent,
        ReflectionCache reflectionCache
    ) {
        this.parent = parent;
        this.reflectionCache = reflectionCache;
    }

    @Override
    public ManagedObjectRegistry createChild() {
        return new DefaultManagedObjectRegistry(this, reflectionCache);
    }

    @Override
    public List<Class<? extends Annotation>> getAnnotations() {
        return Collections.singletonList(ManagedObjectProvider.class);
    }

    @Nullable
    @Override
    public Class<? extends Annotation> getImplicitAnnotation() {
        return null;
    }

    @Override
    public void whenRegistered(Class<? extends Annotation> annotation, Registration registration) {
        assert annotation == ManagedObjectProvider.class;

        Object instance = registration.getInstance();

        for (Class<?> declaredType : registration.getDeclaredTypes()) {
            if (declaredType.isAnnotationPresent(ManagedObjectProvider.class)) {
                boolean registeredCreator = false;
                for (Method declaredMethod : declaredType.getMethods()) {
                    Class<?> publicType = findCreatorAnnotation(declaredMethod);
                    if (publicType != null) {
                        MethodHandle handle = getHandleForInstance(declaredMethod, instance);
                        registerFactory(publicType, handle);

                        registeredCreator = true;
                    }
                }
                if (!registeredCreator) {
                    throw new IllegalArgumentException("Service " + declaredType + " annotated with @ManagedObjectProvider must have at least one method annotated with @ManagedObjectCreator.");
                }
            }
        }
    }

    /**
     * Determine if this method is a factory method, returning the public type of the
     * object that will be created, or null if it is not a factory method.
     * <p>
     * The returned type is the type that we expect users to request when creating an
     * instance of the managed object.
     */
    @Nullable
    private static Class<?> findCreatorAnnotation(Method declaredMethod) {
        ManagedObjectCreator creatorAnnotation = declaredMethod.getAnnotation(ManagedObjectCreator.class);
        if (creatorAnnotation == null) {
            // This method is not a factory method.
            return null;
        }

        if (creatorAnnotation.publicType() != void.class) {
            return creatorAnnotation.publicType();
        }

        return declaredMethod.getReturnType();
    }

    /**
     * Get a {@link MethodHandle} which when invoked will execute the given method on the given instance.
     */
    private MethodHandle getHandleForInstance(Method declaredMethod, Object instance) {
        Method instanceMethod = reflectionCache.getMethod(
            instance.getClass(),
            declaredMethod.getName(),
            declaredMethod.getParameterTypes()
        );

        if (Modifier.isStatic(instanceMethod.getModifiers())) {
            throw new IllegalArgumentException("Method " + instanceMethod + " annotated with @ManagedObjectCreator must not be static.");
        }

        MethodHandle handle = reflectionCache.unreflect(instanceMethod).bindTo(instance);
        validateFactoryMethod(instanceMethod, handle);
        return handle;
    }

    private static void validateFactoryMethod(Method method, MethodHandle handle) {
        MethodType type = handle.type();

        Class<?> returnType = type.returnType();
        if (returnType == void.class) {
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must return a value.");
        }

        if (type.parameterCount() > 2) {
            // We only support max 2 arg factories.
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator has too many parameters.");
        }

        for (Class<?> parameterType : type.parameterArray()) {
            if (parameterType != Class.class) {
                throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must have parameters of type Class, but has parameter of type " + parameterType + ".");
            }
        }
    }

    private void registerFactory(Class<?> publicType, MethodHandle handle) {
        MethodHandle existing = factoryByPublicType.put(publicType, handle);

        if (existing != null) {
            // Class#getMethods does not have a consistent order.
            // For consistency in tests, we sort the method handles in the error message.
            List<String> sortedMethods = Stream.of(existing, handle)
                .map(MethodHandle::toString)
                .sorted()
                .collect(Collectors.toList());

            throw new IllegalArgumentException("Method " + sortedMethods.get(0) + " for type " + publicType + "conflicts with existing factory method " + sortedMethods.get(1) + ".");
        }
    }

    @Override
    @Nullable
    public <T> T newInstance(Class<T> type) {
        MethodHandle factory = factoryByPublicType.get(type);

        if (factory != null) {
            try {
                Object result = factory.invoke();
                return type.cast(result);
            } catch (Throwable e) {
                throw new RuntimeException("Could not create managed object.", e);
            }
        }

        if (parent != null) {
            return parent.newInstance(type);
        }

        return null;
    }

    @Override
    @Nullable
    public <T> T newInstance(Class<T> type, Class<?> arg1) {
        MethodHandle factory = factoryByPublicType.get(type);

        if (factory != null) {
            try {
                Object result = factory.invoke(arg1);
                return type.cast(result);
            } catch (Throwable e) {
                throw new RuntimeException("Could not create managed object.", e);
            }
        }

        if (parent != null) {
            return parent.newInstance(type, arg1);
        }

        return null;
    }

    @Override
    @Nullable
    public <T> T newInstance(Class<T> type, Class<?> arg1, Class<?> arg2) {
        MethodHandle factory = factoryByPublicType.get(type);

        if (factory != null) {
            try {
                Object result = factory.invoke(arg1, arg2);
                return type.cast(result);
            } catch (Throwable e) {
                throw new RuntimeException("Could not create managed object.", e);
            }
        }

        if (parent != null) {
            return parent.newInstance(type, arg1, arg2);
        }

        return null;
    }

    /**
     * A cache shared by all registries in a hierarchy, to avoid recomputing expensive reflection
     * operations on the same types.
     * <p>
     * Since we create a managed object registry for each service scope instance, we very often
     * need to unreflect the same methods multiple times (e.g. multiple project service registry
     * instances all offer the same managed types).
     */
    private static class ReflectionCache {

        private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();

        private final ConcurrentMap<Method, MethodHandle> unreflectionCache = new ConcurrentHashMap<>();
        private final ConcurrentMap<GetMethodKey, Method> findMethodCache = new ConcurrentHashMap<>();

        /**
         * Unreflect the given method, caching the result.
         *
         * @throws IllegalArgumentException if the method is not public or its declaring class is not public.
         */
        public MethodHandle unreflect(Method method) {
            MethodHandle handle = unreflectionCache.get(method);
            if (handle != null) {
                return handle;
            }

            if (!Modifier.isPublic(method.getModifiers())) {
                throw new IllegalArgumentException("Method " + method + " is not public.");
            } else if (!Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
                throw new IllegalArgumentException("Declaring class '" + method.getDeclaringClass().getName() + "' of method " + method + " is not public.");
            }

            return unreflectionCache.computeIfAbsent(method, m -> {
                try {
                    return LOOKUP.unreflect(m);
                } catch (IllegalAccessException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            });
        }

        /**
         * Get the method in the given class with the given signature.
         *
         * @throws RuntimeException if the method cannot be found.
         */
        public Method getMethod(Class<?> targetClass, String methodName, Class<?>[] parameterTypes) {
            GetMethodKey key = new GetMethodKey(targetClass, methodName, parameterTypes);
            Method method = findMethodCache.get(key);
            if (method != null) {
                return method;
            }

            return findMethodCache.computeIfAbsent(key, k -> {
                try {
                    return targetClass.getMethod(k.methodName, k.parameterTypes);
                } catch (NoSuchMethodException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            });
        }

        private static final class GetMethodKey {

            private final Class<?> targetClass;
            private final String methodName;
            private final Class<?>[] parameterTypes;

            private final int hashCode;

            public GetMethodKey(Class<?> targetClass, String methodName, Class<?>[] parameterTypes) {
                this.targetClass = targetClass;
                this.methodName = methodName;
                this.parameterTypes = parameterTypes;
                this.hashCode = computeHashCode(targetClass, methodName, parameterTypes);
            }

            @Override
            public boolean equals(Object o) {
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }

                GetMethodKey that = (GetMethodKey) o;
                return targetClass.equals(that.targetClass) &&
                    methodName.equals(that.methodName) &&
                    Arrays.equals(parameterTypes, that.parameterTypes);
            }

            @Override
            public int hashCode() {
                return hashCode;
            }

            private static int computeHashCode(
                Class<?> declaringClass,
                String methodName,
                Class<?>[] parameterTypes
            ) {
                int result = declaringClass.hashCode();
                result = 31 * result + methodName.hashCode();
                result = 31 * result + Arrays.hashCode(parameterTypes);
                return result;
            }

        }

    }

}
