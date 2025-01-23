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

package org.gradle.internal.instantiation.generator;

import org.gradle.internal.Factory;
import org.gradle.internal.instantiation.generator.annotations.ManagedObjectCreator;
import org.gradle.internal.instantiation.generator.annotations.ManagedObjectProvider;
import org.gradle.internal.service.AnnotatedServiceLifecycleHandler;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Responsible for discovering services that provide Gradle-managed types, and creating instances of those types.
 * <p>
 * Discovers all services annotated with {@link ManagedObjectProvider} and searches its methods for those annotated
 * with {@link ManagedObjectCreator}.
 */
@ServiceScope({Scope.Global.class, Scope.BuildTree.class, Scope.Build.class, Scope.Project.class})
public class ManagedObjectRegistry implements AnnotatedServiceLifecycleHandler {

    private final ManagedObjectRegistry parent;

    private final Map<Class<?>, Factory<?>> noArgFactories = new HashMap<>();
    private final Map<Class<?>, Function<Class<?>, ?>> singleArgFactories = new HashMap<>();
    private final Map<Class<?>, BiFunction<Class<?>, Class<?>, ?>> twoArgFactories = new HashMap<>();

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
        for (Method method : instance.getClass().getMethods()) {
            if (method.isAnnotationPresent(ManagedObjectCreator.class) ||
                doesParentInterfaceMethodHaveAnnotation(instance.getClass(), method, ManagedObjectCreator.class)
            ) {
                if (method.getReturnType() == Void.class) {
                    throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must return a value");
                }

                Class<?>[] parameterTypes = method.getParameterTypes();
                switch (parameterTypes.length) {
                    case 0: registerNoArgFactory(method, instance);
                        break;
                    case 1: registerSingleArgFactory(method, parameterTypes, instance);
                        break;
                    case 2: registerTwoArgFactory(method, parameterTypes, instance);
                        break;
                    default:
                        throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must not have parameters");
                }
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

    private void registerNoArgFactory(Method method, Object instance) {
        noArgFactories.put(method.getReturnType(), () -> {
            try {
                return method.invoke(instance);
            } catch (Exception e) {
                throw new RuntimeException("Could not create managed object", e);
            }
        });
    }

    private void registerSingleArgFactory(Method method, Class<?>[] parameterTypes, Object instance) {
        if (parameterTypes[0] != Class.class) {
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must have a single parameter of type Class");
        }

        singleArgFactories.put(method.getReturnType(), arg1 -> {
            try {
                return method.invoke(instance, arg1);
            } catch (Exception e) {
                throw new RuntimeException("Could not create managed object", e);
            }
        });
    }

    private void registerTwoArgFactory(Method method, Class<?>[] parameterTypes, Object instance) {
        if (parameterTypes[0] != Class.class || parameterTypes[1] != Class.class) {
            throw new IllegalArgumentException("Method " + method + " annotated with @ManagedObjectCreator must have two parameters of type Class");
        }

        twoArgFactories.put(method.getReturnType(), (arg1, arg2) -> {
            try {
                return method.invoke(instance, arg1, arg2);
            } catch (Exception e) {
                throw new RuntimeException("Could not create managed object", e);
            }
        });
    }

    /**
     * Create a managed object instance for a type with no arguments.
     */
    @Nullable
    public <T> T newInstance(Class<T> type) {
        Factory<?> factory = noArgFactories.get(type);

        if (factory != null) {
            return type.cast(factory.create());
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
        Function<Class<?>, ?> factory = singleArgFactories.get(type);

        if (factory != null) {
            return type.cast(factory.apply(arg1));
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
        BiFunction<Class<?>, Class<?>, ?> factory = twoArgFactories.get(type);

        if (factory != null) {
            return type.cast(factory.apply(arg1, arg2));
        }

        if (parent != null) {
            return parent.newInstance(type, arg1, arg2);
        }

        return null;
    }

}
