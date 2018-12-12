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

import org.gradle.api.internal.instantiation.ConstructorSelector;
import org.gradle.api.internal.instantiation.SelectedConstructor;
import org.gradle.api.reflect.ObjectInstantiationException;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;

/**
 * An {@link Instantiator} that applies dependency injection, delegating to a {@link ConstructorSelector} to decide which constructor to use to create instances.
 */
public class DependencyInjectingInstantiator implements Instantiator {
    private final ServiceRegistry services;
    private final ConstructorSelector constructorSelector;

    public DependencyInjectingInstantiator(ConstructorSelector constructorSelector, ServiceRegistry services) {
        this.services = services;
        this.constructorSelector = constructorSelector;
    }

    public <T> T newInstance(Class<? extends T> type, Object... parameters) {
        try {
            SelectedConstructor constructor = findConstructor(type, parameters);
            Object[] resolvedParameters = convertParameters(type, constructor, parameters);
            Object instance;
            try {
                instance = constructor.getConstructor().newInstance(resolvedParameters);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (instance instanceof WithServiceRegistry) {
                ((WithServiceRegistry) instance).setServices(services);
            }
            return type.cast(instance);
        } catch (Throwable t) {
            throw new ObjectInstantiationException(type, t);
        }
    }

    private SelectedConstructor findConstructor(Class<?> type, Object[] parameters) throws Throwable {
        SelectedConstructor constructor = constructorSelector.forParams(type, parameters);
        if (constructor.getFailure() != null) {
            throw constructor.getFailure();
        }
        return constructor;
    }

    private <T> Object[] convertParameters(Class<T> type, SelectedConstructor constructor, Object[] parameters) {
        Class<?>[] parameterTypes = constructor.getConstructor().getParameterTypes();
        if (parameterTypes.length < parameters.length) {
            throw new IllegalArgumentException(String.format("Too many parameters provided for constructor for class %s. Expected %s, received %s.", type.getName(), parameterTypes.length, parameters.length));
        }
        if (parameterTypes.length == parameters.length) {
            // No services to be mixed in
            return verifyParameters(type, constructor, parameters);
        } else {
            return addServicesToParameters(type, constructor, parameters);
        }
    }

    private <T> Object[] verifyParameters(Class<T> type, SelectedConstructor constructor, Object[] parameters) {
        Class<?>[] parameterTypes = constructor.getConstructor().getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> targetType = parameterTypes[i];
            if (targetType.isPrimitive()) {
                targetType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(targetType);
            }
            Object currentParameter = parameters[i];
            if (currentParameter == null && constructor.allowsNullParameters()) {
                continue;
            }
            if (!targetType.isInstance(currentParameter)) {
                StringBuilder builder = new StringBuilder(String.format("Unable to determine %s argument #%s:", type.getName(), i + 1));
                builder.append(String.format(" value %s not assignable to type %s", currentParameter, parameterTypes[i]));
                throw new IllegalArgumentException(builder.toString());
            }
        }
        return parameters;
    }

    private <T> Object[] addServicesToParameters(Class<T> type, SelectedConstructor constructor, Object[] parameters) {
        Class<?>[] parameterTypes = constructor.getConstructor().getParameterTypes();
        Type[] genericTypes = constructor.getConstructor().getGenericParameterTypes();
        Object[] resolvedParameters = new Object[parameterTypes.length];
        int pos = 0;
        for (int i = 0; i < resolvedParameters.length; i++) {
            Class<?> targetType = parameterTypes[i];
            if (targetType.isPrimitive()) {
                targetType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(targetType);
            }
            Type serviceType = genericTypes[i];
            Object currentParameter;
            if (pos < parameters.length && targetType.isInstance(parameters[pos])) {
                currentParameter = parameters[pos];
                pos++;
            } else {
                currentParameter = services.find(serviceType);
            }
            if (currentParameter != null) {
                resolvedParameters[i] = currentParameter;
            } else {
                StringBuilder builder = new StringBuilder(String.format("Unable to determine %s argument #%s:", type.getName(), i + 1));
                if (pos < parameters.length) {
                    builder.append(String.format(" value %s not assignable to type %s", parameters[pos], parameterTypes[i]));
                } else {
                    builder.append(String.format(" missing parameter value of type %s", parameterTypes[i]));
                }
                builder.append(String.format(", or no service of type %s", serviceType));
                throw new IllegalArgumentException(builder.toString());
            }
        }
        if (pos != parameters.length) {
            throw new IllegalArgumentException(String.format("Unexpected parameter provided for constructor for class %s.", type.getName()));
        }
        return resolvedParameters;
    }

    /**
     * An internal interface that can be used by code generators/proxies to indicate that
     * they require a service registry.
     */
    public interface WithServiceRegistry {
        void setServices(ServiceRegistry services);
    }
}
