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

        boolean registeredCreator = false;
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (Method method : instance.getClass().getMethods()) {
            ManagedObjectCreator creatorAnnotation = getCreatorAnnotation(method, instance);
            if (creatorAnnotation != null) {
                MethodHandle handle = getBoundMethodHandle(method, lookup, instance);
                registerFactory(creatorAnnotation, handle);
                registeredCreator = true;
            }
        }

        if (!registeredCreator) {
            throw new IllegalArgumentException("Service " + instance.getClass() + " annotated with @ManagedObjectProvider must have at least one method annotated with @ManagedObjectCreator.");
        }
    }

    private static MethodHandle getBoundMethodHandle(Method method, MethodHandles.Lookup lookup, Object instance) {
        MethodHandle handle;
        try {
             handle = lookup.unreflect(method);
        } catch (IllegalAccessException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must not be static.");
        }

        MethodHandle boundHandle = handle.bindTo(instance);
        validateFactoryMethod(method, boundHandle);
        return boundHandle;
    }

    @Nullable
    private static ManagedObjectCreator getCreatorAnnotation(Method method, Object instance) {
        ManagedObjectCreator directAnnotation = method.getAnnotation(ManagedObjectCreator.class);
        if (directAnnotation != null) {
            return directAnnotation;
        }
        return getParentInterfaceCreatorAnnotation(instance.getClass(), method, ManagedObjectCreator.class);
    }

    @Nullable
    public static <T extends Annotation> T getParentInterfaceCreatorAnnotation(
        Class<?> clazz,
        Method targetMethod,
        Class<T> annotation
    ) {
        for (Class<?> parent : clazz.getInterfaces()) {
            Method interfaceMethod = null;
            try {
                interfaceMethod = parent.getMethod(targetMethod.getName(), targetMethod.getParameterTypes());
            } catch (NoSuchMethodException e) {
                // Method not found in this interface, continue checking others
            }
            if (interfaceMethod != null) {
                T annotationInstance = interfaceMethod.getAnnotation(annotation);
                if (annotationInstance != null) {
                    return annotationInstance;
                }
            }
        }

        return null;
    }

    private void registerFactory(ManagedObjectCreator creatorAnnotation, MethodHandle handle) {
        // Use the return type of the method as the public type, unless otherwise specified.
        Class<?> publicType = creatorAnnotation.publicType() == void.class
            ? handle.type().returnType()
            : creatorAnnotation.publicType();

        MethodHandle existing = factories.put(publicType, handle);

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

    /**
     * Create a managed object instance for a type with no arguments.
     *
     * @return null if no zero-argument factory is registered for the type.
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
     *
     * @return null if no single-argument factory is registered for the type.
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
     *
     * @return null if no two-argument factory is registered for the type.
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
