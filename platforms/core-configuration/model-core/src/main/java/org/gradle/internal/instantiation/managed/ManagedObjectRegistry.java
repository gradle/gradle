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
import org.gradle.internal.service.AnnotatedServiceLifecycleHandler;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Responsible for discovering services that provide Gradle-managed types, and creating instances of those types.
 * <p>
 * Discovers all services annotated with {@link ManagedObjectProvider} and searches its methods for those annotated
 * with {@link ManagedObjectCreator}.
 */
@ServiceScope({Scope.Global.class, Scope.BuildTree.class, Scope.Build.class, Scope.Project.class})
public class ManagedObjectRegistry implements AnnotatedServiceLifecycleHandler {

    private final @Nullable ManagedObjectRegistry parent;
    private final Map<Class<?>, MethodHandle> factories = new ConcurrentHashMap<>();

    public ManagedObjectRegistry(@Nullable ManagedObjectRegistry parent) {
        this.parent = parent;
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

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method method : instance.getClass().getMethods()) {
            if (method.isAnnotationPresent(ManagedObjectCreator.class) ||
                doesParentInterfaceMethodHaveAnnotation(instance.getClass(), method, ManagedObjectCreator.class)
            ) {
                MethodHandle handle;
                try {
                     handle = lookup.unreflect(method);
                } catch (IllegalAccessException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }

                validateMethod(method, handle);
                registerFactory(handle, instance);
            }
        }
    }

    public static boolean doesParentInterfaceMethodHaveAnnotation(
        Class<?> clazz,
        Method targetMethod,
        Class<? extends Annotation> annotation
    ) {
        for (Class<?> parent : clazz.getInterfaces()) {
            Method interfaceMethod = null;
            try {
                interfaceMethod = parent.getMethod(targetMethod.getName(), targetMethod.getParameterTypes());
            } catch (NoSuchMethodException e) {
                // Method not found in this interface, continue checking others
            }
            if (interfaceMethod != null && interfaceMethod.isAnnotationPresent(annotation)) {
                return true;
            }
        }

        return false;
    }

    private void registerFactory(MethodHandle handle, Object instance) {
        MethodType type = handle.type();
        Class<?> returnType = type.returnType();
        MethodHandle boundHandle = handle.bindTo(instance);
        MethodHandle existing = factories.put(returnType, boundHandle);

        if (existing != null) {
            // Class#getMethods does not have a consistent order.
            // For consistency in tests, we sort the method handles in the error message.
            List<String> sortedMethods = Stream.of(existing, boundHandle)
                .map(MethodHandle::toString)
                .sorted()
                .collect(Collectors.toList());

            throw new IllegalArgumentException("Method " + sortedMethods.get(0) + " for type " + returnType + "conflicts with existing factory method " + sortedMethods.get(1) + ".");
        }
    }

    private static void validateMethod(Method method, MethodHandle handle) {
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must not be static.");
        }

        MethodType type = handle.type();

        if (type.returnType() == void.class) {
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must return a value.");
        }

        // The 0th parameter is the `this` receiver parameter.
        int actualParameterCount = type.parameterCount() - 1;

        if (actualParameterCount > 2) {
            // We only support max 2 arg factories.
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator has too many parameters.");
        }

        for (int i = 0; i < actualParameterCount; i++) {
            Class<?> parameterType = type.parameterType(i + 1);
            if (parameterType != Class.class) {
                throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must have parameters of type Class, but has parameter of type " + parameterType + ".");
            }
        }
    }

    /**
     * Create a managed object instance for a type with no arguments.
     */
    @Nullable
    public <T> T newInstance(Class<T> type) {
        MethodHandle factory = factories.get(type);

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

    /**
     * Create a managed object instance for a type with a type argument.
     */
    @Nullable
    public <T> T newInstance(Class<T> type, Class<?> arg1) {
        MethodHandle factory = factories.get(type);

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

    /**
     * Create a managed object instance for a type with two type arguments.
     */
    @Nullable
    public <T> T newInstance(Class<T> type, Class<?> arg1, Class<?> arg2) {
        MethodHandle factory = factories.get(type);

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

}
