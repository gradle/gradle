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

package org.gradle.api.internal;

import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.ObjectInstantiationException;
import org.gradle.internal.service.ServiceRegistry;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * An {@link Instantiator} that applies JSR-330 style dependency injection.
 */
public class DependencyInjectingInstantiator implements Instantiator {
    private final ServiceRegistry services;

    public DependencyInjectingInstantiator(ServiceRegistry services) {
        this.services = services;
    }

    public <T> T newInstance(Class<? extends T> type, Object... parameters) {
        try {
            validateType(type);
            Constructor<?> constructor = selectConstructor(type);
            constructor.setAccessible(true);
            Object[] resolvedParameters = convertParameters(type, constructor, parameters);
            try {
                return type.cast(constructor.newInstance(resolvedParameters));
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        } catch (Throwable e) {
            throw new ObjectInstantiationException(type, e);
        }
    }

    private <T> Object[] convertParameters(Class<T> type, Constructor<?> match, Object[] parameters) {
        Class<?>[] parameterTypes = match.getParameterTypes();
        if (parameterTypes.length < parameters.length) {
            throw new IllegalArgumentException(String.format("Too many parameters provided for constructor for class %s. Expected %s, received %s.", type.getName(), parameterTypes.length, parameters.length));
        }
        Object[] resolvedParameters = new Object[parameterTypes.length];
        int pos = 0;
        for (int i = 0; i < resolvedParameters.length; i++) {
            Class<?> targetType = parameterTypes[i];
            if (targetType.isPrimitive()) {
                targetType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(targetType);
            }
            if (pos < parameters.length && targetType.isInstance(parameters[pos])) {
                resolvedParameters[i] = parameters[pos];
                pos++;
            } else {
                resolvedParameters[i] = services.get(match.getGenericParameterTypes()[i]);
            }
        }
        if (pos != parameters.length) {
            throw new IllegalArgumentException(String.format("Unexpected parameter provided for constructor for class %s.", type.getName()));
        }
        return resolvedParameters;
    }

    private <T> Constructor<?> selectConstructor(Class<T> type) {
        Constructor<?>[] constructors = type.getDeclaredConstructors();

        if (constructors.length == 1) {
            Constructor<?> constructor = constructors[0];
            if (constructor.getParameterTypes().length == 0 && isPublicOrPackageScoped(type, constructor)) {
                return constructor;
            }
        }

        List<Constructor<?>> injectConstructors = new ArrayList<Constructor<?>>();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getAnnotation(Inject.class) != null) {
                injectConstructors.add(constructor);
            }
        }

        if (injectConstructors.isEmpty()) {
            throw new IllegalArgumentException(String.format("Class %s has no constructor that is annotated with @Inject.", type.getName()));
        }
        if (injectConstructors.size() > 1) {
            throw new IllegalArgumentException(String.format("Class %s has multiple constructors that are annotated with @Inject.", type.getName()));
        }
        return injectConstructors.get(0);
    }

    private static boolean isPublicOrPackageScoped(Class<?> type, Constructor<?> constructor) {
        if (isPackagePrivate(type.getModifiers())) {
            return !Modifier.isPrivate(constructor.getModifiers()) && !Modifier.isProtected(constructor.getModifiers());
        } else {
            return Modifier.isPublic(constructor.getModifiers());
        }
    }

    private static boolean isPackagePrivate(int modifiers) {
        return !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers);
    }

    private <T> void validateType(Class<T> type) {
        if (type.isInterface() || type.isAnnotation() || type.isEnum()) {
            throw new IllegalArgumentException(String.format("Type %s is not a class.", type.getName()));
        }
        if (type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
            throw new IllegalArgumentException(String.format("Class %s is a non-static inner class.", type.getName()));
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException(String.format("Class %s is an abstract class.", type.getName()));
        }
    }
}
